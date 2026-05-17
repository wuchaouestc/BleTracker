package com.example.bletracker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 蓝牙设备持久化实体
 *
 * 存储扫描到的蓝牙设备基本信息，用于收藏管理和快速识别
 */
@Entity(tableName = "ble_devices")
data class BleDeviceEntity(
    /** MAC地址作为主键，保证设备唯一性 */
    @PrimaryKey
    val mac: String,

    /** 设备名称（可能为空） */
    val name: String,

    /** 是否收藏 */
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,

    /** 用户自定义校准的TxPower（1米处RSSI），null表示使用默认值 */
    val customTxPower: Int? = null,

    /** 最后一次扫描到的时间戳（毫秒） */
    val lastSeen: Long = System.currentTimeMillis(),

    /** 最后一次定位纬度（预留） */
    val lastLatitude: Double? = null,

    /** 最后一次定位经度（预留） */
    val lastLongitude: Double? = null,

    /** 设备制造商名称 */
    val manufacturer: String = "",

    /** 信号强度等级 (1-5) */
    @ColumnInfo(defaultValue = "0")
    val signalLevel: Int = 0,

    /** 估计距离（米） */
    @ColumnInfo(defaultValue = "0.0")
    val estimatedDistance: Float = 0f
)
