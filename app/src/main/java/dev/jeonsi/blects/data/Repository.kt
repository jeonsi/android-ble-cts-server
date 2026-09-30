package dev.jeonsi.blects.data

import android.content.Context
import android.os.Build
import dev.jeonsi.blects.R
import dev.jeonsi.blects.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

class Repository(
    private val context: Context,
    db: AppDatabase,
    private val bootState: BootState,
    private val unlocked: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val devices = db.deviceDao()
    private val events = db.eventDao()

    /** 잠금 해제 전에 생긴 로그·동기화 기록. 해제되면 순서대로 DB 에 쓴다. */
    private val lock = Mutex()
    private val pendingEvents = ArrayList<Event>()
    private val pendingSyncs = LinkedHashMap<String, Long>()
    private var flushed = false

    init {
        scope.launch {
            unlocked.first { it }
            onUnlocked()
        }
    }

    private suspend fun onUnlocked() {
        lock.withLock {
            for (e in pendingEvents) events.insert(e)
            for ((address, at) in pendingSyncs) devices.markSynced(address, at)
            pendingEvents.clear()
            pendingSyncs.clear()
            flushed = true
        }
        // 원본(Room) → 잠금 해제 전 저장소. 업데이트 전부터 등록돼 있던 기기도 여기서 옮겨진다.
        devices.all().collect { bootState.setDevices(it) }
    }

    /** 잠금 해제 전에는 잠금 해제 전 저장소의 사본, 해제 뒤에는 Room. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun devices(): Flow<List<Device>> = unlocked.flatMapLatest { open ->
        if (open) devices.all() else flowOf(bootState.devices())
    }

    fun device(address: String): Flow<Device?> = devices.byAddress(address)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun lastSyncAny(): Flow<Long?> = unlocked.flatMapLatest { open ->
        if (open) devices.lastSyncAny() else flowOf(bootState.devices().mapNotNull { it.lastSyncAt }.maxOrNull())
    }

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

    suspend fun markSynced(address: String, at: Long) {
        bootState.markSynced(address, at)
        lock.withLock {
            if (!flushed) {
                pendingSyncs[address] = at
                return
            }
        }
        devices.markSynced(address, at)
    }

    /** 어디서든 부담 없이 호출하는 fire-and-forget 로그. */
    fun log(type: EventType, address: String? = null, detail: String? = null) {
        val e = Event(at = System.currentTimeMillis(), address = address, type = type, detail = detail)
        scope.launch { insertOrHold(e) }
    }

    suspend fun logNow(type: EventType, address: String? = null, detail: String? = null) {
        insertOrHold(Event(at = System.currentTimeMillis(), address = address, type = type, detail = detail))
    }

    private suspend fun insertOrHold(e: Event) {
        lock.withLock {
            if (!flushed) {
                pendingEvents += e
                return
            }
        }
        events.insert(e)
    }

    suspend fun pruneOldEvents() {
        if (!unlocked.value) return
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
