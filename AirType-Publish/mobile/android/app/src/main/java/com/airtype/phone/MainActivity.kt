package com.airtype.phone

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.airtype.phone.net.Discovery
import com.airtype.phone.net.SubnetScanner
import com.airtype.phone.net.TcpLinker
import com.airtype.phone.theme.Theme
import com.airtype.phone.theme.dp
import com.airtype.phone.ui.RadarView
import org.json.JSONArray

/**
 * AirType 手机端主界面（纯 View 直接构建，保证可见性）。
 * 终端仪器世界；双态：发现页 <-> 已连接工作页。
 */
class MainActivity : Activity(), TcpLinker.Callbacks {

    companion object {
        private const val DISCOVERY_PORT = 47556
        private const val DEFAULT_TCP_PORT = 47555
        private const val PREFS = "airtype"
        private const val MAX_HISTORY = 5
        private const val SNACK_TAG = "at_snack"
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout

    private lateinit var discoverScreen: ScrollView
    private lateinit var workScreen: ScrollView
    private lateinit var discoverStatusRe: LinearLayout
    private lateinit var discoverListRe: LinearLayout
    private lateinit var ipEt: EditText
    private lateinit var codeEt: EditText
    private lateinit var workStatusRe: LinearLayout
    private lateinit var historyRe: LinearLayout
    private lateinit var historySection: TextView
    private lateinit var inputEt: EditText
    private lateinit var sendBtn: TextView
    private lateinit var onboardHint: TextView
    private lateinit var logReDiscover: LinearLayout
    private lateinit var logReWork: LinearLayout
    private val logLines = java.util.ArrayDeque<String>()

    private val linker = TcpLinker(this)
    private val discovered = LinkedHashMap<String, Discovery.BeaconInfo>()
    private val history = ArrayList<String>()
    private lateinit var scanner: SubnetScanner
    private val discovery = Discovery { info -> main.post { onDiscovered(info) } }

    @Volatile private var workVisible = false
    @Volatile private var connected = false
    private var pendingIp: String? = null
    private var pendingPort = DEFAULT_TCP_PORT
    private var pendingCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        scanner = SubnetScanner(cm, DEFAULT_TCP_PORT)
        linker.deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        root = FrameLayout(this).apply { setBackgroundColor(Theme.CANVAS) }
        setContentView(root)
        root.addView(RadarView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        try {
            buildScreens()
            showScreen(false)
            onboardHint.visibility = if (prefs.getBoolean("onboarded", false)) View.GONE else View.VISIBLE
            discovery.start(DISCOVERY_PORT)
            loadHistory()
            refreshHistory()
            autoConnectLast()
        } catch (t: Throwable) {
            try { java.io.File(filesDir, "at-crash.txt").writeText(t.stackTraceToString()) } catch (_: Exception) { }
            root.removeAllViews()
            root.addView(t("初始化失败\n${t.message ?: t.javaClass.simpleName}", Theme.TEXT, 14f, mono = true), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        }
    }

    override fun onDestroy() {
        discovery.stop(); scanner.stop(); linker.stop(); super.onDestroy()
    }

    // ============================================================================
    // 纯 View 构建
    // ============================================================================

    private fun buildScreens() {
        discoverScreen = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.argb(0xCC, 0x0B, 0x0E, 0x14)) }
        val di = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        discoverScreen.addView(di, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        buildDiscover(di)
        root.addView(discoverScreen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        workScreen = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.argb(0xCC, 0x0B, 0x0E, 0x14)) }
        val wi = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        workScreen.addView(wi, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        buildWork(wi)
        root.addView(workScreen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun buildDiscover(container: LinearLayout) {
        container.addView(topBar(), lpV(0, 0, 0, dp(8)))

        val hero = panel()
        hero.addView(rowOf(
            t("选择电脑", Theme.TEXT, 22f, mono = true, bold = true, weight = 1f),
            tag("LINK")
        ))
        discoverStatusRe = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, 0) }
        hero.addView(discoverStatusRe)
        hero.addView(bar(42, 3, Theme.ACCENT))
        container.addView(hero, lpV(0, 0, 0, dp(4)))

        onboardHint = t("首次？同一 WiFi → 点发现的电脑 → 输入电脑上的 4 位码", Theme.ACCENT_STRONG, 11f, mono = true)
        container.addView(onboardHint, lpV(0, 0, 0, dp(2)))

        container.addView(t("发现的电脑", Theme.TEXT_DIM, 11f, mono = true, bold = true), lpV(0, dp(4), 0, 0))
        discoverListRe = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(discoverListRe, lpV(0, 0, 0, 0))

        container.addView(btn("扫描局域网 / 手机热点下的电脑", primary = false) { onScanClick() }, lpV(0, dp(4), 0, 0))

        val mc = panel()
        mc.setPadding(dp(12), dp(12), dp(12), dp(12))
        mc.addView(t("手动连接", Theme.TEXT, 13f, mono = true, bold = true))
        ipEt = inp("电脑 IP : 端口", numeric = false, maxLen = 0)
        codeEt = inp("4 位码", numeric = true, maxLen = 4)
        val ipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ipRow.addView(ipEt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f).apply { marginEnd = dp(8) })
        ipRow.addView(codeEt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mc.addView(ipRow, lpV(0, dp(6), 0, 0))
        codeEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                codeEt.setBackground(Theme.outline(Theme.INSET, Theme.EDGE, 1f, 2f))
            }
        })
        mc.addView(btn("连接", primary = true) { onManualConnect() }, lpV(0, dp(6), 0, 0))
        container.addView(mc, lpV(0, dp(8), 0, 0))

        val (logDiscover, logInnerDiscover) = makeLog()
        logReDiscover = logInnerDiscover
        container.addView(logDiscover, lpV(0, dp(8), 0, 0))
        container.addView(footer(), lpV(0, dp(8), 0, 0))
    }

    private fun buildWork(container: LinearLayout) {
        val status = panel()
        workStatusRe = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status.addView(workStatusRe)
        container.addView(status)

        container.addView(t("断开配对 ›", Theme.ACCENT_STRONG, 12f, mono = true) { onCancelPair() }, lpV(0, dp(4), 0, 0))

        val input = panel()
        input.addView(t("输入内容", Theme.TEXT, 13f, mono = true, bold = true))
        inputEt = inp("点输入框，用语音输入法说话或打字", numeric = false, maxLen = 2000, multiline = true)
        inputEt.minLines = 3
        inputEt.gravity = Gravity.TOP or Gravity.START
        inputEt.imeOptions = EditorInfo.IME_ACTION_SEND
        inputEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { onSendClick(); true } else false
        }
        input.addView(inputEt, lpV(0, dp(6), 0, 0))
        sendBtn = btn("发送到电脑", primary = true) { onSendClick() }
        input.addView(sendBtn, lpV(0, dp(6), 0, 0))
        container.addView(input, lpV(0, dp(8), 0, 0))

        historySection = t("最近发送（点击可重发）", Theme.TEXT_FAINT, 11f, mono = true, bold = true)
        container.addView(historySection, lpV(0, dp(8), 0, 0))
        historyRe = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(historyRe, lpV(0, dp(2), 0, 0))

        val (logWork, logInnerWork) = makeLog()
        logReWork = logInnerWork
        container.addView(logWork, lpV(0, dp(8), 0, 0))
        container.addView(footer(), lpV(0, dp(8), 0, 0))
    }

    // ---- helpers ----
    private fun lpV(t: Int = 0, top: Int, b: Int = 0, bottom: Int): LinearLayout.LayoutParams {
        // helper: build margins (l,t,r,b). Params: top, bottom
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(t), dp(top), dp(b), dp(bottom))
        return lp
    }

    private fun spacer(weight: Float): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, weight)
    }

    private fun rowOf(vararg views: View): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for (v in views) {
            val existing = v.layoutParams
            if (existing is LinearLayout.LayoutParams && existing.weight > 0f) row.addView(v, existing)
            else row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return row
    }

    /** 顶部品牌栏：AT 框 + AIRTYPE + ● + TERMINAL（与 PC 标题栏一致）。 */
    private fun topBar(): LinearLayout {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val atBox = TextView(this).apply {
            text = "AT"; textSize = 9f; typeface = Typeface.create(Theme.FONT_MONO, Typeface.BOLD)
            setTextColor(Theme.ACCENT_STRONG); gravity = Gravity.CENTER
            setBackground(Theme.outline(Color.TRANSPARENT, Theme.ACCENT_STRONG, 2f, 0f))
        }
        bar.addView(atBox, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
        bar.addView(t("AIRTYPE", Theme.TEXT, 13f, mono = true, bold = true))
        bar.addView(View(this).apply { setBackground(Theme.solid(Theme.ACCENT_STRONG, dp(3).toFloat())) },
            LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginStart = dp(10); marginEnd = dp(6) })
        bar.addView(t("TERMINAL", Theme.TEXT_FAINT, 10f, mono = true))
        return bar
    }

    /** 底部信息栏：v1.0.0 + 本机直连（与 PC 底部一致）。 */
    private fun footer(): LinearLayout {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(t("v1.0.6", Theme.TEXT_FAINT, 10f, mono = true))
        return bar
    }

    /** 日志卡：标题 + 可滚动日志区（较高）。 */
    private fun makeLog(): Pair<LinearLayout, LinearLayout> {
        val log = panel()
        log.addView(t("LOG · 日志", Theme.TEXT_FAINT, 11f, mono = true, bold = true))
        val sv = ScrollView(this)
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(inner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        log.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)).apply { topMargin = dp(6) })
        return log to inner
    }

    private fun t(text: String, color: Int, size: Float, mono: Boolean = true, bold: Boolean = false, weight: Float = 0f, onClick: (() -> Unit)? = null): TextView {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = size
            typeface = Typeface.create(if (mono) Theme.FONT_MONO else Theme.FONT_BODY, if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (weight > 0f) layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            if (onClick != null) setOnClickListener { onClick() }
        }
        return tv
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackground(Theme.outline(Color.argb(0xCC, 0x0E, 0x12, 0x1A), Theme.EDGE, 1f, 2f))
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun btn(text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val b = TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.create(Theme.FONT_MONO, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextColor(if (primary) Theme.CTA_TEXT else Theme.TEXT)
            setBackground(if (primary) Theme.solid(Theme.CTA, 2f) else Theme.outline(Theme.SURFACE2, Theme.EDGE, 1f, 2f))
            setOnClickListener { onClick() }
        }
        b.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        return b
    }

    private fun tag(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 10f
        typeface = Typeface.create(Theme.FONT_MONO, Typeface.BOLD)
        setTextColor(Theme.ACCENT_STRONG)
        setBackground(Theme.solid(Theme.ACCENT_SOFT, 2f))
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun bar(width: Int, height: Int, color: Int): View = View(this).apply {
        setBackground(Theme.solid(color, 0f))
        layoutParams = ViewGroup.LayoutParams(dp(width), dp(height))
    }

    private fun inp(hint: String, numeric: Boolean, maxLen: Int, multiline: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setHintTextColor(Theme.TEXT_FAINT)
            setTextColor(Theme.TEXT)
            textSize = 14f
            typeface = Typeface.create(Theme.FONT_MONO, Typeface.NORMAL)
            setBackground(Theme.outline(Theme.INSET, Theme.EDGE, 1f, 2f))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER
            else if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            if (maxLen > 0) filters = arrayOf(InputFilter.LengthFilter(maxLen))
            if (multiline) setSingleLine(false)
        }

    // ============================================================================
    // 状态刷新（纯 View）
    // ============================================================================

    private fun showScreen(work: Boolean) {
        workVisible = work
        discoverScreen.visibility = if (work) View.GONE else View.VISIBLE
        workScreen.visibility = if (work) View.VISIBLE else View.GONE
        if (work) main.post { inputEt.requestFocus() } else main.post { updateSendEnabled() }
    }

    private fun reflow(container: LinearLayout, fill: (LinearLayout) -> Unit) {
        container.removeAllViews()
        fill(container)
    }

    private fun fillStatus(re: LinearLayout, text: String, color: Int) {
        re.removeAllViews()
        val dot = View(this).apply { setBackground(Theme.solid(color, dp(4).toFloat())) }
        re.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8); gravity = Gravity.CENTER_VERTICAL })
        re.addView(t(text, Theme.TEXT_DIM, 11f, mono = true))
    }

    private fun refreshDiscoverStatus() {
        fillStatus(discoverStatusRe,
            if (connected) "已连接 · 可到输入框直接发送" else "正在查找同一网络下的电脑…",
            if (connected) Theme.ACCENT_STRONG else Theme.ACCENT_STRONG)
    }

    private fun refreshDiscoverList() {
        reflow(discoverListRe) { l ->
            if (discovered.isEmpty()) {
                l.addView(t("未发现，请确认电脑端 AirTypePC 已运行且在同一网络；可扫描或手动连接。", Theme.TEXT_FAINT, 11f, mono = true))
            } else {
                var idx = 0
                for (info in discovered.values) {
                    idx++
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(t("%02d".format(idx), Theme.TEXT_FAINT, 11f, mono = true))
                    row.addView(t(info.name, Theme.TEXT, 15f, mono = true, weight = 1f))
                    row.addView(t(info.ip, Theme.TEXT_FAINT, 11f, mono = true))
                    row.addView(t("连接 →", Theme.ACCENT_STRONG, 12f, mono = true))
                    row.setPadding(0, dp(12), 0, dp(12))
                    row.setOnClickListener { showConnectDialog(info.name, info.ip) { code -> doConnect(info.ip, info.port, code) } }
                    l.addView(row)
                    val div = View(this).apply { setBackground(Theme.solid(Theme.STROKE_SOFT, 0f)) }
                    l.addView(div, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
                }
            }
        }
    }

    private fun updateSendEnabled() {
        if (!::sendBtn.isInitialized) return
        sendBtn.alpha = if (connected) 1f else 0.4f
        sendBtn.isEnabled = connected
    }

    private fun refreshWorkStatus() {
        reflow(workStatusRe) { l ->
            val dot = View(this).apply { setBackground(Theme.solid(if (connected) Theme.ACCENT_STRONG else Theme.WARN, dp(4).toFloat())) }
            l.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(9) })
            l.addView(t(if (connected) "已连接 · 可直接发送" else "未连接 · 正在自动重连…", Theme.TEXT_DIM, 12f, mono = true, weight = 1f))
        }
        updateSendEnabled()
    }

    private fun refreshHistory() {
        if (::historySection.isInitialized) historySection.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        if (!::historyRe.isInitialized) return
        reflow(historyRe) { l ->
            var idx = 0
            for (text in history) {
                idx++
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
                row.addView(t("%02d  %s".format(idx, text), Theme.TEXT, 14f, mono = true))
                row.addView(t("点此重发", Theme.ACCENT_STRONG, 10f, mono = true))
                row.setOnClickListener { resend(text) }
                l.addView(row)
                val div = View(this).apply { setBackground(Theme.solid(Theme.STROKE_SOFT, 0f)) }
                l.addView(div, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            }
        }
    }

    private fun renderLogs() {
        fun fill(re: LinearLayout) {
            re.removeAllViews()
            for (l in logLines) re.addView(t(l, Theme.TEXT_FAINT, 10f, mono = true))
        }
        if (::logReDiscover.isInitialized) fill(logReDiscover)
        if (::logReWork.isInitialized) fill(logReWork)
    }

    private fun addLog(msg: String) {
        logLines.addFirst("[${now()}] $msg")
        while (logLines.size > 6) logLines.removeLast()
        renderLogs()
    }

    private fun now(): String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    /** 终端风"连接"弹窗（纯 View）。 */
    private fun showConnectDialog(name: String, ip: String, onConnect: (String) -> Unit) {
        val dialog = Dialog(this)
        val scrim = FrameLayout(this).apply { setBackgroundColor(Color.argb(0xB3, 0, 0, 0)) }
        val card = panel()
        card.addView(t("连接 · $name", Theme.TEXT, 13f, mono = true, bold = true))
        card.addView(t(ip, Theme.TEXT_DIM, 11f, mono = true), lpV(0, dp(4), 0, dp(12)))
        val edit = inp("4 位配对码", numeric = true, maxLen = 4)
        card.addView(edit)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val cancel = btn("取消", false) { dialog.dismiss() }
        val ok = btn("连接", true) {
            val c = edit.text.toString().trim()
            if (c.length != 4) { snack("配对码需为 4 位数字", error = true); edit.requestFocus() }
            else { dialog.dismiss(); onConnect(c) }
        }
        row.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
        row.addView(ok, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row, lpV(0, dp(16), 0, 0))
        scrim.addView(card, FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(scrim)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    /** 终端风自定义 Snackbar。 */
    private fun snack(text: String, error: Boolean = false) {
        main.post {
            root.findViewWithTag<View>(SNACK_TAG)?.let { root.removeView(it) }
            val tv = TextView(this).apply {
                tag = SNACK_TAG
                setText(text)
                setTextColor(if (error) Theme.DANGER else Theme.TEXT)
                textSize = 13f
                typeface = Typeface.create(Theme.FONT_MONO, Typeface.NORMAL)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackground(Theme.outline(Theme.SURFACE2, if (error) Theme.DANGER else Theme.EDGE, 1f, 2f))
            }
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(20)
            }
            root.addView(tv, lp)
            tv.alpha = 0f; tv.translationY = dp(20).toFloat()
            tv.animate().alpha(1f).translationY(0f).setDuration(180).start()
            tv.postDelayed({
                tv.animate().alpha(0f).translationY(dp(20).toFloat()).setDuration(180).withEndAction { root.removeView(tv) }.start()
            }, 2600)
        }
    }

    // ============================================================================
    // 事件
    // ============================================================================

    private fun onScanClick() {
        discovered.clear(); refreshDiscoverList(); refreshDiscoverStatus()
        scanner.start(object : SubnetScanner.Result {
            override fun onProgress(text: String) { fillStatus(discoverStatusRe, text, Theme.ACCENT_STRONG) }
            override fun onFound(ip: String) { addLog("扫描到 $ip"); onDiscovered(Discovery.BeaconInfo("(局域网扫描)", DEFAULT_TCP_PORT, ip, fromScan = true)) }
            override fun onDone(text: String) {
                val msg = if (discovered.isEmpty()) "$text · 未找到电脑；请确认电脑端已运行并放行防火墙" else "$text · 发现 ${discovered.size} 台"
                fillStatus(discoverStatusRe, msg, if (discovered.isEmpty()) Theme.WARN else Theme.ACCENT_STRONG)
            }
        })
    }

    private fun onManualConnect() {
        val raw = ipEt.text.toString().trim()
        if (raw.isEmpty()) { snack("请填写电脑 IP", error = true); return }
        val code = codeEt.text.toString().trim()
        if (code.isEmpty()) { markCodeError("配对码为电脑屏幕上的 4 位数字"); return }
        if (code.length != 4) { markCodeError("配对码需为 4 位数字"); return }
        var host = raw; var port = DEFAULT_TCP_PORT
        if (raw.contains(":")) { val idx = raw.lastIndexOf(':'); host = raw.substring(0, idx).trim(); port = raw.substring(idx + 1).trim().toIntOrNull() ?: DEFAULT_TCP_PORT }
        if (host.isEmpty()) { snack("IP 格式不正确", error = true); return }
        doConnect(host, port, code)
    }

    private fun markCodeError(msg: String) {
        codeEt.setBackground(Theme.outline(Theme.INSET, Theme.DANGER, 1f, 2f))
        codeEt.requestFocus(); snack(msg, error = true)
    }

    private fun onCancelPair() {
        linker.stop(); inputEt.setText(""); showScreen(false)
        discovery.start(DISCOVERY_PORT); refreshDiscoverStatus(); refreshDiscoverList()
    }

    private fun onSendClick() {
        val text = inputEt.text.toString()
        if (text.isBlank()) { snack("内容为空", error = true); return }
        sendAndTrack(text, fromInput = true)
    }

    private fun resend(text: String) = sendAndTrack(text, fromInput = false)

    private fun sendAndTrack(text: String, fromInput: Boolean): Boolean {
        if (!linker.connected) { snack("尚未连接到电脑（自动重连中）", error = true); return false }
        val err = linker.sendText(text)
        if (err == null) {
            if (fromInput) inputEt.setText("")
            addLog("已发送（待上屏）"); addHistory(text); return true
        }
        snack(err, error = true); return false
    }

    private fun onDiscovered(info: Discovery.BeaconInfo) {
        addLog("发现 ${info.name} ${info.ip}")
        discovered[info.ip] = info; refreshDiscoverList(); refreshDiscoverStatus()
    }

    private fun doConnect(ip: String, port: Int, code: String) {
        pendingIp = ip; pendingPort = port; pendingCode = code
        addLog("连接 $ip:$port …"); refreshDiscoverStatus(); linker.connect(ip, port, code)
    }

    // ============================================================================
    // 历史
    // ============================================================================

    private fun addHistory(text: String) {
        history.remove(text); history.add(0, text)
        while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        persistHistory(); refreshHistory()
    }

    private fun persistHistory() {
        val arr = JSONArray(); for (t in history) arr.put(t)
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        history.clear()
        try {
            val raw = prefs.getString("history", null) ?: return
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) { if (history.size >= MAX_HISTORY) break; history.add(arr.getString(i)) }
        } catch (ignored: Exception) { history.clear() }
    }

    private fun autoConnectLast() {
        val ip = prefs.getString("ip", null); val code = prefs.getString("code", null)
        if (!ip.isNullOrBlank() && !code.isNullOrBlank()) doConnect(ip, prefs.getInt("port", DEFAULT_TCP_PORT), code)
    }

    private fun saveLastSuccess(ip: String, port: Int, code: String) {
        prefs.edit().putString("ip", ip).putInt("port", port).putString("code", code).apply()
    }

    // ============================================================================
    // TcpLinker.Callbacks
    // ============================================================================

    override fun onStatus(text: String) { main.post { if (workVisible) refreshWorkStatus() else refreshDiscoverStatus() } }

    override fun onConnected(pcName: String) {
        main.post {
            prefs.edit().putBoolean("onboarded", true).apply()
            if (::onboardHint.isInitialized) onboardHint.visibility = View.GONE
            pendingIp?.let { ip -> pendingCode?.let { code -> saveLastSuccess(ip, pendingPort, code) } }
            discovery.stop(); scanner.stop()
            connected = true; showScreen(true); refreshWorkStatus(); addLog("已连接 $pcName"); snack("已连接 · $pcName")
        }
    }

    override fun onDisconnected(reason: String) {
        main.post { connected = false; if (workVisible) refreshWorkStatus(); addLog("连接断开，重连中…") }
    }

    override fun onAck(ok: Boolean, error: String) {
        main.post {
            if (ok) { snack("已上屏 ✓"); addLog("已上屏 ✓") }
            else { snack("上屏失败：${error.ifBlank { "未知错误" }}", error = true); addLog("上屏失败：$error") }
        }
    }

    override fun onSendError(reason: String) { main.post { snack(reason, error = true); addLog(reason) } }
}
