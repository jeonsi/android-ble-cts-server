package dev.jeonsi.blects.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import java.time.ZonedDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 폰을 CTS 시간 서버로 만드는 GATT 서버.
 *
 * - CTS (0x1805): Current Time (0x2A2B, read/notify + CCCD), Local Time Information (0x2A0F, read)
 * - ANCS 스텁: 아이폰용 액세서리가 구독을 시도하면 성공만 응답한다. 알림은 보내지 않는다.
 *
 * 모든 읽기·쓰기는 아이폰과 똑같이 **암호화된(페어링된) 연결에서만** 허용한다. 암호화 없이도
 * 읽게 두면 기기(ESP32)가 페어링이 끝나기 전에 시간을 읽고 ANCS까지 구독한 뒤 2초 만에 라디오를
 * 꺼 버려, 폰의 페어링 대화상자가 링크 끊김으로 죽는다(실기기 확인, 2026-09-29).
 *
 * 콜백은 바인더 스레드에서 온다. Listener 구현은 스레드를 신경 써야 한다.
 */
@SuppressLint("MissingPermission")
class CtsGattServer(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected(device: BluetoothDevice, status: Int)
        fun onCurrentTimeRead(device: BluetoothDevice, sent: ZonedDateTime)
        fun onLocalTimeRead(device: BluetoothDevice)
        fun onSubscriptionChanged(device: BluetoothDevice, characteristic: UUID, enabled: Boolean)
        fun onServiceAddFailed(service: UUID)
    }

    private class Sub(val device: BluetoothDevice) {
        val chars: MutableSet<UUID> = Collections.newSetFromMap(ConcurrentHashMap())
    }

    private var server: BluetoothGattServer? = null
    private val pending = ArrayDeque<BluetoothGattService>()
    private val subs = ConcurrentHashMap<String, Sub>()
    private lateinit var currentTime: BluetoothGattCharacteristic

    /** 마지막 시간 변경 사유. Current Time 의 Adjust Reason 필드로 나간다. */
    @Volatile
    var adjustReason: Int = 0
        private set

    val isOpen: Boolean get() = server != null

    fun open(): Boolean {
        if (server != null) return true
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        val s = runCatching { manager.openGattServer(context, callback) }.getOrNull() ?: return false
        server = s
        pending.clear()
        pending += buildCts()
        pending += buildAncs()
        addNext()
        return true
    }

    fun close() {
        server?.let { runCatching { it.close() } }
        server = null
        subs.clear()
        pending.clear()
    }

    /** 폰 시간·시간대가 바뀌었을 때 구독자에게 알린다. */
    fun notifyTimeChanged(reason: Int) {
        adjustReason = reason
        val s = server ?: return
        val value = TimeCodec.currentTime(ZonedDateTime.now(), reason)
        for (sub in subs.values) {
            if (Uuids.CURRENT_TIME !in sub.chars) continue
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    s.notifyCharacteristicChanged(sub.device, currentTime, false, value)
                } else {
                    @Suppress("DEPRECATION")
                    currentTime.value = value
                    @Suppress("DEPRECATION")
                    s.notifyCharacteristicChanged(sub.device, currentTime, false)
                }
            }
        }
    }

    // 서비스는 하나씩 순서대로 추가해야 한다(onServiceAdded 뒤에 다음 것).
    private fun addNext() {
        val next = pending.removeFirstOrNull() ?: return
        if (server?.addService(next) != true) listener.onServiceAddFailed(next.uuid)
    }

    private fun buildCts(): BluetoothGattService {
        val svc = BluetoothGattService(Uuids.CTS_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        currentTime = BluetoothGattCharacteristic(
            Uuids.CURRENT_TIME,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        ).apply { addDescriptor(cccd()) }
        svc.addCharacteristic(currentTime)
        svc.addCharacteristic(
            BluetoothGattCharacteristic(
                Uuids.LOCAL_TIME_INFO,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
            )
        )
        return svc
    }

    private fun buildAncs(): BluetoothGattService {
        val svc = BluetoothGattService(Uuids.ANCS_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        svc.addCharacteristic(notifyOnly(Uuids.ANCS_NOTIFICATION_SOURCE))
        svc.addCharacteristic(
            BluetoothGattCharacteristic(
                Uuids.ANCS_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
            )
        )
        svc.addCharacteristic(notifyOnly(Uuids.ANCS_DATA_SOURCE))
        return svc
    }

    private fun notifyOnly(uuid: UUID) = BluetoothGattCharacteristic(
        uuid, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0,
    ).apply { addDescriptor(cccd()) }

    private fun cccd() = BluetoothGattDescriptor(
        Uuids.CCCD,
        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
    )

    private fun isSubscribed(device: BluetoothDevice, uuid: UUID) =
        subs[device.address]?.chars?.contains(uuid) == true

    private fun respond(device: BluetoothDevice, requestId: Int, offset: Int, value: ByteArray?, failure: Int) {
        val s = server ?: return
        when {
            value == null -> s.sendResponse(device, requestId, failure, 0, null)
            offset > value.size -> s.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, 0, null)
            else -> s.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) listener.onServiceAddFailed(service.uuid)
            addNext()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> listener.onConnected(device)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    subs.remove(device.address)
                    listener.onDisconnected(device, status)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic,
        ) {
            val value: ByteArray? = when (characteristic.uuid) {
                Uuids.CURRENT_TIME -> {
                    val now = ZonedDateTime.now()
                    listener.onCurrentTimeRead(device, now)
                    TimeCodec.currentTime(now, adjustReason)
                }
                Uuids.LOCAL_TIME_INFO -> {
                    listener.onLocalTimeRead(device)
                    TimeCodec.localTimeInformation(ZonedDateTime.now())
                }
                else -> null
            }
            respond(device, requestId, offset, value, BluetoothGatt.GATT_READ_NOT_PERMITTED)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor,
        ) {
            val value = if (descriptor.uuid == Uuids.CCCD) {
                if (isSubscribed(device, descriptor.characteristic.uuid)) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
            } else {
                null
            }
            respond(device, requestId, offset, value, BluetoothGatt.GATT_READ_NOT_PERMITTED)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            if (descriptor.uuid == Uuids.CCCD) {
                val enable = value != null && value.isNotEmpty() && (value[0].toInt() and 0x03) != 0
                val uuid = descriptor.characteristic.uuid
                val sub = subs.getOrPut(device.address) { Sub(device) }
                if (enable) sub.chars += uuid else sub.chars -= uuid
                listener.onSubscriptionChanged(device, uuid, enable)
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            // ANCS Control Point: 받기만 하고 아무것도 하지 않는다.
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }
}
