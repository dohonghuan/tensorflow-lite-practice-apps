package com.example.objectdetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.util.Size
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op

class ObjectDetectionHelper(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: List<String>,
    private val objectDetectionListener: DetectorListener?) {

    private lateinit var tflite: Interpreter

    /** Abstraction object that wraps a prediction output in an easy to parse way */
    data class ObjectPrediction(val location: RectF, val label: String, val score: Float)

    private val locations = arrayOf(Array(OBJECT_COUNT) {FloatArray(4)})
    private val labelIndices = arrayOf(FloatArray(OBJECT_COUNT))
    private val scores = arrayOf(FloatArray(OBJECT_COUNT))

    private val outputBuffer = mapOf(
        0 to locations,
        1 to labelIndices,
        2 to scores,
        3 to FloatArray(1)
    )

    val predictions get() = (0 until OBJECT_COUNT).map {
        ObjectPrediction(

            // The locations are an array of [0, 1] floats for [top, left, bottom, right]
            location = locations[0][it].let {
                RectF(it[1], it[0], it[3], it[2])
            },

            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes + 1,
            // while outputClasses correspond to class index from 0 to number_of_classes
//            label = labels[1 + labelIndices[0][it].toInt()],
            label = labelPath[1 + labelIndices[0][it].toInt()],
            // Score is a single value of [0, 1]
            score = scores[0][it]
        )
    }

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        val options = Interpreter.Options()
        options.setNumThreads(5)
        options.setUseNNAPI(true)

        tflite = Interpreter(
            FileUtil.loadMappedFile(context, modelPath),
            options
        )
    }

    fun detect(image: Bitmap, imageRotation: Int) {

        val cropSize = minOf(image.width, image.height)
        val inputShape = tflite.getInputTensor(0).shape()
        val inputSize = Size(inputShape[2], inputShape[1])

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
//            .add(NormalizeOp(127.5f, 127.5f))
            .add(NormalizeOp(0f, 1f))
            .add(Rot90Op(-imageRotation / 90))
            .build()

//        val imageProcessor = ImageProcessor.Builder()
//            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
//            .add(
//                ResizeOp(
//                    inputSize.height,
//                    inputSize.width,
//                    ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)
//            )
//            .add(Rot90Op(-imageRotation / 90))
//            .add(NormalizeOp(0f, 1f))
//            .build()

//        val imageProcessor = ImageProcessor.Builder()
//            .add(Rot90Op(-imageRotation / 90))
//            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        Log.d("ObjectDetection", "Tensor he: ${tensorImage.height}")
        Log.d("ObjectDetection", "Tensor wi: ${tensorImage.width}")
//        tflite.run(tensorImage.buffer, outputBuffer)

        tflite.runForMultipleInputsOutputs(arrayOf(tensorImage.buffer), outputBuffer)

        objectDetectionListener?.onResult(
            predictions,
            tensorImage.height,
            tensorImage.width)
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResult(result: List<ObjectPrediction>?, imageHeight: Int, imageWidth: Int)
    }

    companion object {
        private const val OBJECT_COUNT = 10
    }
}