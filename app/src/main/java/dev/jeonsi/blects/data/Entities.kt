package dev.jeonsi.blects.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 앱이 관리하는 기기 목록. 시스템 본딩 목록과 별개다. */
@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val address: String,
    val name: String?,
    val addedAt: Long,
    /** 마지막으로 Current Time(0x2A2B)을 읽어 간 시각(epoch ms). null이면 아직 없음 */
    val lastSyncAt: Long? = null,
) {
    val displayName: String get() = name ?: address
}

enum class EventType {
    SERVICE_STARTED, SERVICE_STOPPED,
    BLUETOOTH_ON, BLUETOOTH_OFF,
    BOOT,
    CONNECTED, DISCONNECTED,
    TIME_READ, LOCAL_TIME_READ,
    SUBSCRIPTION,
    BOND,
    TIME_CHANGED,
    DEVICE_ADDED, DEVICE_REMOVED,
    ERROR,
}

/** 이벤트 로그. address가 null이면 특정 기기와 무관한 전역 이벤트(서비스·블루투스·부팅). */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val address: String?,
    val type: EventType,
    val detail: String? = null,
)
