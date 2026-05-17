package com.example.bletracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bletracker.data.db.dao.BleDeviceDao
import com.example.bletracker.data.db.dao.ScanRecordDao
import com.example.bletracker.data.db.entity.BleDeviceEntity
import com.example.bletracker.data.db.entity.ScanRecordEntity

/**
 * 应用数据库
 *
 * 使用 Room 持久化库管理蓝牙设备和扫描记录数据。
 * 数据库文件：ble_tracker_db
 */
@Database(
    entities = [BleDeviceEntity::class, ScanRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bleDeviceDao(): BleDeviceDao
    abstract fun scanRecordDao(): ScanRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ble_tracker_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
