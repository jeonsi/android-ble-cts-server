package dev.jeonsi.blects.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class Settings(private val context: Context, private val bootState: BootState) {
    private object Keys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val BATTERY_PROMPT_DONE = booleanPreferencesKey("battery_prompt_done")
    }

    /** 사용자가 원하는 서비스 상태. 부팅 후 복원에 쓴다. */
    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SERVICE_ENABLED] ?: false }
    val autoStart: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_START] ?: true }
    val batteryPromptDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.BATTERY_PROMPT_DONE] ?: false }

    suspend fun setServiceEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SERVICE_ENABLED] = value }
        bootState.serviceEnabled = value
    }

    suspend fun setAutoStart(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START] = value }
        bootState.autoStart = value
    }

    /** 잠금 해제 뒤 원본 값을 잠금 해제 전 저장소로 복사한다(업데이트 전에 켜 둔 설정 포함). */
    suspend fun mirrorToBootState() {
        bootState.serviceEnabled = serviceEnabled.first()
        bootState.autoStart = autoStart.first()
    }

    suspend fun setBatteryPromptDone(value: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_PROMPT_DONE] = value }
    }
}
