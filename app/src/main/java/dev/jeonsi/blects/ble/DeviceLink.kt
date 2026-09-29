package dev.jeonsi.blects.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 등록된 기기 하나에 대한 central 쪽 연결 유지.
 *
 * 폰이 연결을 "걸어 두면"(autoConnect) 기기가 광고를 시작할 때 블루투스 스택이 스스로 붙는다.
 * 연결 자체가 목적이고, 데이터는 반대 방향(기기가 폰의 GATT 서버를 읽음)으로 흐르므로
 * 여기서는 서비스 탐색이나 읽기를 하지 않는다.
 *
 * 모든 메서드는 [scope] 의 디스패처(메인)에서 호출한다고 가정한다.
 */
@SuppressLint("MissingPermission")
class DeviceLink(
    private val context: Context,
    val address: String,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onBadAddress()
        /** 비정상 GATT 상태로 끊겨 잠시 후 다시 연결을 건다. */
        fun onRetry(status: Int)
    }

    private var gatt: BluetoothGatt? = null
    private var active = false
    private var retryJob: Job? = null

    /**
     * @param direct true 면 즉시 연결을 시도(사용자가 방금 추가한 기기, 광고 중이라고 가정).
     *               false 면 백그라운드 자동 재연결로 걸어 둔다.
     */
    fun start(direct: Boolean) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            listener.onBadAddress(); return
        }
        active = true
        retryJob?.cancel()
        gatt?.let { runCatching { it.close() } }
        // API 37 의 BluetoothGattConnectionSettings 오버로드는 minSdk 29 에서 쓸 수 없다.
        @Suppress("DEPRECATION")
        gatt = device.connectGatt(context, !direct, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun stop() {
        active = false
        retryJob?.cancel()
        gatt?.let { g ->
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        gatt = null
    }

    private fun scheduleRestart(status: Int) {
        listener.onRetry(status)
        gatt?.let { runCatching { it.close() } }
        gatt = null
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)
            if (active) start(direct = false)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                if (!active || g !== gatt) return@launch
                if (newState != BluetoothProfile.STATE_DISCONNECTED) return@launch
                when (status) {
                    GATT_ERROR_133 -> scheduleRestart(status)
                    else -> {
                        // 정상적인 끊김(기기가 라디오를 끔 등). 다음 광고를 기다리도록 다시 걸어 둔다.
                        if (!g.connect()) scheduleRestart(status)
                    }
                }
            }
        }
    }

    companion object {
        private const val GATT_ERROR_133 = 133
        private const val RETRY_DELAY_MS = 3_000L
    }
}
