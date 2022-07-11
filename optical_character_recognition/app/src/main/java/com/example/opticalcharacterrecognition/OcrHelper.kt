package com.example.opticalcharacterrecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.util.Size
import com.example.opticalcharacterrecognition.presentation.overview.OverviewFragment
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

class OcrHelper(
    private val context: Context,
    private val detectionModelPath: String,
    private val recognitionModelPath: String,
    private val ocrListener: OcrListener?
) {
    private lateinit var detectionInterpreter: Interpreter
    private lateinit var recognitionInterpreter: Interpreter
    private var rW: Float = 0.0f
    private var rH: Float = 0.0f

    data class rectDetected(val score: Float,
                            val left: Float,
                            val top: Float,
                            val right: Float,
                            val bottom: Float)

    init {
        setupModel()
    }

    private fun setupModel() {
        detectionInterpreter = getInterpreter(context, detectionModelPath, true)
        recognitionInterpreter = getInterpreter(context, recognitionModelPath, false)
    }

    private fun getInterpreter(
        context: Context,
        modelPath: String,
        isUseGpu: Boolean
    ): Interpreter {
        val options = Interpreter.Options()
        options.setNumThreads(4)
        if (isUseGpu) {
            val gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
        }
//        options.setUseNNAPI(true)

        return Interpreter(loadModelFile(context, modelPath), options)
    }

    private fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    fun process(image: Bitmap) {
        rW = image.width / 320.0f
        rH = image.height / 320.0f

        val result = detect(image, 0)
        val resultText = ArrayList<String>()
        result.forEach {
            val x = it.left.toInt()
            val y = it.top.toInt()
            val w = (abs(it.left - it.right)).toInt()
            val h = (abs(it.top - it.bottom)).toInt()
            Log.d(TAG, "x: ${x} y: ${y} ${it.right} ${it.bottom}")
            val textImage = Bitmap.createBitmap(image,
                x,
                y,
                w,
                h)
            resultText.add(recognize(textImage))
        }

        ocrListener?.onResult(result, resultText, image)
    }

    private fun detect(image: Bitmap, imageRotation: Int): ArrayList<RectF> {

        val tensorImage = bitmapToTensorImageForDetection(image,
            detectionImageWidth,
            detectionImageHeight,
            detectionImageMeans,
            detectionImageStds)

        val detectionOutputs: HashMap<Int, Any> = HashMap<Int, Any>()

        val detectionScores =
            Array(1) {
                Array(detectionOutputNumRows) {
                    Array(detectionOutputNumCols) {
                        FloatArray(1) } } }

        val detectionGeometries =
            Array(1) {
                Array(detectionOutputNumRows) {
                    Array(detectionOutputNumCols) {
                        FloatArray(5) } } }

        detectionOutputs.put(0, detectionScores)
        detectionOutputs.put(1, detectionGeometries)

        val detectionInputs = arrayOf(tensorImage.buffer.rewind())
        detectionInterpreter.runForMultipleInputsOutputs(detectionInputs, detectionOutputs)

        // [1][1][80][80]
        val transposeddetectionScores =
            Array(1) { Array(1) { Array(detectionOutputNumRows) { FloatArray(
                detectionOutputNumCols
            ) } } }

        // [1][5][80][80]
        val transposedDetectionGeometries =
            Array(1) { Array(5) { Array(detectionOutputNumRows) { FloatArray(
                detectionOutputNumCols
            ) } } }

        // transpose detection output tensors
        for (i in 0 until transposeddetectionScores[0][0].size) { //80
            for (j in 0 until transposeddetectionScores[0][0][0].size) { //80
                for (k in 0 until 1) {
                    transposeddetectionScores[0][k][i][j] = detectionScores[0][i][j][k]
                }
                for (k in 0 until 5) {
                    transposedDetectionGeometries[0][k][i][j] = detectionGeometries[0][i][j][k]
                }
            }
        }

        val detectedRotatedRects = ArrayList<rectDetected>()

        // numCols
        for (y in 0 until transposeddetectionScores[0][0].size) {
            val detectionScoreData = transposeddetectionScores[0][0][y]

            val detectionGeometryX0Data = transposedDetectionGeometries[0][0][y]
            val detectionGeometryX1Data = transposedDetectionGeometries[0][1][y]
            val detectionGeometryX2Data = transposedDetectionGeometries[0][2][y]
            val detectionGeometryX3Data = transposedDetectionGeometries[0][3][y]
            val detectionRotationAngleData = transposedDetectionGeometries[0][4][y]

            // numCols
            for (x in 0 until transposeddetectionScores[0][0][0].size) {
                if (detectionScoreData[x] < 0.5) {
                    continue
                }
                // Compute the rotated bounding boxes and confiences (heavily based on OpenCV example):
                // https://github.com/opencv/opencv/blob/master/samples/dnn/text_detection.py
                var offsetX = x * 4.0
                var offsetY = y * 4.0

                val h = detectionGeometryX0Data[x] + detectionGeometryX2Data[x]
                val w = detectionGeometryX1Data[x] + detectionGeometryX3Data[x]

                val angle = detectionRotationAngleData[x]
                val cos = Math.cos(angle.toDouble())
                val sin = Math.sin(angle.toDouble())

                val endX = offsetX + cos * detectionGeometryX1Data[x] + sin * detectionGeometryX2Data[x]
                val endY = offsetY - sin * detectionGeometryX1Data[x] + cos * detectionGeometryX2Data[x]
                val startX = endX - w
                val startY = endY - h

                if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
                    continue
                }

                detectedRotatedRects.add(rectDetected(detectionScoreData[x],
                startX.toFloat() * rW,
                startY.toFloat() * rH,
                endX.toFloat() * rW,
                endY.toFloat() * rH)
                )
            }
        }

        detectedRotatedRects.sortWith(compareByDescending<rectDetected> {it.score})

        var isDeleted = IntArray(detectedRotatedRects.size) { _ -> 0}
        for (i in 0 until detectedRotatedRects.size) {
            if (isDeleted[i] == 1) {
                continue
            }
            for (j in i + 1 until detectedRotatedRects.size) {
                if ((abs(detectedRotatedRects[i].left - detectedRotatedRects[j].left) <= duplicateThreshold)
                    && (abs(detectedRotatedRects[i].top - detectedRotatedRects[j].top) <= duplicateThreshold)
                    && (abs(detectedRotatedRects[i].right - detectedRotatedRects[j].right) <= duplicateThreshold)
                    && (abs(detectedRotatedRects[i].bottom - detectedRotatedRects[j].bottom) <= duplicateThreshold)) {
                    isDeleted[j] = 1
                }
            }
        }

        val result = ArrayList<RectF>()
        for (i in 0 until detectedRotatedRects.size) {
            if (isDeleted[i] == 0) {
                result.add(RectF(detectedRotatedRects[i].left,
                    detectedRotatedRects[i].top,
                    detectedRotatedRects[i].right,
                    detectedRotatedRects[i].bottom))
            }
        }
        return result
    }

    private fun recognize(image: Bitmap): String {

        val tensorImage = bitmapToTensorImageForRecognition(image,
            recognitionImageWidth,
            recognitionImageHeight,
            recognitionImageMean,
            recognitionImageStd)

        val recognitionResult = ByteBuffer.allocateDirect(recognitionModelOutputSize * 8)
        recognitionResult.order(ByteOrder.nativeOrder())
        recognitionResult.rewind()

        recognitionInterpreter.run(tensorImage.buffer, recognitionResult)

        var recognizedText = ""
        for (k in 0 until recognitionModelOutputSize) {
            var alphabetIndex = recognitionResult.getInt(k * 8)
            if (alphabetIndex in alphabets.indices)
                recognizedText += alphabets[alphabetIndex]
        }
        return recognizedText
    }

    private fun bitmapToTensorImageForDetection(
        bitmapIn: Bitmap,
        width: Int,
        height: Int,
        means: FloatArray,
        stds: FloatArray): TensorImage {

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(height, width, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(means, stds))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)

        tensorImage.load(bitmapIn)
        tensorImage = imageProcessor.process(tensorImage)

        return tensorImage
    }

    private fun bitmapToTensorImageForRecognition(
        bitmapIn: Bitmap,
        width: Int,
        height: Int,
        mean: Float,
        std: Float
    ): TensorImage {

        val imageProcessor =
            ImageProcessor.Builder()
                .add(ResizeOp(height, width, ResizeOp.ResizeMethod.BILINEAR))
                .add(TransformToGrayscaleOp())
                .add(NormalizeOp(mean, std))
                .build()
        var tensorImage = TensorImage(DataType.FLOAT32)

        tensorImage.load(bitmapIn)
        tensorImage = imageProcessor.process(tensorImage)

        return tensorImage
    }

    interface OcrListener {
        fun onError(error: String)
//        fun onResult(result: HashMap<Int, Any>)
        fun onResult(detectedRects: ArrayList<RectF>, detectedText: ArrayList<String>, originBitmap: Bitmap)
    }

    companion object {
        private const val TAG = "OcrApp"

        private const val duplicateThreshold = 200.0f

        private const val detectionImageHeight = 320
        private const val detectionImageWidth = 320
        private const val detectionOutputNumRows = 80
        private const val detectionOutputNumCols = 80
        private val detectionImageMeans =
            floatArrayOf(103.94.toFloat(), 116.78.toFloat(), 123.68.toFloat())
        private val detectionImageStds = floatArrayOf(1.toFloat(), 1.toFloat(), 1.toFloat())

        private const val alphabets = "0123456789abcdefghijklmnopqrstuvwxyz"
        private const val recognitionImageHeight = 31
        private const val recognitionImageWidth = 200
        private const val recognitionImageMean = 0.toFloat()
        private const val recognitionImageStd = 255.toFloat()
        private const val recognitionModelOutputSize = 48
    }
}