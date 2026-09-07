package com.airtype.phone.net

import java.security.MessageDigest

object Auth {
    private val HEX = "0123456789abcdef".toCharArray()

    /** SHA-256(s) 的 64 位小写十六进制（与 PC 端一致，用于挑战-应答认证）。 */
    fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0f])
        }
        return sb.toString()
    }
}
