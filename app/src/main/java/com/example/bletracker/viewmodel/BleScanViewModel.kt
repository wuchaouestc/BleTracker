package com.example.bletracker.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletracker.data.ble.BleScanManager
import com.example.bletracker.data.ble.BleDeviceUiState
import com.example.bletracker.data.repository.DeviceRepository
import com.example.bletracker.util.CrashGuard
import com.example.bletracker.data.bridge.PythonBridge
import com.example.bletracker.data.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BleScanViewModel @Inject constructor(
    private val bleScanManager: BleScanManager,
    private val deviceRepository: DeviceRepository,
    private val pythonBridge: PythonBridge,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _deviceList = MutableStateFlow<List<BleDeviceUiState>>(emptyList())
    val deviceList: StateFlow<List<BleDeviceUiState>> = _deviceList

    val isScanning: StateFlow<Boolean> = bleScanManager.isScanning
    val isBluetoothEnabled: StateFlow<Boolean> = bleScanManager.isBluetoothEnabled
    val deviceCount: StateFlow<Int> = bleScanManager.deviceCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val error: SharedFlow<String> = _error

    private val favoriteMacs = MutableStateFlow<Set<String>>(emptySet())
    private var currentTxPower = -59
    private var currentEnvFactor = 2.5f

    init {
        viewModelScope.launch(CrashGuard.coroutineHandler("BleScanVM/initPython")) {
            try {
                pythonBridge.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Python init failed in BleScanVM", e)
            }
        }
        
        viewModelScope.launch(CrashGuard.coroutineHandler("BleScanVM/settings")) {
            try {
                settingsStore.scanSettingsFlow.collect { settings ->
                    currentTxPower = settings.txPower
                    currentEnvFactor = settings.envFactor
                }
            } catch (e: Exception) {
                Log.e(TAG, "Settings flow error", e)
            }
        }

        viewModelScope.launch(CrashGuard.coroutineHandler("BleScanVM")) {
            try {
                deviceRepository.allDevices
                    .catch { e -> Log.e(TAG, "DB flow error", e) }
                    .collect { dbDevices ->
                        val favs = dbDevices.filter { it.isFavorite }.map { it.mac }.toSet()
                        favoriteMacs.value = favs
                        if (!isScanning.value) {
                            val scanned = _deviceList.value.associateBy { it.mac }
                            val merged = dbDevices.map { db ->
                                scanned[db.mac]?.copy(isFavorite = db.isFavorite) ?: db
                            }
                            _deviceList.value = sortDevices(merged)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Init error", e)
                _error.emit("数据加载失败: ${e.message}")
            }
        }
    }

    fun startScan(scanInterval: Long = 1000L) {
        if (!bleScanManager.isBluetoothEnabled.value) {
            viewModelScope.launch { _error.emit("请先开启蓝牙") }
            return
        }
        viewModelScope.launch(CrashGuard.coroutineHandler("BleScanVM/scan")) {
            _isLoading.value = true
            try {
                bleScanManager.startScan(scanInterval)
                    .catch { e ->
                        Log.e(TAG, "Scan flow error", e)
                        _error.emit("扫描异常: ${e.message}")
                    }
                    .collect { scannedDevices ->
                        val favs = favoriteMacs.value
                        val newStates = scannedDevices.map { device ->
                            val dist = try {
                                pythonBridge.calculateDistance(device.rssi.toFloat(), currentTxPower, currentEnvFactor)
                            } catch (_: Exception) {
                                0f
                            }
                            BleDeviceUiState(
                                mac = device.mac,
                                name = device.name,
                                rssi = device.rssi,
                                distance = dist,
                                lastSeen = "刚刚",
                                isFavorite = device.mac in favs,
                                signalLevel = signalLevelOf(device.rssi)
                            )
                        }
                        _deviceList.value = mergeAndSort(newStates)
                        try { deviceRepository.saveScanResultsPreservingFavorites(scannedDevices) } catch (_: Exception) {}
                    }
            } catch (e: Exception) {
                _error.emit("扫描出错: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun stopScan() {
        try { bleScanManager.stopScan() } catch (e: Exception) {
            Log.e(TAG, "Stop scan error", e)
        }
    }

    fun toggleFavorite(mac: String) {
        viewModelScope.launch {
            try {
                deviceRepository.toggleFavorite(mac)
                val current = favoriteMacs.value.toMutableSet()
                if (mac in current) current.remove(mac) else current.add(mac)
                favoriteMacs.value = current
                _deviceList.value = sortDevices(
                    _deviceList.value.map { device ->
                        if (device.mac == mac) device.copy(isFavorite = mac in current) else device
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Toggle favorite error", e)
                _error.emit("收藏操作失败")
            }
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            try {
                val dbDevices = deviceRepository.allDevices.first()
                val favs = dbDevices.filter { it.isFavorite }.map { it.mac }.toSet()
                favoriteMacs.value = favs
                _deviceList.value = sortDevices(dbDevices)
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
            }
        }
    }

    private fun mergeAndSort(scanned: List<BleDeviceUiState>): List<BleDeviceUiState> {
        return try {
            val existing = _deviceList.value.associateBy { it.mac }
            val merged = scanned.map { fresh ->
                existing[fresh.mac]?.let { old ->
                    fresh.copy(isFavorite = old.isFavorite || fresh.isFavorite)
                } ?: fresh
            }
            val scannedMacs = scanned.map { it.mac }.toSet()
            val historical = existing.values.filter { it.mac !in scannedMacs }
            sortDevices(merged + historical)
        } catch (e: Exception) {
            scanned // fallback: raw list
        }
    }

    private fun sortDevices(devices: List<BleDeviceUiState>): List<BleDeviceUiState> {
        return try {
            devices.sortedWith(
                compareByDescending<BleDeviceUiState> { it.isFavorite }
                    .thenByDescending { it.signalLevel }
                    .thenByDescending { it.rssi }
                    .thenBy { it.name }
            )
        } catch (e: Exception) { devices }
    }

    private fun signalLevelOf(rssi: Int) = when {
        rssi >= -50 -> 5; rssi >= -60 -> 4; rssi >= -70 -> 3; rssi >= -80 -> 2; else -> 1
    }

    companion object { private const val TAG = "BleScanVM" }
}
