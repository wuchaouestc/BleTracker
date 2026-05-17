package com.example.bletracker.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 扫描记录持久化实体
 *
 * 每次扫描到蓝牙设备时记录一条数据，用于历史信号追踪和趋势分析。
 * 采用外键关联到 BleDeviceEntity，设备删除时级联删除记录。
 */
@Entity(
    tableName = "scan_records",
    foreignKeys = [
        ForeignKey(
            entity = BleDeviceEntity::class,
            parentColumns = ["mac"],
            childColumns = ["deviceMac"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deviceMac", "timestamp"])
    ]
)
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 关联的设备MAC地址 */
    val deviceMac: String,

    /** RSSI 信号强度 */
    val rssi: Int,

    /** 计算出的距离（米） */
    val distance: Float,

    /** 扫描时间戳（毫秒） */
    val timestamp: Long = System.currentTimeMillis(),

    /** 纬度（预留，后续可结合GPS定位） */
    val latitude: Double? = null,

    /** 经度（预留，后续可结合GPS定位） */
    val longitude: Double? = null
)
