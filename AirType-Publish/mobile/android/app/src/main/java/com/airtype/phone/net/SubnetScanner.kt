package com.airtype.phone.net

import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 局域网/热点兜底扫描：UDP 广播收不到时，用 TCP 探测 PC 端口。
 * 候选网段：手机热点默认 192.168.43.x + 当前 WiFi 同网段（前缀>=24 才枚举）。
 * 命中规则：能连上端口且收到 {"t":"challenge"} 应答（说明是 AirTypePC/PhmicPC）。
 */
class SubnetScanner(private val cm: ConnectivityManager?, private val port: Int) {

    interface Result {
        fun onProgress(text: String)
        fun onFound(ip: String)
        fun onDone(text: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newFixedThreadPool(16)
    @Volatile private var running = false
    @Volatile private var activeResult: Result? = null

    fun start(result: Result) {
        if (running) return
        running = true
        activeResult = result

        val hosts = LinkedHashSet<String>()
        val own = ownIpv4Host()
        if (own != null && currentPrefix() >= 24) {
            val parts = own.split('.')
            val base = parts[0] + "." + parts[1] + "." + parts[2] + "."
            for (i in 1..254) {
                val h = base + i
                if (h != own) hosts.add(h)
            }
        }
        for (i in 2..254) hosts.add("192.168.43.$i") // 手机热点默认网段

        val total = hosts.size
        main.post { result.onProgress("扫描中…$total 个地址（约 5~15 秒）") }

        Thread {
            try {
                for (h in hosts) {
                    if (!running) break
                    executor.submit { probe(h) }
                }
                executor.shutdown()
                executor.awaitTermination(30, TimeUnit.SECONDS)
            } catch (ignored: Exception) {
            } finally {
                running = false
                main.post { result.onDone("扫描完成") }
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        executor.shutdownNow()
    }

    private fun probe(host: String) {
        var s: Socket? = null
        try {
            s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 300)
            s.soTimeout = 400
            val out = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
            out.write("{\"t\":\"pair\",\"d\":\"Scanner\"}\n")
            out.flush()
            val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
            val line = reader.readLine()
            if (line != null && line.contains("\"t\":\"challenge\"")) {
                main.post { activeResult?.onFound(host) }
            }
        } catch (ignored: Exception) {
        } finally {
            try { s?.close() } catch (ignored: Exception) { }
        }
    }

    private fun ownIpv4Host(): String? {
        return try {
            val net = cm?.activeNetwork ?: return null
            val lp = cm.getLinkProperties(net) ?: return null
            lp.linkAddresses
                .mapNotNull { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        } catch (e: Exception) { null }
    }

    private fun currentPrefix(): Int {
        return try {
            val net = cm?.activeNetwork ?: return 0
            val lp = cm.getLinkProperties(net) ?: return 0
            lp.linkAddresses.firstOrNull { it.address is Inet4Address }?.prefixLength ?: 0
        } catch (e: Exception) { 0 }
    }
}
