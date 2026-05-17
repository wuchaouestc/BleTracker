package com.example.bletracker.ui.screen.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bletracker.ui.components.DeviceSelector
import com.example.bletracker.ui.components.TrackingInfoBar
import com.example.bletracker.ui.components.displayDeviceName
import com.example.bletracker.ui.custom.radar.RadarView
import com.example.bletracker.viewmodel.BleScanViewModel
import com.example.bletracker.viewmodel.DeviceTrackerViewModel

/**
 * 雷达追踪页（主界面）
 *
 * 修复：选择设备与开始追踪分离，添加"开始追踪"按钮，
 * 追踪时自动启动扫描获取 RSSI 数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    bleScanViewModel: BleScanViewModel,
    trackerViewModel: DeviceTrackerViewModel
) {
    val deviceList by bleScanViewModel.deviceList.collectAsState()
    val isScanning by bleScanViewModel.isScanning.collectAsState()
    val isBluetoothEnabled by bleScanViewModel.isBluetoothEnabled.collectAsState()
    val isTracking by trackerViewModel.isTracking.collectAsState()
    val radarTargets by trackerViewModel.radarTargets.collectAsState()
    val trackedDevice by trackerViewModel.trackedDevice.collectAsState()

    var selectedDeviceMac by remember { mutableStateOf<String?>(null) }

    // 追踪停止时清除选中
    LaunchedEffect(isTracking) {
        if (!isTracking) selectedDeviceMac = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ── 顶部操作栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "蓝牙定位追踪",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(
                onClick = {
                    try {
                        if (isScanning) {
                            bleScanViewModel.stopScan()
                            if (isTracking) trackerViewModel.stopTracking()
                        } else {
                            if (isBluetoothEnabled) {
                                bleScanViewModel.startScan()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TrackerScreen", "Scan toggle error", e)
                    }
                }
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isScanning) "停止" else "扫描")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 设备选择器（只选择，不自动追踪）──
        DeviceSelector(
            devices = deviceList,
            selectedMac = selectedDeviceMac,
            onDeviceSelected = { mac ->
                selectedDeviceMac = mac
            },
            enabled = deviceList.isNotEmpty() && !isTracking
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 开始追踪按钮 ──
        Button(
            onClick = {
                try {
                    selectedDeviceMac?.let { mac ->
                        // 确保扫描运行中（追踪依赖扫描 RSSI 数据）
                        if (!isScanning) {
                            bleScanViewModel.startScan()
                        }
                        trackerViewModel.startTracking(mac)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TrackerScreen", "Start tracking error", e)
                }
            },
            enabled = selectedDeviceMac != null && !isTracking,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Filled.GpsFixed,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedDeviceMac == null) "请选择设备开始追踪"
                else if (isTracking) "追踪中…"
                else "开始追踪"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 雷达图 ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!isBluetoothEnabled) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.BluetoothDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("蓝牙未开启", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (radarTargets.isEmpty() && !isTracking) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("选择设备后点击「开始追踪」", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (isTracking && radarTargets.isEmpty()) {
                    // 这个分支理论上不再会走到，但保留作为防御
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.GpsFixed,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("正在获取信号…", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    RadarView(
                        targets = radarTargets,
                        maxDistance = 10f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 底部追踪状态栏 ──
        if (isTracking) {
            // 追踪中：显示距离+信号
            TrackingInfoBar(
                device = trackedDevice,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // 未追踪：显示选中设备信息
            selectedDeviceMac?.let { mac ->
                val device = deviceList.find { it.mac == mac }
                if (device != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Bluetooth, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(displayDeviceName(device.name, device.mac),
                                    style = MaterialTheme.typography.titleSmall)
                                Text("MAC: ${device.mac}\n信号: ${device.signalLevel}/5  |  RSSI: ${device.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
