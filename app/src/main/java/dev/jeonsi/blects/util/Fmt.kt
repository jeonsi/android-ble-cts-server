package dev.jeonsi.blects.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Fmt {
    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val MD_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val LOG: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    fun at(epochMs: Long): ZonedDateTime = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())

    fun hm(epochMs: Long): String = at(epochMs).format(HM)

    /** 오늘이면 HH:mm, 아니면 MM-dd HH:mm */
    fun clock(epochMs: Long, nowMs: Long): String {
        val t = at(epochMs)
        return if (t.toLocalDate() == at(nowMs).toLocalDate()) t.format(HM) else t.format(MD_HM)
    }

    fun full(t: ZonedDateTime): String = t.format(FULL)
    fun full(epochMs: Long): String = at(epochMs).format(FULL)
    fun log(epochMs: Long): String = at(epochMs).format(LOG)

    /** "Asia/Seoul (UTC+09:00)" */
    fun zoneLabel(t: ZonedDateTime): String {
        val offset = t.offset.id.let { if (it == "Z") "+00:00" else it }
        return "${t.zone.id} (UTC$offset)"
    }

    /** "N분 전" / "N시간 전" / "N일 전" */
    fun ago(epochMs: Long, nowMs: Long): String {
        val min = ((nowMs - epochMs) / 60_000).coerceAtLeast(0)
        return when {
            min < 60 -> "${min}분 전"
            min < 48 * 60 -> "${min / 60}시간 전"
            else -> "${min / (24 * 60)}일 전"
        }
    }
}
