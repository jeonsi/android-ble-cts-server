package dev.jeonsi.blects.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jeonsi.blects.App
import dev.jeonsi.blects.data.Device
import dev.jeonsi.blects.service.ServiceState
import dev.jeonsi.blects.service.TimeServerService
import dev.jeonsi.blects.ui.SystemStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceUi(
    val address: String,
    val name: String?,
    val lastSyncAt: Long?,
    val connected: Boolean,
    val stale: Boolean,
) {
    val displayName: String get() = name ?: address
}

/** 상태 배너. 우선순위대로 하나만 보여 준다. */
sealed interface Banner {
    data object Permission : Banner
    data object BluetoothOff : Banner
    data class ServiceOff(val died: Boolean) : Banner
    data object Battery : Banner
    data object Notifications : Banner
    data class Stale(val sinceMs: Long) : Banner
}

data class HomeUiState(
    val serviceEnabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val bluetoothOn: Boolean = false,
    val autoTime: Boolean = true,
    val nowMs: Long = System.currentTimeMillis(),
    val devices: List<DeviceUi> = emptyList(),
    val banner: Banner? = null,
    /** 첫 동기화 성공 직후 배터리 최적화 제외를 한 번 물어야 하는 상태 */
    val batteryPromptDue: Boolean = false,
)

class HomeViewModel(private val app: App) : ViewModel() {
    private val container = app.container
    private val repo = container.repository
    private val settings = container.settings

    private val system = MutableStateFlow(SystemStatus.read(app))
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private data class Core(
        val devices: List<Device>,
        val running: Boolean,
        val connected: Set<String>,
        val enabled: Boolean,
        val batteryPromptDone: Boolean,
    )

    private val core: Flow<Core> = combine(
        repo.devices(), ServiceState.running, ServiceState.connected,
        settings.serviceEnabled, settings.batteryPromptDone,
    ) { devices, running, connected, enabled, promptDone ->
        Core(devices, running, connected, enabled, promptDone)
    }

    val uiState: StateFlow<HomeUiState> = combine(core, system, container.bluetooth.isOn, ticker) { c, sys, btOn, now ->
        build(c, sys, btOn, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun build(c: Core, sys: SystemStatus, btOn: Boolean, nowMs: Long): HomeUiState {
        val devices = c.devices.map { d ->
            DeviceUi(
                address = d.address,
                name = d.name,
                lastSyncAt = d.lastSyncAt,
                connected = d.address in c.connected,
                stale = d.lastSyncAt != null && nowMs - d.lastSyncAt > STALE_MS,
            )
        }
        val wantsService = c.enabled || devices.isNotEmpty()
        val banner: Banner? = when {
            wantsService && !sys.hasConnect -> Banner.Permission
            !btOn -> Banner.BluetoothOff
            wantsService && !c.running -> Banner.ServiceOff(died = c.enabled)
            c.running && !sys.ignoringBatteryOptimizations -> Banner.Battery
            c.running && !sys.notificationsEnabled -> Banner.Notifications
            c.running && devices.any { it.stale } ->
                Banner.Stale(devices.filter { it.stale }.minOf { it.lastSyncAt!! })
            else -> null
        }
        return HomeUiState(
            serviceEnabled = c.enabled,
            serviceRunning = c.running,
            bluetoothOn = btOn,
            autoTime = sys.autoTime,
            nowMs = nowMs,
            devices = devices,
            banner = banner,
            batteryPromptDue = c.running && !c.batteryPromptDone && !sys.ignoringBatteryOptimizations &&
                c.devices.any { it.lastSyncAt != null },
        )
    }

    fun refreshSystemStatus() {
        system.value = SystemStatus.read(app)
    }

    fun device(address: String): Flow<Device?> = repo.device(address)

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setServiceEnabled(enabled)
            if (enabled) TimeServerService.start(app) else TimeServerService.stop(app)
        }
    }

    /** 기기 추가 시트에서 탭. 서비스가 꺼져 있으면 같이 켠다. */
    suspend fun addDevice(address: String, name: String?) {
        repo.addDevice(address, name)
        settings.setServiceEnabled(true)
        TimeServerService.start(app, connectNow = address)
    }

    fun removeDevice(address: String) {
        viewModelScope.launch { repo.removeDevice(address) }
    }

    fun markBatteryPromptDone() {
        viewModelScope.launch { settings.setBatteryPromptDone(true) }
    }

    fun refreshNotification() = TimeServerService.refreshNotification(app)

    companion object {
        /** esp32-c3-clock 의 SYNC_STALE_MS = 주기(60분) + 5분 */
        const val STALE_MS = 65L * 60 * 1000

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App) }
        }
    }
}
