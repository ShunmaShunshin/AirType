package com.airtype.phone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.airtype.phone.theme.Theme

/**
 * 参考采样机（VAULTS）线条：同心刻度环 + 指针弧 + 十字线 + 细分刻度。
 * 作为背景装饰层，弱透明、非交互。
 */
class RadarView(context: Context) : View(context) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = Theme.ACCENT
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = Theme.ACCENT
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        color = Theme.ACCENT
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val r = minOf(w, h) / 2f * 0.92f

        ring.alpha = 88
        line.alpha = 80
        arcPaint.alpha = 175

        for (f in floatArrayOf(1f, 0.78f, 0.55f, 0.32f)) canvas.drawCircle(cx, cy, r * f, ring)

        canvas.drawLine(0f, cy, w, cy, line)
        canvas.drawLine(cx, 0f, cx, h, line)

        for (deg in 0 until 360 step 5) {
            val a = Math.toRadians(deg.toDouble())
            val len = if (deg % 30 == 0) 7f else 3f
            canvas.drawLine(
                cx + (Math.cos(a) * r).toFloat(), cy + (Math.sin(a) * r).toFloat(),
                cx + (Math.cos(a) * (r - len)).toFloat(), cy + (Math.sin(a) * (r - len)).toFloat(), line)
        }

        val ar = r * 0.92f
        canvas.drawArc(cx - ar, cy - ar, cx + ar, cy + ar, 210f, 120f, false, arcPaint)
    }
}
