package dev.jeonsi.blects.ble

import java.time.ZonedDateTime

/**
 * Bluetooth SIG 시간 특성 인코딩.
 *
 * Current Time (0x2A2B), 10 bytes:
 *   year(u16 LE) month day hours minutes seconds day_of_week fractions256 adjust_reason
 * Local Time Information (0x2A0F), 2 bytes:
 *   time_zone(s8, 15분 단위, 표준시 기준) dst_offset(u8)
 */
object TimeCodec {
    const val ADJUST_MANUAL = 0x01
    const val ADJUST_EXTERNAL_REFERENCE = 0x02
    const val ADJUST_TIMEZONE = 0x04
    const val ADJUST_DST = 0x08

    fun currentTime(t: ZonedDateTime, adjustReason: Int = 0): ByteArray {
        val year = t.year
        val fractions = (t.nano / 1_000_000_000.0 * 256).toInt().coerceIn(0, 255)
        return byteArrayOf(
            (year and 0xFF).toByte(),
            ((year shr 8) and 0xFF).toByte(),
            t.monthValue.toByte(),
            t.dayOfMonth.toByte(),
            t.hour.toByte(),
            t.minute.toByte(),
            t.second.toByte(),
            t.dayOfWeek.value.toByte(), // ISO 월=1 … 일=7, SIG 정의와 동일
            fractions.toByte(),
            (adjustReason and 0xFF).toByte(),
        )
    }

    fun localTimeInformation(t: ZonedDateTime): ByteArray {
        val rules = t.zone.rules
        val instant = t.toInstant()
        val standardOffsetSec = rules.getStandardOffset(instant).totalSeconds
        val timeZone = (standardOffsetSec / 900).coerceIn(-48, 56)
        val dstMinutes = rules.getDaylightSavings(instant).toMinutes()
        val dst = when (dstMinutes) {
            0L -> 0
            30L -> 2
            60L -> 4
            120L -> 8
            else -> 255
        }
        return byteArrayOf(timeZone.toByte(), dst.toByte())
    }
}
