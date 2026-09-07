package com.airtype.phone.ui.dsl

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.airtype.phone.theme.Theme
import kotlin.reflect.KProperty

// ============================================================================
// 响应式数据（对应 kuikly 的 observable）：变量 set 触发订阅者，驱动 UI 局部刷新。
// ============================================================================

class State<T>(initial: T) {
    var value: T = initial
        set(v) {
            if (field != v) {
                field = v
                _listeners.toList().forEach { it() }
            }
        }

    private val _listeners = mutableListOf<() -> Unit>()
    fun subscribe(fn: () -> Unit): () -> Unit { _listeners.add(fn); return { _listeners.remove(fn) } }
}

fun <T> observable(initial: T) = State(initial)
operator fun <T> State<T>.getValue(thisRef: Any?, prop: KProperty<*>): T = value
operator fun <T> State<T>.setValue(thisRef: Any?, prop: KProperty<*>, v: T) { value = v }

// ============================================================================
// DSL 引擎：声明式构建 Android 原生 View。
// component：当前组件；contentArea：当前组件子视图的挂载容器（叶节点为 null）。
// 遵循 kuikly-ui-framework skill 的 attr / event / 布局 / 组件概念。
// ============================================================================

class Dsl(val ctx: Context) {
    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    fun run(root: ViewGroup, block: Scope.() -> Unit): Scope {
        val scope = Scope(this, root, root)
        scope.block()
        return scope
    }
}

class Scope(val dsl: Dsl, val component: View, val contentArea: ViewGroup?) {
    val ctx = dsl.ctx
    internal val attr = A(dsl, component)

    // ---- 属性（作用于当前组件 component） ----
    fun bg(color: Int) = attr.bg(color)
    fun bg(d: android.graphics.drawable.Drawable) = attr.bg(d)
    fun gradient(vararg colors: Int) = attr.gradient(*colors)
    fun gradientH(vararg colors: Int) = attr.gradientH(*colors)
    fun radius(r: Float) = attr.radius(r)
    fun stroke(color: Int, width: Int = 1, r: Float = 0f) = attr.stroke(color, width, r)
    fun elevation(e: Float) = attr.elevation(e)
    fun alpha(a: Float) = attr.alpha(a)
    fun visible(v: Boolean) = attr.visible(v)
    fun size(w: Int, h: Int) = attr.size(w, h)
    fun width(w: Int) = attr.width(w)
    fun height(h: Int) = attr.height(h)
    fun wrap() = attr.wrap()
    fun match() = attr.match()
    fun weight(f: Float) = attr.weight(f)
    fun pad(l: Int, t: Int, r: Int, b: Int) = attr.pad(l, t, r, b)
    fun pad(all: Int) = attr.pad(all)
    fun padH(h: Int) = attr.padH(h)
    fun padV(v: Int) = attr.padV(v)
    fun margin(l: Int, t: Int, r: Int, b: Int) = attr.margin(l, t, r, b)
    fun margin(all: Int) = attr.margin(all)
    fun marginTop(t: Int) = attr.marginTop(t)
    fun marginBottom(b: Int) = attr.marginBottom(b)
    fun marginLeft(l: Int) = attr.marginLeft(l)
    fun marginRight(r: Int) = attr.marginRight(r)
    fun textColor(c: Int) = attr.textColor(c)
    fun textSize(sp: Float) = attr.textSize(sp)
    fun bold() = attr.bold()
    fun medium() = attr.medium()
    fun textGravity(gr: Int) = attr.textGravity(gr)
    fun centerText() = attr.centerText()
    fun centerH() = attr.centerH()
    fun center() = attr.center()
    fun maxLines(n: Int) = attr.maxLines(n)
    fun ellipsizeEnd() = attr.ellipsizeEnd()
    fun singleLine() = attr.singleLine()
    fun letterSpacing(f: Float) = attr.letterSpacing(f)
    fun hint(s: String) = attr.hint(s)
    fun inputType(t: Int) = attr.inputType(t)
    fun minLines(n: Int) = attr.minLines(n)
    fun maxLength(n: Int) = attr.maxLength(n)
    fun singleLineInput() = attr.singleLineInput()
    fun gravityFlagTop() = attr.gravityFlagTop()
    fun click(fn: () -> Unit) = attr.click(fn)
    fun disabled() = attr.disabled()
    fun displayType() = attr.displayType()
    fun monoFont() = attr.monoFont()
    fun monoBoldFont() = attr.monoBoldFont()
    fun bodyFont() = attr.bodyFont()

    // ---- 子组件构建器（添加到 contentArea） ----
    fun Column(content: Scope.() -> Unit = {}): LinearLayout {
        val v = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val c = Scope(dsl, v, v)
        c.content()
        addTo(v, c.attr)
        return v
    }

    fun Row(content: Scope.() -> Unit = {}): LinearLayout {
        val v = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val c = Scope(dsl, v, v)
        c.content()
        addTo(v, c.attr)
        return v
    }

    /** 品牌卡片：surface 背景 + 圆角 + 阴影。 */
    fun Card(content: Scope.() -> Unit = {}): LinearLayout {
        val v = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val c = Scope(dsl, v, v)
        with(c) {
            bg(Theme.outline(Theme.SURFACE, Theme.EDGE, 1f, 2f))
            pad(16)
            elevation(0f)
            content()
        }
        addTo(v, c.attr)
        return v
    }

    fun Scroll(content: Scope.() -> Unit = {}): ScrollView {
        val sv = ScrollView(ctx).apply { isFillViewport = true; setBackgroundColor(Theme.CANVAS) }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dsl.dp(20), dsl.dp(24), dsl.dp(20), dsl.dp(24))
        }
        sv.addView(inner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val c = Scope(dsl, sv, inner)
        c.content()
        addTo(sv, c.attr)
        return sv
    }

    fun Spacer(weight: Float) {
        val v = View(ctx)
        val c = Scope(dsl, v, null)
        c.weight(weight)
        addTo(v, c.attr)
    }

    fun Text(text: String, content: Scope.() -> Unit = {}): TextView {
        val v = TextView(ctx).apply {
            this.text = text
            setTextColor(Theme.TEXT)
            textSize = 15f
        }
        val c = Scope(dsl, v, null)
        c.content()
        addTo(v, c.attr)
        return v
    }

    fun Button(text: String, content: Scope.() -> Unit = {}): TextView {
        val v = TextView(ctx).apply {
            this.text = text
            setTextColor(Theme.CTA_TEXT)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dsl.dp(16), dsl.dp(13), dsl.dp(16), dsl.dp(13))
            background = Theme.solid(Theme.CTA, 2f)
            isClickable = true
        }
        val c = Scope(dsl, v, null)
        c.content()
        addTo(v, c.attr)
        return v
    }

    fun IconButton(text: String, content: Scope.() -> Unit = {}): TextView {
        val v = TextView(ctx).apply {
            this.text = text
            setTextColor(Theme.TEXT)
            textSize = 22f
            gravity = Gravity.CENTER
            background = Theme.solid(Theme.SURFACE2, 12f)
            isClickable = true
        }
        val c = Scope(dsl, v, null)
        c.content()
        addTo(v, c.attr)
        return v
    }

    fun Input(hint: String, content: Scope.() -> Unit = {}): EditText {
        val v = EditText(ctx).apply {
            this.hint = hint
            setHintTextColor(Theme.TEXT_FAINT)
            setTextColor(Theme.TEXT)
            textSize = 16f
            background = Theme.outline(Theme.INSET, Theme.EDGE, 1f, 2f)
            setPadding(dsl.dp(14), dsl.dp(12), dsl.dp(14), dsl.dp(12))
            gravity = Gravity.TOP or Gravity.START
        }
        val c = Scope(dsl, v, null)
        c.content()
        addTo(v, c.attr)
        return v
    }

    fun Dot(color: Int, size: Int = 9) {
        val v = View(ctx).apply { background = Theme.solid(color, (dsl.dp(size)) / 2f) }
        val c = Scope(dsl, v, null)
        with(c) { width(size); height(size) }
        addTo(v, c.attr)
    }

    fun Divider(marginTop: Int = 10, marginBottom: Int = 10) {
        val v = View(ctx).apply { background = Theme.solid(Theme.STROKE, 1f) }
        val c = Scope(dsl, v, null)
        with(c) { height(1); margin(0, marginTop, 0, marginBottom) }
        addTo(v, c.attr)
    }

    /** 定宽高色块（强调条）。 */
    fun Bar(width: Int, height: Int, color: Int, a: Scope.() -> Unit = {}) {
        val v = View(ctx).apply { background = Theme.solid(color, 0f) }
        val c = Scope(dsl, v, null)
        with(c) { this.width(width); this.height(height); a() }
        addTo(v, c.attr)
    }

    /** 整行发丝线（纵向容器里自动撑满宽度）。 */
    fun Rule(color: Int = Theme.STROKE_SOFT) {
        val v = View(ctx).apply { background = Theme.solid(color, 0f) }
        val c = Scope(dsl, v, null)
        with(c) { height(1) }
        addTo(v, c.attr)
    }

    private fun addTo(v: View, a: A) {
        val area = contentArea ?: return
        val w = if (a.widthPx != null) a.widthPx!! else if (area is LinearLayout && area.orientation == LinearLayout.VERTICAL)
            ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        val h = a.heightPx ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val lp: ViewGroup.LayoutParams = when (area) {
            is LinearLayout -> LinearLayout.LayoutParams(w, h).apply {
                if (a.weight > 0f) weight = a.weight
                setMargins(dsl.dp(a.marginL), dsl.dp(a.marginT), dsl.dp(a.marginR), dsl.dp(a.marginB))
                a.gravity?.let { gravity = it }
            }
            is FrameLayout -> FrameLayout.LayoutParams(w, h).apply {
                a.gravity?.let { gravity = it }
            }
            else -> ViewGroup.LayoutParams(w, h)
        }
        area.addView(v, lp)
        a.applyClick()
    }
}

/** 单个组件的属性 / 事件设置作用域。 */
class A(val dsl: Dsl, val view: View) {
    var widthPx: Int? = null
    var heightPx: Int? = null
    var weight = 0f
    var marginL = 0; var marginT = 0; var marginR = 0; var marginB = 0
    var gravity: Int? = null
    private var _radius = 0f
    private var _click: (() -> Unit)? = null

    fun size(w: Int, h: Int) { widthPx = dsl.dp(w); heightPx = dsl.dp(h) }
    fun width(w: Int) { widthPx = dsl.dp(w) }
    fun height(h: Int) { heightPx = dsl.dp(h) }
    fun wrap() { widthPx = ViewGroup.LayoutParams.WRAP_CONTENT; heightPx = ViewGroup.LayoutParams.WRAP_CONTENT }
    fun match() { widthPx = ViewGroup.LayoutParams.MATCH_PARENT; heightPx = ViewGroup.LayoutParams.MATCH_PARENT }
    fun weight(f: Float) { weight = f }

    fun pad(l: Int, t: Int, r: Int, b: Int) { view.setPadding(dsl.dp(l), dsl.dp(t), dsl.dp(r), dsl.dp(b)) }
    fun pad(all: Int) { pad(all, all, all, all) }
    fun padH(h: Int) { view.setPadding(dsl.dp(h), view.paddingTop, dsl.dp(h), view.paddingBottom) }
    fun padV(v: Int) { view.setPadding(view.paddingLeft, dsl.dp(v), view.paddingRight, dsl.dp(v)) }
    fun margin(l: Int, t: Int, r: Int, b: Int) { marginL = l; marginT = t; marginR = r; marginB = b }
    fun margin(all: Int) { margin(all, all, all, all) }
    fun marginTop(t: Int) { marginT = t }
    fun marginBottom(b: Int) { marginB = b }
    fun marginLeft(l: Int) { marginL = l }
    fun marginRight(r: Int) { marginR = r }

    fun bg(color: Int) { view.background = Theme.solid(color, _radius) }
    fun bg(d: android.graphics.drawable.Drawable) { view.background = d }
    fun gradient(vararg colors: Int) { view.background = Theme.vGradient(*colors); round() }
    fun gradientH(vararg colors: Int) { view.background = Theme.hGradient(*colors); round() }
    fun radius(r: Float) { _radius = r; round() }
    fun stroke(color: Int, width: Int = 1, r: Float = _radius) { _radius = r; view.background = Theme.outline(Color.TRANSPARENT, color, width.toFloat(), r) }
    fun elevation(e: Float) { view.elevation = e }
    fun alpha(a: Float) { view.alpha = a }
    fun visible(v: Boolean) { view.visibility = if (v) View.VISIBLE else View.GONE }

    fun textColor(c: Int) { (view as? TextView)?.setTextColor(c) }
    fun textSize(sp: Float) { (view as? TextView)?.textSize = sp }
    fun bold() { (view as? TextView)?.typeface = Typeface.DEFAULT_BOLD }
    fun medium() { (view as? TextView)?.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    fun displayType() { (view as? TextView)?.typeface = Typeface.create(Theme.FONT_DISPLAY, Typeface.BOLD) }
    fun bodyFont() { (view as? TextView)?.typeface = Typeface.create(Theme.FONT_BODY, Typeface.NORMAL) }
    fun monoFont() { (view as? TextView)?.typeface = Typeface.create(Theme.FONT_MONO, Typeface.NORMAL) }
    fun monoBoldFont() { (view as? TextView)?.typeface = Typeface.create(Theme.FONT_MONO, Typeface.BOLD) }
    fun textGravity(gr: Int) { (view as? TextView)?.gravity = gr }
    fun centerText() { (view as? TextView)?.gravity = Gravity.CENTER }
    fun centerH() { gravity = Gravity.CENTER_HORIZONTAL }
    fun center() { gravity = Gravity.CENTER }
    fun maxLines(n: Int) { (view as? TextView)?.maxLines = n }
    fun ellipsizeEnd() { (view as? TextView)?.ellipsize = TextUtils.TruncateAt.END }
    fun singleLine() { (view as? TextView)?.setSingleLine(true) }
    fun letterSpacing(f: Float) { (view as? TextView)?.letterSpacing = f }

    fun hint(s: String) { (view as? EditText)?.hint = s }
    fun inputType(t: Int) { (view as? EditText)?.inputType = t }
    fun minLines(n: Int) { (view as? EditText)?.minLines = n }
    fun maxLength(n: Int) { (view as? EditText)?.filters = arrayOf(InputFilter.LengthFilter(n)) }
    fun singleLineInput() { (view as? EditText)?.setSingleLine(true) }
    fun gravityFlagTop() { (view as? EditText)?.gravity = Gravity.TOP or Gravity.START }

    fun click(fn: () -> Unit) { _click = fn }
    fun disabled() { view.alpha = 0.4f; view.isEnabled = false }

    private fun round() {
        if (_radius > 0f) {
            val b = view.background
            if (b is android.graphics.drawable.GradientDrawable) {
                b.cornerRadius = _radius * dsl.ctx.resources.displayMetrics.density
            }
        }
    }

    internal fun applyClick() {
        _click?.let { fn ->
            view.setOnClickListener { fn() }
            view.isClickable = true
            view.isFocusable = true
        }
    }
}
