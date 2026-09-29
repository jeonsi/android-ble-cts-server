package dev.jeonsi.blects.ble

import java.util.Locale
import java.util.UUID

object Uuids {
    private fun sig(short: Int): UUID =
        UUID.fromString(String.format(Locale.ROOT, "%08x-0000-1000-8000-00805f9b34fb", short))

    val CTS_SERVICE: UUID = sig(0x1805)
    val CURRENT_TIME: UUID = sig(0x2A2B)
    val LOCAL_TIME_INFO: UUID = sig(0x2A0F)
    val CCCD: UUID = sig(0x2902)

    // Apple Notification Center Service (Apple 사설 UUID)
    val ANCS_SERVICE: UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
    val ANCS_NOTIFICATION_SOURCE: UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
    val ANCS_CONTROL_POINT: UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    val ANCS_DATA_SOURCE: UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")

    fun name(uuid: UUID): String = when (uuid) {
        CURRENT_TIME -> "Current Time"
        LOCAL_TIME_INFO -> "Local Time Information"
        ANCS_NOTIFICATION_SOURCE -> "ANCS Notification Source"
        ANCS_CONTROL_POINT -> "ANCS Control Point"
        ANCS_DATA_SOURCE -> "ANCS Data Source"
        else -> uuid.toString()
    }
}
