package com.example.bletracker.data.db.dao

import androidx.room.*
import com.example.bletracker.data.db.entity.BleDeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 蓝牙设备数据访问对象
 */
@Dao
interface BleDeviceDao {

    /** 获取所有设备（列表流） */
    @Query("SELECT * FROM ble_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<BleDeviceEntity>>

    /** 获取收藏的设备 */
    @Query("SELECT * FROM ble_devices WHERE isFavorite = 1 ORDER BY lastSeen DESC")
    fun getFavoriteDevices(): Flow<List<BleDeviceEntity>>

    /** 根据MAC查找设备 */
    @Query("SELECT * FROM ble_devices WHERE mac = :mac")
    suspend fun getDeviceByMac(mac: String): BleDeviceEntity?

    /** 插入或更新设备 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: BleDeviceEntity)

    /** 批量插入或更新设备 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevices(devices: List<BleDeviceEntity>)

    /** 切换收藏状态 */
    @Query("UPDATE ble_devices SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE mac = :mac")
    suspend fun toggleFavorite(mac: String)

    /** 更新设备最后扫描时间 */
    @Query("UPDATE ble_devices SET lastSeen = :timestamp, estimatedDistance = :distance, signalLevel = :level WHERE mac = :mac")
    suspend fun updateLastSeen(mac: String, timestamp: Long, distance: Float, level: Int)

    /** 更新自定义TxPower */
    @Query("UPDATE ble_devices SET customTxPower = :txPower WHERE mac = :mac")
    suspend fun updateCustomTxPower(mac: String, txPower: Int)

    /** 删除设备 */
    @Delete
    suspend fun deleteDevice(device: BleDeviceEntity)

    /** 获取设备数量 */
    @Query("SELECT COUNT(*) FROM ble_devices")
    suspend fun getDeviceCount(): Int

    /** 清除所有设备 */
    @Query("DELETE FROM ble_devices")
    suspend fun clearAll()
}
