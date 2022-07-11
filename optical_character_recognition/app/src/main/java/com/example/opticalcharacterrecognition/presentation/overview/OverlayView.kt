package com.example.opticalcharacterrecognition.presentation.overview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.example.opticalcharacterrecognition.R
import org.checkerframework.common.subtyping.qual.Bottom

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var boxPaint = Paint()

    private var top: Float = 0.0f
    private var left: Float = 0.0f
    private var right: Float = 0.0f
    private var bottom: Float = 0.0f

    init {
        initPaints()
    }

    private fun initPaints() {
        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    fun clear() {
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        val drawableRect = RectF(left, top, right, bottom)
        canvas.drawRect(drawableRect, boxPaint)

    }

    fun setOnResult(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom

        Log.d("OcrApp", "Scale: ${width} - ${height}")
    }
}