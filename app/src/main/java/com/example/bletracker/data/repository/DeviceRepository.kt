package com.example.bletracker.data.repository

import android.util.Log
import com.example.bletracker.data.db.AppDatabase
import com.example.bletracker.data.db.entity.BleDeviceEntity
import com.example.bletracker.data.ble.BleDevice
import com.example.bletracker.data.ble.BleDeviceUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val database: AppDatabase
) {
    private val dao by lazy {
        try { database.bleDeviceDao() } catch (e: Exception) {
            Log.e("DeviceRepo", "DAO creation failed", e)
            throw e
        }
    }

    val allDevices: Flow<List<BleDeviceUiState>> = try {
        dao.getAllDevices().map { entities ->
            entities.mapNotNull { entity ->
                try { entity.toUiState() } catch (e: Exception) { null }
            }
        }
    } catch (e: Exception) {
        Log.e("DeviceRepo", "allDevices flow failed", e)
        kotlinx.coroutines.flow.emptyFlow()
    }

    val favoriteDevices: Flow<List<BleDeviceUiState>> = try {
        dao.getFavoriteDevices().map { entities ->
            entities.mapNotNull { entity ->
                try { entity.toUiState() } catch (e: Exception) { null }
            }
        }
    } catch (e: Exception) {
        Log.e("DeviceRepo", "favoriteDevices flow failed", e)
        kotlinx.coroutines.flow.emptyFlow()
    }

    suspend fun saveScanResultsPreservingFavorites(devices: List<BleDevice>) {
        try {
            val entities = devices.mapNotNull { device ->
                try {
                    val existing = dao.getDeviceByMac(device.mac)
                    if (existing != null) {
                        val updatedName = if (device.name != "Unknown" && device.name.isNotBlank()) {
                            device.name
                        } else if (existing.name == "Unknown" && device.manufacturer.isNotBlank() && device.manufacturer != "Unknown Device") {
                            device.manufacturer
                        } else {
                            existing.name
                        }

                        existing.copy(
                            name = updatedName,
                            lastSeen = device.lastSeen,
                            manufacturer = device.manufacturer.ifBlank { existing.manufacturer },
                            signalLevel = BleDevice.getSignalLevel(device.rssi)
                        )
                    } else device.toEntity()
                } catch (e: Exception) { device.toEntity() }
            }
            if (entities.isNotEmpty()) dao.upsertDevices(entities)
        } catch (e: Exception) {
            Log.e("DeviceRepo", "saveScanResults error", e)
        }
    }

    suspend fun getDevice(mac: String): BleDeviceEntity? {
        return try { dao.getDeviceByMac(mac) } catch (e: Exception) {
            Log.e("DeviceRepo", "getDevice error", e); null
        }
    }

    suspend fun toggleFavorite(mac: String) {
        try { dao.toggleFavorite(mac) } catch (e: Exception) {
            Log.e("DeviceRepo", "toggleFavorite error", e)
        }
    }

    suspend fun updateLastSeen(mac: String, timestamp: Long, distance: Float, level: Int) {
        try { dao.updateLastSeen(mac, timestamp, distance, level) } catch (e: Exception) {
            Log.e("DeviceRepo", "updateLastSeen error", e)
        }
    }

    suspend fun updateCustomTxPower(mac: String, txPower: Int) {
        try { dao.updateCustomTxPower(mac, txPower) } catch (e: Exception) {
            Log.e("DeviceRepo", "updateCustomTxPower error", e)
        }
    }

    suspend fun getDeviceCount(): Int {
        return try { dao.getDeviceCount() } catch (e: Exception) { 0 }
    }

    suspend fun clearAll() {
        try { dao.clearAll() } catch (e: Exception) {
            Log.e("DeviceRepo", "clearAll error", e)
        }
    }
}

fun BleDevice.toEntity(): BleDeviceEntity {
    val finalName = if (name == "Unknown" || name.isBlank()) {
        if (manufacturer.isNotBlank() && manufacturer != "Unknown Device") manufacturer else "Unknown"
    } else name

    return BleDeviceEntity(
        mac = mac, name = finalName, lastSeen = lastSeen, manufacturer = manufacturer,
        signalLevel = BleDevice.getSignalLevel(rssi), estimatedDistance = 0f
    )
}

fun BleDeviceEntity.toUiState(): BleDeviceUiState {
    val timeSince = System.currentTimeMillis() - lastSeen
    val lastSeenStr = when {
        timeSince < 1000 -> "刚刚"
        timeSince < 60_000 -> "${timeSince / 1000}秒前"
        timeSince < 3600_000 -> "${timeSince / 60_000}分钟前"
        timeSince < 86400_000 -> "${timeSince / 3600_000}小时前"
        else -> "${timeSince / 86400_000}天前"
    }
    return BleDeviceUiState(
        mac = mac, name = name, rssi = 0, distance = estimatedDistance,
        lastSeen = lastSeenStr, isFavorite = isFavorite, signalLevel = signalLevel
    )
}
