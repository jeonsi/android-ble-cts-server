package dev.jeonsi.blects.data

import android.content.Context
import android.os.Build
import dev.jeonsi.blects.R
import dev.jeonsi.blects.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class Repository(
    private val context: Context,
    db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val devices = db.deviceDao()
    private val events = db.eventDao()

    fun devices(): Flow<List<Device>> = devices.all()
    fun device(address: String): Flow<Device?> = devices.byAddress(address)
    fun lastSyncAny(): Flow<Long?> = devices.lastSyncAny()
    fun events(address: String): Flow<List<Event>> = events.forDevice(address)

    suspend fun addDevice(address: String, name: String?) {
        val existing = devices.get(address)
        if (existing == null) {
            devices.insert(Device(address = address, name = name, addedAt = System.currentTimeMillis()))
        } else if (name != null && existing.name != name) {
            devices.updateName(address, name)
        }
        logNow(EventType.DEVICE_ADDED, address, name)
    }

    suspend fun removeDevice(address: String) {
        devices.delete(address)
        logNow(EventType.DEVICE_REMOVED, address)
    }

    suspend fun markSynced(address: String, at: Long) = devices.markSynced(address, at)

    /** 어디서든 부담 없이 호출하는 fire-and-forget 로그. */
    fun log(type: EventType, address: String? = null, detail: String? = null) {
        scope.launch { logNow(type, address, detail) }
    }

    suspend fun logNow(type: EventType, address: String? = null, detail: String? = null) {
        events.insert(Event(at = System.currentTimeMillis(), address = address, type = type, detail = detail))
    }

    suspend fun pruneOldEvents() {
        events.prune(System.currentTimeMillis() - RETENTION_MS)
    }

    /** 이슈 리포트용 텍스트. */
    suspend fun exportText(): String {
        val sb = StringBuilder()
        sb.appendLine("BLE CTS Server log")
        sb.appendLine("exported: ${Fmt.full(ZonedDateTime.now())} ${Fmt.zoneLabel(ZonedDateTime.now())}")
        sb.appendLine("phone: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("app: ${appVersion()}")
        sb.appendLine()
        sb.appendLine("devices:")
        val snapshot = devices.snapshot()
        if (snapshot.isEmpty()) sb.appendLine("  (none)")
        for (d in snapshot) {
            sb.appendLine("  ${d.address}  ${d.name ?: "-"}  lastSync=${d.lastSyncAt?.let { Fmt.full(it) } ?: "-"}")
        }
        sb.appendLine()
        sb.appendLine("events:")
        for (e in events.allForExport()) {
            sb.append(Fmt.full(e.at)).append("  ")
            sb.append(e.address ?: "-----------------").append("  ")
            sb.appendLine(e.text(context))
        }
        return sb.toString()
    }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** 로그 한 줄 문구. 화면과 내보내기에서 같이 쓴다. */
fun Event.text(context: Context): String {
    val r = context.resources
    val base = when (type) {
        EventType.CONNECTED -> r.getString(R.string.ev_connected)
        EventType.DISCONNECTED -> r.getString(R.string.ev_disconnected)
        EventType.TIME_READ -> r.getString(R.string.ev_time_read)
        EventType.LOCAL_TIME_READ -> r.getString(R.string.ev_local_time_read)
        EventType.SERVICE_STARTED -> r.getString(R.string.ev_service_started)
        EventType.SERVICE_STOPPED -> r.getString(R.string.ev_service_stopped)
        EventType.BLUETOOTH_ON -> r.getString(R.string.ev_bt_on)
        EventType.BLUETOOTH_OFF -> r.getString(R.string.ev_bt_off)
        EventType.BOOT -> r.getString(R.string.ev_boot)
        EventType.DEVICE_ADDED -> r.getString(R.string.ev_device_added)
        EventType.DEVICE_REMOVED -> r.getString(R.string.ev_device_removed)
        EventType.ERROR -> r.getString(R.string.ev_error)
        EventType.SUBSCRIPTION, EventType.BOND, EventType.TIME_CHANGED -> return detail ?: type.name
    }
    return if (detail.isNullOrBlank()) base else "$base ($detail)"
}
