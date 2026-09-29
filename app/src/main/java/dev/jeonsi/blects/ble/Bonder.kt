package dev.jeonsi.blects.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 기기 추가 시 폰이 먼저 명시적으로 본딩한다(`createBond`).
 *
 * 연결만 걸어 두고 기기(ESP32)의 Security Request에 본딩을 맡기면 안드로이드가 키를 끝까지
 * 저장하지 않는 경우가 실기기에서 확인됐다 — 다음 재동기화에 페어링 대화상자가 다시 뜨고
 * 실패한다. 시스템 블루투스 설정에서 먼저 페어링하면 되는데, `createBond`가 그와 같은 경로다.
 */
@SuppressLint("MissingPermission")
object Bonder {

    sealed interface Result {
        data object Bonded : Result
        /** @param reason 안드로이드의 UNBOND_REASON_* (숨은 extra). 1 인증 실패, 3 취소, 4 상대 끊김, 6 시간 초과, 8 상대가 취소 */
        data class Failed(val reason: Int) : Result
        data object Timeout : Result
        data object Unavailable : Result
    }

    private data class BondEvent(val state: Int, val reason: Int)

    /** 이미 본딩돼 있으면 곧바로 [Result.Bonded]. 아니면 본딩을 시작하고 끝날 때까지 기다린다. */
    suspend fun ensureBonded(context: Context, address: String, timeoutMs: Long = TIMEOUT_MS): Result {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return Result.Unavailable
        if (!adapter.isEnabled) return Result.Unavailable
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            return Result.Unavailable
        }
        if (device.bondState == BluetoothDevice.BOND_BONDED) return Result.Bonded

        val terminal = withTimeoutOrNull(timeoutMs) {
            bondStates(context, device).first { it.state == BluetoothDevice.BOND_BONDED || it.state == BluetoothDevice.BOND_NONE }
        }
        return when (terminal?.state) {
            BluetoothDevice.BOND_BONDED -> Result.Bonded
            null -> Result.Timeout
            else -> Result.Failed(terminal.reason)
        }
    }

    /** 본딩을 시작하고 이 기기의 본딩 상태 변화를 흘려보낸다. createBond 가 거부되면 BOND_NONE. */
    private fun bondStates(context: Context, device: BluetoothDevice) = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (d?.address != device.address) return
                trySend(
                    BondEvent(
                        state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE),
                        reason = intent.getIntExtra(EXTRA_REASON, -1),
                    ),
                )
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        // 이미 진행 중이면(시스템 설정에서 동시에 눌렀다든지) 그 결과를 기다린다
        if (device.bondState != BluetoothDevice.BOND_BONDING && !device.createBond()) {
            trySend(BondEvent(BluetoothDevice.BOND_NONE, REASON_CREATE_BOND_REFUSED))
        }
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private const val TIMEOUT_MS = 90_000L

    /** BluetoothDevice.EXTRA_REASON (@hide). 본딩이 풀린 사유 UNBOND_REASON_* */
    const val EXTRA_REASON = "android.bluetooth.device.extra.REASON"
    const val REASON_CREATE_BOND_REFUSED = -2
}
