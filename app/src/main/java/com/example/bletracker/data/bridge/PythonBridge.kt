package com.example.bletracker.data.bridge

import com.chaquo.python.PyObject

/**
 * Python 桥接接口
 *
 * 定义 Kotlin ↔ Python 算法层之间的调用契约。
 * filter 参数可为 null（Python 不可用时的回退模式）。
 */
interface PythonBridge {

    /** 初始化 Python 运行时 */
    fun initialize(): Boolean

    /**
     * 创建卡尔曼滤波器
     * @return 滤波器的 Python 对象引用，失败时返回 null
     */
    fun createKalmanFilter(
        processNoise: Float = 0.1f,
        measurementNoise: Float = 0.5f
    ): PyObject?

    /**
     * 对 RSSI 进行卡尔曼滤波
     * @param filter 滤波器对象，可为 null（回退模式）
     * @return 平滑后的 RSSI
     */
    fun filterRssi(filter: PyObject?, rssi: Float): Float

    /**
     * 计算设备距离（米）
     */
    fun calculateDistance(rssi: Float, txPower: Int, envFactor: Float): Float

    /**
     * 计算极坐标位置
     * @return Pair(距离, 角度-弧度)
     */
    fun calculatePolarPosition(
        rssi: Float,
        distance: Float,
        prevAngle: Float?
    ): Pair<Float, Float>

    /** 获取信号等级 (1-5) */
    fun getSignalLevel(rssi: Int): Int

    /** 重置滤波器 */
    fun resetFilter(filter: PyObject?)
}
