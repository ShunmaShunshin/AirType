package com.airtype.phone.net

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

/**
 * 监听 PC 端 UDP beacon（默认端口 47556），逐个回调发现的电脑。
 * 协议 v2：beacon 不含配对码，仅含名字/端口/实例ID；码由用户输入。
 * 若路由器/热点隔离广播导致收不到，可用 [SubnetScanner] 兜底扫描。
 */
class Discovery(private val onBeacon: (BeaconInfo) -> Unit) {

    data class BeaconInfo(
        val name: String,
        val port: Int,
        val ip: String,
        val instance: String = "",
        val fromScan: Boolean = false,
    )

    @Volatile private var running = false

    fun start(port: Int) {
        if (running) return
        running = true
        val t = Thread { runLoop(port) }
        t.isDaemon = true
        t.start()
    }

    fun stop() { running = false }

    private fun runLoop(port: Int) {
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket(port)
            sock.soTimeout = 2000
            val buf = ByteArray(2048)
            while (running) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val json = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val o = JSONObject(json)
                    if (o.optString("t") != "beacon") continue
                    val info = BeaconInfo(
                        name = o.optString("n", "?"),
                        port = o.optInt("p", 47555),
                        ip = pkt.address?.hostAddress ?: "?",
                        instance = o.optString("i", ""),
                        fromScan = false,
                    )
                    if (info.port in 1024..65535) onBeacon(info)
                } catch (e: SocketTimeoutException) {
                    // 正常：继续等待
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        } catch (e: Exception) {
            // 端口被占用等：停止发现（可手动/扫描连接）
        } finally {
            try { sock?.close() } catch (ignored: Exception) { }
        }
    }
}
