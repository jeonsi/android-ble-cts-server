package dev.jeonsi.blects.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 잠금 해제 전(Direct Boot)에도 읽히는 최소 상태.
 *
 * 폰 재부팅 뒤 일반 부팅 완료 신호는 잠금 해제 후에야 오고, 삼성 폰에서는 다른 앱들 뒤에 줄을 서서
 * 늦게 온다(실측: 부팅 뒤 4분 반). 잠금 해제 전 부팅 신호(LOCKED_BOOT_COMPLETED)로 서비스를 띄우려면
 * "켜 둘지"와 "어느 기기에 붙을지"를 잠금 해제 전에 알아야 하는데, Room DB와 DataStore는 잠금 해제
 * 뒤에만 열리는 저장소에 있다. 그래서 그 값들만 기기 보호 저장소에 한 벌 더 둔다(원본은 Room/DataStore).
 */
class BootState(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("boot_state", Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    @Synchronized
    fun devices(): List<Device> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Device(
                    address = o.getString("address"),
                    name = if (o.isNull("name")) null else o.getString("name"),
                    addedAt = o.optLong("addedAt"),
                    lastSyncAt = if (o.isNull("lastSyncAt")) null else o.getLong("lastSyncAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun setDevices(list: List<Device>) {
        val arr = JSONArray()
        for (d in list) {
            arr.put(
                JSONObject()
                    .put("address", d.address)
                    .put("name", d.name ?: JSONObject.NULL)
                    .put("addedAt", d.addedAt)
                    .put("lastSyncAt", d.lastSyncAt ?: JSONObject.NULL),
            )
        }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    @Synchronized
    fun markSynced(address: String, at: Long) {
        setDevices(devices().map { if (it.address == address) it.copy(lastSyncAt = at) else it })
    }

    private companion object {
        const val KEY_ENABLED = "service_enabled"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_DEVICES = "devices"
    }
}

/** 사용자 저장소(Room, DataStore)가 열렸는가. 잠금 해제 전 부팅이면 false 로 시작해 해제 시 true. */
class UnlockMonitor(context: Context) {
    private val userManager = context.getSystemService(UserManager::class.java)
    private val _unlocked = MutableStateFlow(userManager.isUserUnlocked)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    init {
        if (!_unlocked.value) {
            val app = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    _unlocked.value = true
                    runCatching { app.unregisterReceiver(this) }
                }
            }
            // USER_UNLOCKED 는 매니페스트가 아니라 등록된 리시버에만 온다. 시스템 보호 브로드캐스트.
            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_EXPORTED,
            )
            // 등록 사이에 해제됐을 수 있다
            if (userManager.isUserUnlocked) _unlocked.value = true
        }
    }
}
