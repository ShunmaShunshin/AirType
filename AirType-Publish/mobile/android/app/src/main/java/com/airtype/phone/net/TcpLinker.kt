package com.airtype.phone.net

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 与 PC 端（AirTypePC/PhmicPC）的 TCP 连接管理。
 * 配对 v2 挑战-应答：连接后发 pair -> 收 challenge(nonce) ->
 * 回 auth(SHA-256(code+nonce)) -> 收到 pair ok 才进入已连接态。
 * 配对码不会明文出现在网络报文里；断线按上次成功参数每 3 秒自动重连；
 * 码错误则停止自动重连。
 */
class TcpLinker(private val callbacks: Callbacks) {

    interface Callbacks {
        fun onStatus(text: String)
        fun onConnected(pcName: String)
        fun onDisconnected(reason: String)
        fun onAck(ok: Boolean, error: String)
        fun onSendError(reason: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val ioExec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "airtype-sender").apply { isDaemon = true }
    }
    private val idSeq = AtomicInteger(1)
    private val generation = AtomicInteger(0)

    @Volatile private var socket: Socket? = null
    @Volatile var deviceName: String = android.os.Build.MODEL
    @Volatile var connected: Boolean = false
        private set

    private var stoppedByUser = false
    private var badCode = false
    private var lastIp: String? = null
    private var lastPort = 47555
    private var lastCode: String? = null

    fun connect(ip: String, port: Int, code: String) {
        lastIp = ip; lastPort = port; lastCode = code
        val gen = generation.incrementAndGet()
        stoppedByUser = false; badCode = false
        status("正在连接 $ip:$port …")
        startSessionThread(ip, port, code, gen)
    }

    fun stop() {
        generation.incrementAndGet()
        stoppedByUser = true
        closeSocket()
    }

    fun sendText(text: String): String? {
        val s = socket
        if (s == null || !connected) return "未连接到电脑（可能正在自动重连，请稍候）"
        val payload = JSONObject()
            .put("t", "text")
            .put("id", idSeq.getAndIncrement())
            .put("s", text)
            .toString() + "\n"
        val bytes = payload.toByteArray(Charsets.UTF_8)
        ioExec.execute {
            try {
                val cur = socket
                if (cur == null || !connected) throw IllegalStateException("连接已断开")
                synchronized(cur) {
                    val os = cur.getOutputStream()
                    os.write(bytes); os.flush()
                }
            } catch (e: Exception) {
                closeSocket()
                scheduleReconnectSoon()
                val cls = e.javaClass.simpleName
                val msg = e.message?.takeIf { it.isNotBlank() }
                val reason = if (msg == null) "发送失败（$cls）" else "发送失败（$cls：$msg）"
                main.post { callbacks.onSendError(reason) }
            }
        }
        return null
    }

    private fun scheduleReconnectSoon() {
        val ip = lastIp; val code = lastCode
        if (stoppedByUser || badCode || ip == null || code == null) return
        main.postDelayed({
            if (!stoppedByUser && !badCode && !connected) connect(ip, lastPort, code)
        }, 1500)
    }

    private fun startSessionThread(ip: String, port: Int, code: String, gen: Int) {
        val t = Thread {
            while (!stoppedByUser && !badCode && gen == generation.get()) {
                var s: Socket? = null
                try {
                    s = Socket()
                    s.tcpNoDelay = true
                    s.connect(InetSocketAddress(ip, port), 4000)
                    s.soTimeout = 15000
                    socket = s

                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val out = PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true)

                    val pair = JSONObject().put("t", "pair").put("d", deviceName)
                    out.println(pair.toString())

                    var paired = false
                    var pcName = ip
                    val deadline = System.currentTimeMillis() + 10000
                    while (!paired && !stoppedByUser && !badCode) {
                        if (System.currentTimeMillis() > deadline) break
                        val line: String?
                        try { line = reader.readLine() } catch (e: SocketTimeoutException) { continue }
                        if (line == null) break
                        if (line.isBlank()) continue
                        try {
                            val o = JSONObject(line)
                            when (o.optString("t")) {
                                "challenge" -> {
                                    val nonce = o.optString("n", "")
                                    if (nonce.length == 32) {
                                        val h = Auth.sha256Hex(code + nonce)
                                        out.println(JSONObject().put("t", "auth").put("d", deviceName).put("h", h).toString())
                                    }
                                }
                                "pair" -> {
                                    if (o.optBoolean("ok")) {
                                        paired = true
                                        pcName = o.optString("n", ip)
                                    } else {
                                        badCode = true
                                        status("配对码错误，请检查电脑屏幕上显示的码")
                                    }
                                }
                            }
                        } catch (ignored: Exception) { }
                    }

                    if (!paired) {
                        s.close()
                        if (!badCode) status("认证未完成（超时或被拒）")
                    } else {
                        connected = true
                        status("已连接：$pcName")
                        main.post { callbacks.onConnected(pcName) }

                        var sessionEnded = false
                        while (!stoppedByUser && !sessionEnded && gen == generation.get()) {
                            val line: String?
                            try {
                                line = reader.readLine()
                            } catch (e: SocketTimeoutException) {
                                try {
                                    synchronized(s) { out.println("{\"t\":\"ping\"}") }
                                } catch (e2: Exception) { sessionEnded = true }
                                continue
                            } catch (e: Exception) { sessionEnded = true; continue }
                            if (line == null) break
                            if (line.isBlank()) continue
                            try {
                                val o = JSONObject(line)
                                when (o.optString("t")) {
                                    "ack" -> main.post { callbacks.onAck(o.optBoolean("ok"), o.optString("e", "")) }
                                    "pong" -> { }
                                }
                            } catch (ignored: Exception) { }
                        }

                        s.close()
                        if (gen == generation.get()) {
                            connected = false
                            if (!stoppedByUser) {
                                status("连接断开，3 秒后自动重连…")
                                main.post { callbacks.onDisconnected("连接断开") }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!stoppedByUser && !badCode && gen == generation.get()) status("连接失败（$ip:$port），3 秒后重试…")
                } finally {
                    if (gen == generation.get()) connected = false
                    if (socket === s) socket = null
                    try { s?.close() } catch (ignored: Exception) { }
                }

                if (stoppedByUser || badCode || gen != generation.get()) break
                try { Thread.sleep(3000) } catch (e: InterruptedException) { break }
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun closeSocket() {
        val s = socket
        socket = null
        try { s?.close() } catch (ignored: Exception) { }
        connected = false
    }

    private fun status(text: String) { main.post { callbacks.onStatus(text) } }
}
