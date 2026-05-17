package com.example.bletracker.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bletracker.data.ble.BleDeviceUiState
import com.example.bletracker.data.ble.Trend
import com.example.bletracker.data.ble.TrackedDeviceState
import com.example.bletracker.ui.theme.*

/**
 * 信号强度指示器组件
 *
 * 以电池图标风格显示信号强度等级 (1-5)
 */
@Composable
fun SignalStrengthIndicator(
    level: Int,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        5 -> SignalStrong
        4 -> SignalMedium
        3 -> SignalMedium
        else -> SignalWeak
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + index * 4).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (index < level) color
                        else Color.Gray.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

/**
 * 距离显示组件
 *
 * @param distance 距离（米）
 */
@Composable
fun DistanceDisplay(
    distance: Float,
    modifier: Modifier = Modifier
) {
    Text(
        text = formatDistance(distance),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = OnSurface,
        modifier = modifier
    )
}

/**
 * RSSI 显示组件
 */
@Composable
fun RssiDisplay(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.SignalCellularAlt,
            contentDescription = "信号",
            tint = when {
                rssi >= -60 -> SignalStrong
                rssi >= -75 -> SignalMedium
                else -> SignalWeak
            },
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "$rssi dBm",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )
    }
}

/**
 * 设备卡片组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: BleDeviceUiState,
    onToggleFavorite: () -> Unit,
    onCardClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onCardClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设备图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.isTracked) Icons.Filled.GpsFixed
                    else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (device.isTracked) TrackedTarget else Primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 设备信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayDeviceName(device.name, device.mac),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SignalStrengthIndicator(level = device.signalLevel)
                    Text(
                        text = device.lastSeen,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                    if (device.distance > 0f) {
                        Text(
                            text = "估算距离: ${formatDistance(device.distance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
                Text(
                    text = device.mac,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }

            // 收藏按钮
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (device.isFavorite) Icons.Filled.Star
                    else Icons.Filled.StarBorder,
                    contentDescription = if (device.isFavorite) "取消收藏" else "收藏",
                    tint = if (device.isFavorite) SignalMedium else OnSurfaceVariant
                )
            }
        }
    }
}

/**
 * 追踪设备状态栏组件
 *
 * 显示当前追踪设备的详细信息（距离、RSSI、信号趋势）
 */
@Composable
fun TrackingInfoBar(
    device: TrackedDeviceState?,
    modifier: Modifier = Modifier
) {
    if (device == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请选择设备开始追踪",
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 距离
            InfoColumn(
                label = "距离",
                value = formatDistance(device.distance).replace("厘米", "cm").replace("米", "m")
            )

            // RSSI
            InfoColumn(
                label = "RSSI",
                value = "${device.currentRssi} dBm",
                valueColor = when {
                    device.currentRssi >= -60 -> SignalStrong
                    device.currentRssi >= -75 -> SignalMedium
                    else -> SignalWeak
                }
            )

            // 趋势
            val trendIcon = when (device.distanceTrend) {
                Trend.GETTING_CLOSER -> Icons.Filled.TrendingDown
                Trend.GETTING_FARTHER -> Icons.Filled.TrendingUp
                Trend.STABLE -> Icons.Filled.TrendingFlat
            }
            val trendColor = when (device.distanceTrend) {
                Trend.GETTING_CLOSER -> TrendCloser
                Trend.GETTING_FARTHER -> TrendFarther
                Trend.STABLE -> TrendStable
            }
            val trendText = when (device.distanceTrend) {
                Trend.GETTING_CLOSER -> "靠近中"
                Trend.GETTING_FARTHER -> "远离中"
                Trend.STABLE -> "稳定"
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = trendIcon,
                    contentDescription = trendText,
                    tint = trendColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trendText,
                    style = MaterialTheme.typography.bodySmall,
                    color = trendColor
                )
            }
        }
    }
}

@Composable
private fun InfoColumn(
    label: String,
    value: String,
    valueColor: Color = OnSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )
    }
}

/**
 * 下拉选择器组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelector(
    devices: List<BleDeviceUiState>,
    selectedMac: String?,
    onDeviceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDevice = devices.find { it.mac == selectedMac }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (selectedDevice != null) displayDeviceName(selectedDevice.name, selectedDevice.mac) else "选择设备",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            leadingIcon = {
                Icon(Icons.Filled.Devices, contentDescription = null)
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "暂无设备，请先扫描",
                            color = OnSurfaceVariant
                        )
                    },
                    onClick = { expanded = false }
                )
            } else {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SignalStrengthIndicator(level = device.signalLevel)
                                Text(displayDeviceName(device.name, device.mac))
                            }
                        },
                        onClick = {
                            onDeviceSelected(device.mac)
                            expanded = false
                        },
                        leadingIcon = {
                            if (device.mac == selectedMac) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 开关设置项组件
 */
@Composable
fun SwitchSettingItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) OnSurface else OnSurfaceVariant
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Primary,
                    checkedTrackColor = Primary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

/** 安全格式化距离（避免 String.format 异常 + NaN/Infinity 保护） */
internal fun formatDistance(distance: Float): String {
    if (!distance.isFinite() || distance < 0f) return "--"
    return if (distance < 1.0f) "${(distance * 100).toInt()} 厘米"
    else "${"%.1f".format(distance)} 米"
}

/** 显示设备名称：Unknown 时追加 MAC 地址后 6 位以区分设备 */
fun displayDeviceName(name: String, mac: String): String {
    if (name == "Unknown" || name.startsWith("Unknown")) {
        val shortMac = mac.takeLast(6).takeIf { it.length >= 4 } ?: mac
        if (name == "Unknown") return "Unknown（$shortMac）"
        return name  // 已经是 "Unknown（厂商名）"，不重复添加
    }
    return name
}
