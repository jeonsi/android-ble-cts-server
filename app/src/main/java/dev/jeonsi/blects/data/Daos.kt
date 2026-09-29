package dev.jeonsi.blects.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY addedAt")
    fun all(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE address = :address")
    fun byAddress(address: String): Flow<Device?>

    @Query("SELECT * FROM devices WHERE address = :address")
    suspend fun get(address: String): Device?

    @Query("SELECT * FROM devices ORDER BY addedAt")
    suspend fun snapshot(): List<Device>

    @Insert
    suspend fun insert(device: Device)

    @Query("UPDATE devices SET name = :name WHERE address = :address")
    suspend fun updateName(address: String, name: String)

    @Query("UPDATE devices SET lastSyncAt = :at WHERE address = :address")
    suspend fun markSynced(address: String, at: Long)

    @Query("DELETE FROM devices WHERE address = :address")
    suspend fun delete(address: String)

    @Query("SELECT MAX(lastSyncAt) FROM devices")
    fun lastSyncAny(): Flow<Long?>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event)

    /** 기기 이벤트 + 전역 이벤트(서비스 생존 여부를 같이 보기 위해) */
    @Query("SELECT * FROM events WHERE address = :address OR address IS NULL ORDER BY at DESC, id DESC LIMIT 1000")
    fun forDevice(address: String): Flow<List<Event>>

    @Query("SELECT * FROM events ORDER BY at ASC, id ASC")
    suspend fun allForExport(): List<Event>

    @Query("DELETE FROM events WHERE at < :before")
    suspend fun prune(before: Long)
}
