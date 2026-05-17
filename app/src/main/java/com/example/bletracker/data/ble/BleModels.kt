package com.example.bletracker.data.ble

/**
 * 蓝牙设备域模型
 *
 * 表示扫描到的蓝牙设备，包含信号强度、距离计算等实时信息
 */
data class BleDevice(
    val name: String,
    val mac: String,
    val rssi: Int,
    val txPower: Int = -59,
    val manufacturer: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
    val isBonded: Boolean = false
) {
    companion object {
        /** 根据RSSI获取信号等级 (1-5) */
        fun getSignalLevel(rssi: Int): Int = when {
            rssi >= -50 -> 5
            rssi >= -60 -> 4
            rssi >= -70 -> 3
            rssi >= -80 -> 2
            else -> 1
        }

        /** 信号等级对应的颜色名称 */
        fun getSignalColorName(level: Int): String = when (level) {
            5 -> "signal_strong"
            4 -> "signal_medium"
            3 -> "signal_medium"
            else -> "signal_weak"
        }
    }
}

/**
 * 雷达目标数据模型
 *
 * 用于在雷达图中显示追踪目标的极坐标位置
 */
data class RadarTarget(
    val mac: String,
    val name: String,
    val distance: Float,       // 距离（米）
    val angle: Float,          // 角度（弧度）
    val signalStrength: Float, // 信号强度 0.0-1.0
    val isTracked: Boolean = false,
    val signalLevel: Int = 1
)

/**
 * 追踪设备状态
 *
 * 实时追踪的完整状态信息
 */
data class TrackedDeviceState(
    val mac: String,
    val name: String,
    val currentRssi: Int,
    val filteredRssi: Float,
    val distance: Float,
    val distanceTrend: Trend = Trend.STABLE,
    val lastUpdate: Long = System.currentTimeMillis(),
    val signalLevel: Int = 1,
    val angle: Float = 0f
)

/** 距离变化趋势 */
enum class Trend {
    GETTING_CLOSER,
    GETTING_FARTHER,
    STABLE
}

/**
 * 蓝牙设备 UI 展示模型
 */
data class BleDeviceUiState(
    val mac: String,
    val name: String,
    val rssi: Int,
    val distance: Float,
    val lastSeen: String,
    val isFavorite: Boolean,
    val signalLevel: Int,
    val isTracked: Boolean = false
)

/**
 * 扫描设置
 */
data class ScanSettings(
    val scanInterval: Long = 1000L,     // 扫描间隔（毫秒）
    val envFactor: Float = 2.5f,        // 环境衰减因子
    val txPower: Int = -59,             // 1米参考RSSI
    val scanDuration: Long = 10000L     // 单次扫描时长
)
