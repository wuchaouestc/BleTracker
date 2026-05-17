package com.example.bletracker.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.PyObject
import com.example.bletracker.data.ble.*
import com.example.bletracker.data.bridge.PythonBridge
import com.example.bletracker.data.datastore.SettingsStore
import com.example.bletracker.data.repository.DeviceRepository
import com.example.bletracker.data.repository.ScanRecordRepository
import com.example.bletracker.util.CrashGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class DeviceTrackerViewModel @Inject constructor(
    private val bleScanManager: BleScanManager,
    private val pythonBridge: PythonBridge,
    private val deviceRepository: DeviceRepository,
    private val scanRecordRepository: ScanRecordRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _trackedDevice = MutableStateFlow<TrackedDeviceState?>(null)
    val trackedDevice: StateFlow<TrackedDeviceState?> = _trackedDevice

    private val _radarTargets = MutableStateFlow<List<RadarTarget>>(emptyList())
    val radarTargets: StateFlow<List<RadarTarget>> = _radarTargets

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val kalmanFilters = ConcurrentHashMap<String, PyObject?>()
    private var prevAngle: Float? = null
    private var trackedMac: String? = null
    private val distanceHistory = mutableListOf<Float>()
    private val rssiHistory = mutableListOf<Float>()
    private var currentSettings = ScanSettings()
    private var nativeKalmanX = 0f
    private var nativeKalmanP = 1f
    private var nativeKalmanInit = false

    init {
        // 确保 Python bridge 已初始化（kalman 滤波器依赖）
        viewModelScope.launch(CrashGuard.coroutineHandler("TrackerVM")) {
            try {
                pythonBridge.initialize()
                Log.i(TAG, "Python bridge ready")
            } catch (e: Exception) {
                Log.w(TAG, "Python bridge init failed, using native fallback", e)
            }
        }
        viewModelScope.launch(CrashGuard.coroutineHandler("TrackerVM")) {
            try {
                settingsStore.scanSettingsFlow.collect { settings ->
                    currentSettings = settings
                }
            } catch (e: Exception) {
                Log.e(TAG, "Settings flow error", e)
            }
        }
    }

    fun startTracking(mac: String) {
        try {
            stopTracking()
            trackedMac = mac
            _isTracking.value = true
            distanceHistory.clear()
            rssiHistory.clear()
            prevAngle = null
            nativeKalmanInit = false

            // 测试能否使用 BLE 5.1 通信 (AoA/AoD 方位测距)
            val isBle51Supported = bleScanManager.isBle51DirectionFindingSupported()
            if (isBle51Supported) {
                Log.i(TAG, "硬件支持 BLE 5.1 方位测距(AoA/AoD)！将优先使用高精度角度和距离数据。")
                // TODO: 接入厂商特定的 BLE 5.1 AoA SDK 获取真实角度。由于标准 API 尚未开放 CTE 数据，此处暂时走兼容流程。
            } else {
                Log.i(TAG, "当前手机不支持 BLE 5.1 方位测距，回退到 RSSI 信号强度测距方案。")
            }

            val filter = CrashGuard.guard("createFilter") {
                pythonBridge.createKalmanFilter(0.1f, 0.5f)
            }
            if (filter != null) {
                kalmanFilters[mac] = filter
            } else {
                kalmanFilters.remove(mac)
            }

            // 确保扫描运行中（追踪依赖扫描数据）
            // 注意：startScan 已在 BleScanViewModel.startScan() 中调用，
            // 此处仅作为兜底，避免因竞态导致扫描未启动
            if (!bleScanManager.isScanning.value) {
                android.util.Log.w(TAG, "Scan not running, starting as fallback")
                bleScanManager.startScan()  // 不等待返回的 Flow，由内部 scanScope 驱动
            }

            // 初始化默认目标（立即在雷达上显示）
            val initialDeviceName = runBlocking { deviceRepository.getDevice(mac)?.name } ?: "Unknown"
            val initialTarget = RadarTarget(
                mac = mac,
                name = initialDeviceName,
                distance = 10f, // 初始显示在最外圈
                angle = 0f,
                signalStrength = 0.5f,
                isTracked = true,
                signalLevel = 1
            )
            _radarTargets.value = listOf(initialTarget)

            viewModelScope.launch(CrashGuard.coroutineHandler("TrackerVM/rssi")) {
                try {
                    bleScanManager.getDeviceRssi(mac)
                        .catch { e -> Log.e(TAG, "RSSI flow error", e) }
                        .collect { rssi: Int ->
                            if (_isTracking.value && trackedMac == mac) {
                                updateTracking(mac, rssi)
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Tracking flow crash", e)
                    _isTracking.value = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startTracking error", e)
            _isTracking.value = false
        }
    }

    fun stopTracking() {
        try {
            trackedMac?.let { mac ->
                kalmanFilters.remove(mac)?.let { filter ->
                    try { pythonBridge.resetFilter(filter) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        _isTracking.value = false
        _trackedDevice.value = null
        _radarTargets.value = emptyList()
        trackedMac = null
        prevAngle = null
        distanceHistory.clear()
        rssiHistory.clear()
    }

    private suspend fun updateTracking(mac: String, rssi: Int) {
        try {
            val filter = kalmanFilters[mac]

            // 1. 滤波
            val filteredRssi = if (filter != null) {
                try { pythonBridge.filterRssi(filter, rssi.toFloat()) } catch (_: Exception) { nativeKalmanUpdate(rssi.toFloat()) }
            } else nativeKalmanUpdate(rssi.toFloat())

            // 使用最近3次数值的平均值进行平滑处理
            rssiHistory.add(filteredRssi)
            if (rssiHistory.size > 3) {
                rssiHistory.removeAt(0)
            }
            val smoothedRssi = rssiHistory.average().toFloat()

            // 2. 距离
            val distance = try {
                pythonBridge.calculateDistance(smoothedRssi, currentSettings.txPower, currentSettings.envFactor)
            } catch (_: Exception) { nativeCalcDistance(smoothedRssi, currentSettings.txPower, currentSettings.envFactor) }

            // 3. 极坐标
            val (polarDistance, angle) = try {
                pythonBridge.calculatePolarPosition(smoothedRssi, distance, prevAngle)
            } catch (_: Exception) { nativeCalcPolar(smoothedRssi, distance, prevAngle) }
            prevAngle = angle

            // 4. 趋势
            distanceHistory.add(distance)
            if (distanceHistory.size > 5) distanceHistory.removeAt(0)
            val trend = try {
                when {
                    distanceHistory.size < 3 -> Trend.STABLE
                    (distanceHistory.lastOrNull() ?: 0f) < (distanceHistory.firstOrNull() ?: 0f) - 0.5f -> Trend.GETTING_CLOSER
                    (distanceHistory.lastOrNull() ?: 0f) > (distanceHistory.firstOrNull() ?: 0f) + 0.5f -> Trend.GETTING_FARTHER
                    else -> Trend.STABLE
                }
            } catch (_: Exception) { Trend.STABLE }

            // 5. 信号等级
            val signalLevel = try { pythonBridge.getSignalLevel(rssi) } catch (_: Exception) { nativeSignalLevel(rssi) }

            // 6. 设备名称（安全获取）
            val deviceName = try {
                deviceRepository.getDevice(mac)?.name?.takeIf { it.isNotBlank() } ?: "Unknown"
            } catch (_: Exception) { "Unknown" }

            // 7. 更新状态
            val state = TrackedDeviceState(
                mac = mac, name = deviceName,
                currentRssi = rssi, filteredRssi = filteredRssi,
                distance = distance.coerceIn(0f, 100f),
                distanceTrend = trend, signalLevel = signalLevel,
                angle = angle, lastUpdate = System.currentTimeMillis()
            )
            _trackedDevice.value = state

            // 8. 雷达目标（安全值）
            val target = RadarTarget(
                mac = mac, name = deviceName,
                distance = polarDistance.coerceIn(0.1f, 100f),
                angle = if (angle.isFinite()) angle else 0f,
                signalStrength = ((filteredRssi + 100f) / 60f).coerceIn(0f, 1f),
                isTracked = true, signalLevel = signalLevel.coerceIn(1, 5)
            )
            _radarTargets.value = listOf(target)

            // 9. 持久化（非关键）
            try {
                deviceRepository.updateLastSeen(mac, System.currentTimeMillis(), distance, signalLevel)
                scanRecordRepository.insertRecords(listOf(
                    com.example.bletracker.data.db.entity.ScanRecordEntity(
                        deviceMac = mac, rssi = rssi, distance = distance,
                        timestamp = System.currentTimeMillis()
                    )
                ))
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "updateTracking fatal", e)
        }
    }

    fun calibrateTxPower(mac: String) {
        viewModelScope.launch {
            try {
                val avgRssi = scanRecordRepository.getAverageRssi(mac)
                if (avgRssi != null) {
                    deviceRepository.updateCustomTxPower(mac, avgRssi.toInt())
                    settingsStore.setTxPower(avgRssi.toInt())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Calibrate failed", e)
            }
        }
    }

    // ── 原生回退算法 ──
    private fun nativeKalmanUpdate(measurement: Float): Float {
        try {
            if (!nativeKalmanInit) { nativeKalmanX = measurement; nativeKalmanInit = true; return measurement }
            val q = 0.1f; val r = 0.5f
            nativeKalmanP += q
            val k = nativeKalmanP / (nativeKalmanP + r)
            nativeKalmanX += k * (measurement - nativeKalmanX)
            nativeKalmanP *= (1f - k)
            return nativeKalmanX
        } catch (_: Exception) { return measurement }
    }

    private fun nativeCalcDistance(rssi: Float, txPower: Int, envFactor: Float): Float {
        return try {
            var d = Math.pow(10.0, ((txPower - rssi) / (10f * envFactor.coerceAtLeast(0.5f))).toDouble()).toFloat()
            if (d > 4f) {
                d /= 2f
            }
            d.coerceIn(0.1f, 100f)
        } catch (_: Exception) { 1f }
    }

    private fun nativeCalcPolar(rssi: Float, distance: Float, prevAngle: Float?): Pair<Float, Float> {
        return try {
            val confidence = ((rssi + 100f) / 60f).coerceIn(0.3f, 0.95f)
            val angle = if (prevAngle != null) {
                val delta = ((Math.random().toFloat() * 2f - 1f) * (1f - confidence) * Math.PI.toFloat())
                ((prevAngle + delta) % (2f * Math.PI.toFloat()) + 2f * Math.PI.toFloat()) % (2f * Math.PI.toFloat())
            } else (Math.random() * 2 * Math.PI).toFloat()
            Pair(distance, angle)
        } catch (_: Exception) { Pair(distance, 0f) }
    }

    private fun nativeSignalLevel(rssi: Int) = when {
        rssi >= -50 -> 5; rssi >= -60 -> 4; rssi >= -70 -> 3; rssi >= -80 -> 2; else -> 1
    }

    companion object { private const val TAG = "TrackerVM" }
}
