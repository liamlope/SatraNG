package com.v2ray.ang.ui.compose.satra

import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toTrafficString
import java.util.Locale

fun formatBytes(bytes: Long): String = bytes.coerceAtLeast(0L).toTrafficString()

fun formatSpeed(bytesPerSecond: Long): String = bytesPerSecond.coerceAtLeast(0L).toSpeedString()

/** HH:MM:SS (or MM:SS under an hour). */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
}

/** "2 h 05 m" style duration for charts. */
fun formatHoursMinutes(totalSeconds: Long, hourUnit: String, minuteUnit: String): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> String.format(Locale.getDefault(), "%d %s %02d %s", h, hourUnit, m, minuteUnit)
        else -> String.format(Locale.getDefault(), "%d %s", m, minuteUnit)
    }
}

/** Current UI locale, observable so composables update when the language changes. */
@androidx.compose.runtime.Composable
fun currentLocale(): java.util.Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
