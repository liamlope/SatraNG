package com.v2ray.ang.handler

import java.io.File

/**
 * Reads Android's `/proc/net/{tcp,tcp6,udp,udp6}` tables and counts sockets per UID.
 *
 * Under a VPN, [android.net.TrafficStats] / NetworkStats often credit tunnelled bytes to the VPN
 * app UID. Active client apps still show up here as socket owners, so we can weight how to
 * attribute unexplained VPN-core bytes across packages — same method for every app.
 */
object ProcNetUidScanner {

    private val TABLES = listOf(
        "/proc/net/tcp",
        "/proc/net/tcp6",
        "/proc/net/udp",
        "/proc/net/udp6",
    )

    /** UID → number of sockets currently listed in the kernel tables. */
    fun connectionCounts(): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (path in TABLES) {
            val lines = runCatching { File(path).readLines() }.getOrNull() ?: continue
            for (i in 1 until lines.size) {
                val parts = lines[i].trim().split(Regex("\\s+"))
                if (parts.size < 8) continue
                val uid = parts[7].toIntOrNull() ?: continue
                out[uid] = (out[uid] ?: 0) + 1
            }
        }
        return out
    }

    /**
     * Splits [bytes] across [weights] proportional to each weight.
     * Remainder from integer division goes to the heaviest UID so the sum matches.
     */
    internal fun distributeByWeight(bytes: Long, weights: Map<Int, Int>): Map<Int, Long> {
        if (bytes <= 0L || weights.isEmpty()) return emptyMap()
        val positive = weights.filterValues { it > 0 }
        if (positive.isEmpty()) return emptyMap()
        val totalW = positive.values.sum().toLong()
        if (totalW <= 0L) return emptyMap()
        val out = HashMap<Int, Long>(positive.size)
        var assigned = 0L
        val ordered = positive.entries.sortedByDescending { it.value }
        for ((uid, w) in ordered) {
            val share = bytes * w / totalW
            if (share > 0L) {
                out[uid] = share
                assigned += share
            }
        }
        val rem = bytes - assigned
        if (rem > 0L) {
            val top = ordered.first().key
            out[top] = (out[top] ?: 0L) + rem
        }
        return out
    }
}
