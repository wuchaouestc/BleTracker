package com.example.bletracker.data.ble

import com.example.bletracker.data.ble.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙低功耗扫描管理器接口
 *
 * 封装 Android 原生 BLE API，提供响应式数据流。
 * 支持扫描控制、设备发现、RSSI 实时监听。
 */
interface BleScanManager {

    /**
     * 开始扫描蓝牙设备
     *
     * @param scanInterval 扫描间隔（毫秒），默认1秒
     * @return 设备列表流（包含实时RSSI更新）
     */
    fun startScan(scanInterval: Long = 1000L): Flow<List<BleDevice>>

    /** 停止扫描 */
    fun stopScan()

    /**
     * 获取指定设备的实时 RSSI 流
     *
     * @param mac 设备 MAC 地址
     * @return RSSI 实时数据流
     */
    fun getDeviceRssi(mac: String): Flow<Int>

    /** 当前是否正在扫描 */
    val isScanning: StateFlow<Boolean>

    /** 蓝牙是否已开启 */
    val isBluetoothEnabled: StateFlow<Boolean>

    /** 扫描到的设备数量 */
    val deviceCount: StateFlow<Int>
}
