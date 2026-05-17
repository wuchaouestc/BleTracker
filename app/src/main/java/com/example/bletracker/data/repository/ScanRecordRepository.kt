package com.example.bletracker.data.repository

import android.util.Log
import com.example.bletracker.data.db.AppDatabase
import com.example.bletracker.data.db.entity.ScanRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRecordRepository @Inject constructor(
    private val database: AppDatabase
) {
    private val dao by lazy { database.scanRecordDao() }

    suspend fun insertRecords(records: List<ScanRecordEntity>) {
        try { dao.insertRecords(records) } catch (e: Exception) {
            Log.e("ScanRepo", "insertRecords error", e)
        }
    }

    suspend fun getRecentRecords(mac: String, limit: Int = 100): List<ScanRecordEntity> {
        return try { dao.getRecentRecords(mac, limit) } catch (e: Exception) {
            Log.e("ScanRepo", "getRecentRecords error", e); emptyList()
        }
    }

    suspend fun cleanupOldRecords() {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 3600_000L
            dao.deleteOldRecords(sevenDaysAgo)
        } catch (e: Exception) {
            Log.e("ScanRepo", "cleanup error", e)
        }
    }

    suspend fun getAverageRssi(mac: String): Float? {
        return try {
            val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60_000L
            dao.getAverageRssi(mac, fiveMinutesAgo)
        } catch (e: Exception) {
            Log.e("ScanRepo", "getAvgRssi error", e); null
        }
    }
}
