package com.example.bletracker.data.bridge

import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.PyObject

/**
 * Chaquopy Python 桥接实现（稳健版）
 *
 * 通过 Chaquopy 在 Android 进程中运行 Python 代码，
 * 实现 Kotlin 与 Python 算法模块的无缝调用。
 *
 * 关键修复：
 * - 所有回退使用 Kotlin 原生备用算法，Python 异常不会导致 crash
 * - 模块实例缓存，避免重复创建 Python 对象
 * - 参数类型统一使用 Double（与 Python float 兼容）
 */
class ChaquopyPythonBridge : PythonBridge {

    private var isInitialized = false

    // 缓存已创建的 Python 模块实例，避免重复 constructor 调用
    private var distanceCalculator: PyObject? = null
    private var positionCalculator: PyObject? = null

    override fun initialize(): Boolean {
        return try {
            val python = Python.getInstance()
            python.getModule("algorithms.kalman_filter")
            python.getModule("algorithms.distance_calculator")
            python.getModule("algorithms.position_calculator")
            isInitialized = true
            Log.i(TAG, "Python bridge initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Python bridge", e)
            isInitialized = false
            false
        }
    }

    override fun createKalmanFilter(processNoise: Float, measurementNoise: Float): PyObject? {
        if (!isInitialized) {
            Log.w(TAG, "Python not initialized, using fallback")
            return null
        }
        return try {
            val module = Python.getInstance().getModule("algorithms.kalman_filter")
            module.callAttr("KalmanFilter", processNoise.toDouble(), measurementNoise.toDouble())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating KalmanFilter, using fallback", e)
            null // 返回 null，ViewModel 将使用原生回退算法
        }
    }

    override fun filterRssi(filter: PyObject?, rssi: Float): Float {
        if (filter == null) {
            // 无滤波器时使用简单指数平滑回退
            return kalmanFallback(rssi)
        }
        return try {
            filter.callAttr("update", rssi.toDouble()).toDouble().toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering RSSI, using fallback", e)
            kalmanFallback(rssi)
        }
    }

    override fun calculateDistance(rssi: Float, txPower: Int, envFactor: Float): Float {
        // 尝试使用 Python
        if (isInitialized) {
            try {
                if (distanceCalculator == null) {
                    val module = Python.getInstance().getModule("algorithms.distance_calculator")
                    distanceCalculator = module.callAttr("DistanceCalculator", txPower, envFactor.toDouble())
                }
                distanceCalculator?.let {
                    return it.callAttr("calculate", rssi.toDouble()).toDouble().toFloat()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating distance, using fallback", e)
                distanceCalculator = null // 重置缓存，下次重建
            }
        }

        // Kotlin 原生备用算法
        return nativeCalculateDistance(rssi, txPower, envFactor)
    }

    override fun calculatePolarPosition(
        rssi: Float,
        distance: Float,
        prevAngle: Float?
    ): Pair<Float, Float> {
        if (isInitialized) {
            try {
                if (positionCalculator == null) {
                    val module = Python.getInstance().getModule("algorithms.position_calculator")
                    positionCalculator = module.callAttr("PositionCalculator")
                }
                positionCalculator?.let { calc ->
                    val result = if (prevAngle != null) {
                        calc.callAttr("rssi_to_polar", rssi.toDouble(), distance.toDouble(), prevAngle.toDouble())
                    } else {
                        calc.callAttr("rssi_to_polar", rssi.toDouble(), distance.toDouble())
                    }
                    val list = result.asList()
                    val d = list[0].toDouble().toFloat()
                    val a = list[1].toDouble().toFloat()
                    return Pair(d, a)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating polar position, using fallback", e)
                positionCalculator = null
            }
        }

        // Kotlin 原生备用算法
        return nativeCalculatePolarPosition(rssi, distance, prevAngle)
    }

    override fun getSignalLevel(rssi: Int): Int {
        return nativeGetSignalLevel(rssi)
    }

    override fun resetFilter(filter: PyObject?) {
        if (filter == null) return
        try {
            filter.callAttr("reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting filter", e)
        }
    }

    // ── Kotlin 原生回退算法（Python 不可用时的最后防线） ──

    /** 上一次卡尔曼回退值（用于简单指数平滑） */
    private var fallbackKalmanX: Float = 0f
    private var fallbackKalmanInit: Boolean = false

    private fun kalmanFallback(rssi: Float): Float {
        if (!fallbackKalmanInit) {
            fallbackKalmanX = rssi
            fallbackKalmanInit = true
            return rssi
        }
        // 简单指数平滑: X = 0.85 * X + 0.15 * measurement
        fallbackKalmanX = 0.85f * fallbackKalmanX + 0.15f * rssi
        return fallbackKalmanX
    }

    private fun nativeCalculateDistance(rssi: Float, txPower: Int, envFactor: Float): Float {
        val denom = 10.0f * envFactor.coerceAtLeast(0.5f)
        val exponent = (txPower - rssi) / denom
        var distance = Math.pow(10.0, exponent.toDouble()).toFloat()
        if (distance > 4f) {
            distance /= 2f
        }
        return distance.coerceIn(0.1f, 100f)
    }

    private fun nativeCalculatePolarPosition(
        rssi: Float,
        distance: Float,
        prevAngle: Float?
    ): Pair<Float, Float> {
        val confidence = ((rssi + 100f) / 60f).coerceIn(0.3f, 0.95f)
        val angle = if (prevAngle != null) {
            val maxDelta = (1f - confidence) * Math.PI.toFloat()
            val delta = (Math.random().toFloat() * 2f - 1f) * maxDelta
            ((prevAngle + delta) % (2f * Math.PI.toFloat()) + (2f * Math.PI.toFloat())) % (2f * Math.PI.toFloat())
        } else {
            (Math.random() * 2 * Math.PI).toFloat()
        }
        return Pair(distance, angle)
    }

    private fun nativeGetSignalLevel(rssi: Int): Int = when {
        rssi >= -50 -> 5
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }

    companion object {
        private const val TAG = "PythonBridge"
    }
}
