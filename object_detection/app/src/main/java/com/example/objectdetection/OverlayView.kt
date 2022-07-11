package com.example.objectdetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.*
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var result: List<ObjectDetectionHelper.ObjectPrediction> =
        LinkedList<ObjectDetectionHelper.ObjectPrediction>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var scaleFactor: Float = 1f

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        for (objectDetected in result) {

            if (objectDetected.score < 0.5f) {
                continue
            }
            val boundingBox = objectDetected.location

            Log.d("ObjectDetection",
                "Input: ${boundingBox.left} - ${boundingBox.top} ; ${boundingBox.right} - ${boundingBox.bottom}")

            Log.d("ObjectDetection","Output simple: ${boundingBox.left * (width * 1.0f)} - ${boundingBox.top * (height * 1.0f)} ; " +
                    "${boundingBox.right * (width * 1.0f)} - ${boundingBox.bottom * (height * 1.0f)}")

            val test = mapOutputCoordinates(
                RectF(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom))

//            val top = boundingBox.top * scaleFactor
//            val bottom = boundingBox.bottom * scaleFactor
//            val left = boundingBox.left * scaleFactor
//            val right = boundingBox.right * scaleFactor

            val top = test.top
            val bottom = test.bottom
            val left = test.left
            val right = test.right

            Log.d("ObjectDetection", "Output calc: ${left} - ${top} ; ${right} - ${bottom}")

            // Draw bounding box around detected objects
            val drawableRect = RectF(left, top, right, bottom)

//            val drawableRect = RectF(boundingBox.left * width,
//                boundingBox.top * height,
//                boundingBox.right * width,
//                boundingBox.bottom * height)

            canvas.drawRect(drawableRect, boxPaint)

            // Create text to display alongside detected objects
            val drawableText = objectDetected.label

            // Draw rect behind display text
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + Companion.BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + Companion.BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )

            // Draw text for detected object
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }
    }

    private fun mapOutputCoordinates(location: RectF): RectF {

        // Step 1: map location to the preview coordinates
        val previewLocation = RectF(
            location.left * width,
            location.top * height,
            location.right * width,
            location.bottom * height
        )

        // Step 2: compensate for camera sensor orientation and mirroring
        val correctedLocation = previewLocation

        // Step 3: compensate for 1:1 to 4:3 aspect ratio conversion + small margin
        val margin = 0.0f
        val requestedRatio = 4f / 3f
        val midX = (correctedLocation.left + correctedLocation.right) / 2f
        val midY = (correctedLocation.top + correctedLocation.bottom) / 2f
        Log.d("ObjectDetection", "midX ${midX} midY ${midY} ${width} ${height}")
        return if (width < height) {
//            RectF(
//                midX - (1f + margin) * requestedRatio * correctedLocation.width() / 2f,
//                midY - (1f - margin) * correctedLocation.height() / 2f,
//                midX + (1f + margin) * requestedRatio * correctedLocation.width() / 2f,
//                midY + (1f - margin) * correctedLocation.height() / 2f
//            )

            RectF(
                midX - (1f + margin) * requestedRatio * correctedLocation.left / 2f,
                correctedLocation.top,
                midX + (1f + margin) * requestedRatio * correctedLocation.right/ 2f,
                correctedLocation.bottom
            )
        } else {
            RectF(
                midX - (1f - margin) * correctedLocation.width() / 2f,
                midY - (1f + margin) * requestedRatio * correctedLocation.height() / 2f,
                midX + (1f - margin) * correctedLocation.width() / 2f,
                midY + (1f + margin) * requestedRatio * correctedLocation.height() / 2f
            )
        }
    }

    fun setResult(
        detectionResult: List<ObjectDetectionHelper.ObjectPrediction>,
        imageHeight: Int,
        imageWidth: Int
    ) {
//        Log.d("ObjectDetection", "setResult")

        result = detectionResult

        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

        Log.d("ObjectDetection", "scale: ${scaleFactor} with ${width} and ${height}")
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}