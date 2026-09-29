package dev.jeonsi.blects.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * CTS(0x1805) 서비스 솔리시테이션을 광고하는 기기만 찾는다.
 * 호출 전에 BLUETOOTH_SCAN(31+) 또는 ACCESS_FINE_LOCATION(≤30) 권한이 있어야 한다.
 */
@SuppressLint("MissingPermission")
class CtsScanner(private val context: Context) {

    data class Hit(val address: String, val name: String?, val rssi: Int, val seenAt: Long)

    class ScanException(val code: Int) : Exception("scan failed: $code")

    fun scan(): Flow<Hit> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close(ScanException(-1))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toHit())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toHit()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanException(errorCode))
            }
        }
        val filter = ScanFilter.Builder()
            .setServiceSolicitationUuid(ParcelUuid(Uuids.CTS_SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    private fun ScanResult.toHit() = Hit(
        address = device.address,
        name = scanRecord?.deviceName ?: runCatching { device.name }.getOrNull(),
        rssi = rssi,
        seenAt = System.currentTimeMillis(),
    )
}
