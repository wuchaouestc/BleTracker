package com.example.bletracker.di

import android.content.Context
import com.example.bletracker.data.ble.AndroidBleScanManager
import com.example.bletracker.data.ble.BleScanManager
import com.example.bletracker.data.bridge.ChaquopyPythonBridge
import com.example.bletracker.data.bridge.PythonBridge
import com.example.bletracker.data.db.AppDatabase
import com.example.bletracker.data.db.dao.BleDeviceDao
import com.example.bletracker.data.db.dao.ScanRecordDao
import com.example.bletracker.data.datastore.SettingsStore
import com.example.bletracker.data.repository.DeviceRepository
import com.example.bletracker.data.repository.ScanRecordRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 依赖注入模块
 *
 * 使用 Hilt 管理应用级依赖，确保所有组件可独立测试和替换
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── 蓝牙扫描 ──

    @Provides
    @Singleton
    fun provideBleScanManager(@ApplicationContext context: Context): BleScanManager {
        return AndroidBleScanManager(context)
    }

    // ── Python 桥接 ──

    @Provides
    @Singleton
    fun providePythonBridge(): PythonBridge {
        val bridge = ChaquopyPythonBridge()
        bridge.initialize()
        return bridge
    }

    // ── 数据库 ──

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideBleDeviceDao(db: AppDatabase): BleDeviceDao {
        return db.bleDeviceDao()
    }

    @Provides
    fun provideScanRecordDao(db: AppDatabase): ScanRecordDao {
        return db.scanRecordDao()
    }

    // ── 配置存储 ──

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore {
        return SettingsStore(context)
    }

    // ── 数据仓库 ──

    @Provides
    @Singleton
    fun provideDeviceRepository(db: AppDatabase): DeviceRepository {
        return DeviceRepository(db)
    }

    @Provides
    @Singleton
    fun provideScanRecordRepository(db: AppDatabase): ScanRecordRepository {
        return ScanRecordRepository(db)
    }
}
