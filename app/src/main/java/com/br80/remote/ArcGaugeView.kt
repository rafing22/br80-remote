package com.br80.remote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Gauge semicircolare in stile "cruscotto analogico" (batteria / RSSI del telecomando BR80).
 * Disegna un arco di sfondo (track) e un arco di valore (0f..1f) sovrapposto.
 */
class ArcGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var value: Float = 0f // 0f..1f

    private val strokeWidthPx = 5f * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3A3226")
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E0140F")
    }

    private val arcRect = RectF()

    fun setValue(newValue: Float) {
        value = newValue.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx
        arcRect.set(inset, inset, width - inset, height * 2f - inset)
        canvas.drawArc(arcRect, 180f, 180f, false, trackPaint)
        canvas.drawArc(arcRect, 180f, 180f * value, false, valuePaint)
    }
}
