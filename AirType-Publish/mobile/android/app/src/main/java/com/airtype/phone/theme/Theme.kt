package com.airtype.phone.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * AirType 品牌主题 · 终端仪器（Telemetry Console）：近黑机体 + 信号绿 + 等宽读数 + 发丝线。
 * 与 PC 端一致：暗色分层面板、无投影、低圆角、一号信号色。
 */
object Theme {
    val CANVAS = 0xFF0B0E14.toInt()
    val SURFACE = 0xFF0E121A.toInt()
    val SURFACE2 = 0xFF121722.toInt()
    val INSET = 0xFF090C12.toInt()
    val SURFACE_GLASS = 0x990E121A.toInt()
    val SURFACE2_GLASS = 0x99121722.toInt()

    val TEXT = 0xFFD8EAE0.toInt()
    val TEXT_DIM = 0xFF86AE94.toInt()
    val TEXT_FAINT = 0xFF52705F.toInt()

    val STROKE = 0xFF1E2633.toInt()
    val STROKE_SOFT = 0xFF161D28.toInt()
    val EDGE = 0xFF2E9E5F.toInt()

    val ACCENT = 0xFF5EE07A.toInt()
    val ACCENT_STRONG = 0xFF5EE07A.toInt()
    val ACCENT2 = 0xFF2FA85C.toInt()
    val ACCENT_SOFT = 0xFF121722.toInt()

    val SUCCESS = 0xFF5EE07A.toInt()
    val DANGER = 0xFFFF6B6B.toInt()
    val WARN = 0xFFFFB443.toInt()

    val CTA = 0xFF5EE07A.toInt()
    val CTA_TEXT = 0xFF0B0E14.toInt()

    val FONT_DISPLAY = "monospace"
    val FONT_MONO = "monospace"
    val FONT_BODY = "sans-serif"

    fun displayTypeface(): Typeface = Typeface.create(FONT_DISPLAY, Typeface.BOLD)
    fun bodyTypeface(): Typeface = Typeface.create(FONT_BODY, Typeface.NORMAL)

    fun bgGradient(): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACCENT, ACCENT2))

    fun vGradient(vararg colors: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)

    fun hGradient(vararg colors: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)

    fun solid(color: Int, radius: Float = 0f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }

    fun outline(color: Int, stroke: Int, strokeWidth: Float = 1f, radius: Float = 2f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            setStroke(strokeWidth.toInt().coerceAtLeast(1), stroke)
        }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun View.applyElevation(elev: Float): View {
    elevation = elev
    return this
}
