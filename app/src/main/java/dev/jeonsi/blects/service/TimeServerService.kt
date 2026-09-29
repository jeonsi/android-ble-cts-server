package dev.jeonsi.blects.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.jeonsi.blects.App
import dev.jeonsi.blects.R
import dev.jeonsi.blects.ble.BluetoothMonitor
import dev.jeonsi.blects.ble.Bonder
import dev.jeonsi.blects.ble.CtsGattServer
import dev.jeonsi.blects.ble.DeviceLink
import dev.jeonsi.blects.ble.TimeCodec
import dev.jeonsi.blects.ble.Uuids
import dev.jeonsi.blects.data.Device
import dev.jeonsi.blects.data.EventType
import dev.jeonsi.blects.data.Repository
import dev.jeonsi.blects.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/**
 * 앱의 전부인 foreground service. 켜지면 GATT 서버가 올라가고, 등록된 기기마다 재연결을 걸어 둔다.
 *
 * 블루투스가 꺼져도 서비스는 살아 있다가(알림 유지) 다시 켜지면 스스로 올라온다.
 */
class TimeServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: Repository
    private lateinit var bt: BluetoothMonitor

    private var gattServer: CtsGattServer? = null
    private val links = LinkedHashMap<String, DeviceLink>()
    private var seenAddresses: Set<String>? = null
    private val connectNow = mutableSetOf<String>()
    private var up = false
    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as App).container
        repo = container.repository
        bt = container.bluetooth

        try {
            ServiceCompat.startForeground(
                this, Notifications.ID, Notifications.build(this, null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException(권한 회수 등)
            repo.log(EventType.ERROR, null, getString(R.string.detail_fgs_failed, e.javaClass.simpleName))
            stopSelf()
            return
        }

        ServiceState.running.value = true
        repo.log(EventType.SERVICE_STARTED)
        registerReceiver()

        scope.launch {
            bt.isOn.collectIndexed { index, on ->
                if (index > 0) repo.log(if (on) EventType.BLUETOOTH_ON else EventType.BLUETOOTH_OFF)
                if (on) bringUp() else tearDown()
            }
        }
        scope.launch { repo.devices().collect { syncLinks(it) } }
        scope.launch { repo.lastSyncAny().collect { Notifications.update(this@TimeServerService, it) } }
        scope.launch {
            while (isActive) {
                repo.pruneOldEvents()
                delay(PRUNE_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_NOTIFICATION -> scope.launch {
                Notifications.update(this@TimeServerService, repo.lastSyncAny().first())
            }
        }
        intent?.getStringExtra(EXTRA_CONNECT_NOW)?.let { address ->
            val link = links[address]
            if (link != null && up) link.start(direct = true) else connectNow += address
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tearDown()
        if (receiverRegistered) runCatching { unregisterReceiver(receiver) }
        if (ServiceState.running.value) {
            ServiceState.running.value = false
            repo.log(EventType.SERVICE_STOPPED)
        }
        scope.cancel()
        super.onDestroy()
    }

    // ---- GATT 서버 + 링크 -------------------------------------------------

    private fun bringUp() {
        if (up) return
        val server = CtsGattServer(this, serverListener)
        if (!server.open()) {
            repo.log(EventType.ERROR, null, getString(R.string.detail_gatt_open_failed))
            return
        }
        gattServer = server
        up = true
        for ((address, link) in links) {
            link.start(direct = connectNow.remove(address))
        }
    }

    private fun tearDown() {
        if (!up) return
        up = false
        links.values.forEach { it.stop() }
        gattServer?.close()
        gattServer = null
        ServiceState.connected.value = emptySet()
    }

    private fun syncLinks(devices: List<Device>) {
        val wanted = devices.map { it.address }.toSet()
        val firstPass = seenAddresses == null
        for (address in links.keys - wanted) links.remove(address)?.stop()
        for (address in wanted - links.keys) {
            val link = DeviceLink(this, address, scope, object : DeviceLink.Listener {
                override fun onBadAddress() =
                    repo.log(EventType.ERROR, address, getString(R.string.detail_bad_address))

                override fun onRetry(status: Int) =
                    repo.log(EventType.ERROR, address, getString(R.string.detail_link_retry, status))
            })
            links[address] = link
            // 서비스가 이미 떠 있는데 새로 추가된 기기 = 사용자가 방금 탭한 기기 → 즉시 연결
            val direct = !firstPass || connectNow.remove(address)
            if (up) link.start(direct = direct) else if (direct) connectNow += address
        }
        seenAddresses = wanted
    }

    private val serverListener = object : CtsGattServer.Listener {
        override fun onConnected(device: BluetoothDevice) {
            scope.launch {
                ServiceState.connected.update { it + device.address }
                repo.log(EventType.CONNECTED, device.address)
            }
        }

        override fun onDisconnected(device: BluetoothDevice, status: Int) {
            scope.launch {
                ServiceState.connected.update { it - device.address }
                repo.log(EventType.DISCONNECTED, device.address, disconnectReason(status))
            }
        }

        override fun onCurrentTimeRead(device: BluetoothDevice, sent: java.time.ZonedDateTime) {
            scope.launch {
                val at = System.currentTimeMillis()
                ServiceState.lastRead.update { it + (device.address to at) }
                repo.markSynced(device.address, at)
                repo.log(EventType.TIME_READ, device.address, "${Fmt.full(sent)} ${Fmt.zoneLabel(sent)}")
            }
        }

        override fun onLocalTimeRead(device: BluetoothDevice) {
            scope.launch { repo.log(EventType.LOCAL_TIME_READ, device.address) }
        }

        override fun onSubscriptionChanged(device: BluetoothDevice, characteristic: UUID, enabled: Boolean) {
            scope.launch {
                val name = Uuids.name(characteristic)
                val text = if (enabled) getString(R.string.detail_subscribed, name) else getString(R.string.detail_unsubscribed, name)
                repo.log(EventType.SUBSCRIPTION, device.address, text)
            }
        }

        override fun onServiceAddFailed(service: UUID) {
            scope.launch {
                repo.log(EventType.ERROR, null, getString(R.string.detail_service_add_failed, Uuids.name(service)))
            }
        }
    }

    private fun disconnectReason(status: Int): String = when (status) {
        0 -> getString(R.string.reason_ok)
        0x08 -> getString(R.string.reason_timeout)
        0x13 -> getString(R.string.reason_remote)
        0x16 -> getString(R.string.reason_local)
        0x3E -> getString(R.string.reason_establish)
        133 -> getString(R.string.reason_gatt_133)
        else -> getString(R.string.reason_status, status)
    }

    // ---- 시스템 브로드캐스트 ----------------------------------------------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIME_CHANGED -> {
                    gattServer?.notifyTimeChanged(TimeCodec.ADJUST_MANUAL)
                    repo.log(EventType.TIME_CHANGED, null, getString(R.string.detail_time_changed))
                }
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    gattServer?.notifyTimeChanged(TimeCodec.ADJUST_TIMEZONE)
                    repo.log(EventType.TIME_CHANGED, null, getString(R.string.detail_timezone_changed, ZoneId.systemDefault().id))
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> onBondChanged(intent)
            }
        }
    }

    private fun onBondChanged(intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val address = device.address
        if (address !in links) return
        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
        val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
        when {
            state == BluetoothDevice.BOND_BONDED -> repo.log(EventType.BOND, address, getString(R.string.detail_bonded))
            state == BluetoothDevice.BOND_BONDING -> repo.log(EventType.BOND, address, getString(R.string.detail_bonding))
            state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING -> {
                val reason = intent.getIntExtra(Bonder.EXTRA_REASON, -1)
                repo.log(EventType.ERROR, address, getString(R.string.detail_pairing_failed_reason, reason))
                ServiceState.pairingFailed.value = address
            }
            state == BluetoothDevice.BOND_NONE -> repo.log(EventType.BOND, address, getString(R.string.detail_unbonded))
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    companion object {
        private const val ACTION_STOP = "dev.jeonsi.blects.action.STOP"
        private const val ACTION_REFRESH_NOTIFICATION = "dev.jeonsi.blects.action.REFRESH_NOTIFICATION"
        private const val EXTRA_CONNECT_NOW = "connect_now"
        private const val PRUNE_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** @param connectNow 방금 추가한 기기 주소. 자동 재연결 대신 즉시 연결을 시도한다. */
        fun start(context: Context, connectNow: String? = null) {
            val intent = Intent(context, TimeServerService::class.java)
            if (connectNow != null) intent.putExtra(EXTRA_CONNECT_NOW, connectNow)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (!ServiceState.running.value) return
            context.startService(Intent(context, TimeServerService::class.java).setAction(ACTION_STOP))
        }

        fun refreshNotification(context: Context) {
            if (!ServiceState.running.value) return
            context.startService(Intent(context, TimeServerService::class.java).setAction(ACTION_REFRESH_NOTIFICATION))
        }
    }
}
