package dev.jeonsi.blects.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeCodecTest {

    @Test
    fun currentTime_encodesFieldsLittleEndianYearAndIsoDayOfWeek() {
        // 2026-09-29 는 화요일(ISO 2)
        val t = ZonedDateTime.of(2026, 9, 29, 14, 3, 13, 500_000_000, ZoneId.of("Asia/Seoul"))
        val bytes = TimeCodec.currentTime(t, TimeCodec.ADJUST_MANUAL)
        assertEquals(10, bytes.size)
        assertArrayEquals(
            byteArrayOf(
                0xEA.toByte(), 0x07, // 2026 = 0x07EA
                9, 29, 14, 3, 13,
                2,                    // Tuesday
                128.toByte(),         // 0.5 s * 256
                TimeCodec.ADJUST_MANUAL.toByte(),
            ),
            bytes,
        )
    }

    @Test
    fun currentTime_sundayIsSeven() {
        val t = ZonedDateTime.of(2026, 10, 4, 0, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(7, TimeCodec.currentTime(t)[7].toInt())
    }

    @Test
    fun localTimeInformation_seoulIsPlusNineNoDst() {
        val t = ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        assertArrayEquals(byteArrayOf(36, 0), TimeCodec.localTimeInformation(t))
    }

    @Test
    fun localTimeInformation_reportsStandardOffsetAndDstSeparately() {
        // 베를린 여름: 표준 +1h(4 단위), DST +1h(코드 4)
        val summer = ZonedDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertArrayEquals(byteArrayOf(4, 4), TimeCodec.localTimeInformation(summer))
        val winter = ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertArrayEquals(byteArrayOf(4, 0), TimeCodec.localTimeInformation(winter))
    }

    @Test
    fun localTimeInformation_negativeOffset() {
        val t = ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneId.of("America/New_York"))
        assertArrayEquals(byteArrayOf(-20, 0), TimeCodec.localTimeInformation(t))
    }
}
