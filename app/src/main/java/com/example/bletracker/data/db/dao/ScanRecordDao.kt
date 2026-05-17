package com.example.bletracker.data.db.dao

import androidx.room.*
import com.example.bletracker.data.db.entity.ScanRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 扫描记录数据访问对象
 */
@Dao
interface ScanRecordDao {

    /** 获取指定设备的所有扫描记录（按时间倒序） */
    @Query("SELECT * FROM scan_records WHERE deviceMac = :mac ORDER BY timestamp DESC")
    fun getRecordsForDevice(mac: String): Flow<List<ScanRecordEntity>>

    /** 获取指定设备的最近N条扫描记录 */
    @Query("SELECT * FROM scan_records WHERE deviceMac = :mac ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRecords(mac: String, limit: Int = 100): List<ScanRecordEntity>

    /** 获取指定时间范围内的记录 */
    @Query("SELECT * FROM scan_records WHERE deviceMac = :mac AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getRecordsInRange(mac: String, startTime: Long, endTime: Long): List<ScanRecordEntity>

    /** 批量插入扫描记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<ScanRecordEntity>)

    /** 删除指定设备的记录 */
    @Query("DELETE FROM scan_records WHERE deviceMac = :mac")
    suspend fun deleteRecordsForDevice(mac: String)

    /** 删除超过指定时间的旧记录 */
    @Query("DELETE FROM scan_records WHERE timestamp < :beforeTime")
    suspend fun deleteOldRecords(beforeTime: Long)

    /** 获取记录总数 */
    @Query("SELECT COUNT(*) FROM scan_records")
    suspend fun getRecordCount(): Int

    /** 获取指定设备的平均RSSI */
    @Query("SELECT AVG(rssi) FROM scan_records WHERE deviceMac = :mac AND timestamp > :sinceTime")
    suspend fun getAverageRssi(mac: String, sinceTime: Long): Float?
}
