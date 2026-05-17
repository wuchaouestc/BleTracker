package com.example.bletracker.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.bletracker.data.ble.ScanSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// 顶层扩展属性，用于 DataStore 单例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ble_tracker_settings")

/**
 * 设置存储管理器
 *
 * 使用 DataStore Preferences 持久化用户设置
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_SCAN_INTERVAL = longPreferencesKey("scan_interval")
        private val KEY_ENV_FACTOR = floatPreferencesKey("env_factor")
        private val KEY_TX_POWER = intPreferencesKey("tx_power")
        private val KEY_SCAN_DURATION = longPreferencesKey("scan_duration")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    /** 获取完整扫描设置流 */
    val scanSettingsFlow: Flow<ScanSettings> = try {
        context.dataStore.data.map { prefs ->
            ScanSettings(
                scanInterval = prefs[KEY_SCAN_INTERVAL] ?: 1000L,
                envFactor = prefs[KEY_ENV_FACTOR] ?: 2.5f,
                txPower = prefs[KEY_TX_POWER] ?: -59,
                scanDuration = prefs[KEY_SCAN_DURATION] ?: 10000L
            )
        }.catch { e ->
            android.util.Log.e("SettingsStore", "DataStore read error", e)
            emit(ScanSettings())
        }
    } catch (e: Exception) {
        android.util.Log.e("SettingsStore", "DataStore init error", e)
        kotlinx.coroutines.flow.flowOf(ScanSettings())
    }

    /** 扫描间隔流 */
    val scanIntervalFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SCAN_INTERVAL] ?: 1000L
    }

    /** 环境因子流 */
    val envFactorFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENV_FACTOR] ?: 2.5f
    }

    /** TxPower 流 */
    val txPowerFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_TX_POWER] ?: -59
    }

    /** 是否首次启动 */
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }

    /** 更新扫描间隔 */
    suspend fun setScanInterval(interval: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCAN_INTERVAL] = interval.coerceIn(500L, 10000L)
        }
    }

    /** 更新环境因子 */
    suspend fun setEnvFactor(factor: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENV_FACTOR] = factor.coerceIn(1.0f, 5.0f)
        }
    }

    /** 更新 TxPower */
    suspend fun setTxPower(txPower: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TX_POWER] = txPower.coerceIn(-100, 0)
        }
    }

    /** 更新扫描时长 */
    suspend fun setScanDuration(duration: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCAN_DURATION] = duration.coerceIn(1000L, 60000L)
        }
    }

    /** 标记已完成首次启动 */
    suspend fun completeFirstLaunch() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = false
        }
    }
}
