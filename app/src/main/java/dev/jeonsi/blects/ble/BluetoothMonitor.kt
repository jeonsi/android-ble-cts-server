package dev.jeonsi.blects.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 블루투스 어댑터 on/off 를 StateFlow 로 노출. Application 수명. */
class BluetoothMonitor(context: Context) {
    val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _isOn = MutableStateFlow(adapter?.isEnabled == true)
    val isOn: StateFlow<Boolean> = _isOn

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> _isOn.value = true
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> _isOn.value = false
                }
            }
        }
        // 시스템 보호 브로드캐스트라 exported 여도 다른 앱이 위조할 수 없다.
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
