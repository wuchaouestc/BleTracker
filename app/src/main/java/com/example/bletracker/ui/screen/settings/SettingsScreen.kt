package com.example.bletracker.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bletracker.BuildConfig
import com.example.bletracker.viewmodel.SettingsViewModel

/**
 * 设置页（简化版）
 *
 * 管理蓝牙扫描参数和定位算法配置
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scanSettings by viewModel.scanSettings.collectAsState()
    val envFactor by viewModel.envFactor.collectAsState()
    val txPower by viewModel.txPower.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 标题 ──
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 蓝牙扫描设置 ──
        SettingsSection(title = "蓝牙扫描设置") {
            // 扫描间隔选择
            ScanIntervalSelector(
                currentInterval = scanSettings.scanInterval,
                onIntervalSelected = { viewModel.setScanInterval(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 定位算法设置 ──
        SettingsSection(title = "定位算法设置") {
            // 环境参数说明
            Text(
                text = "环境参数影响距离估算精度：\n室内办公=2.5 | 居家=2.7 | 室外=2.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 环境预设按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnvPresetButton("室内办公", 2.5f, envFactor) {
                    viewModel.setEnvFactor(2.5f)
                }
                EnvPresetButton("居家", 2.7f, envFactor) {
                    viewModel.setEnvFactor(2.7f)
                }
                EnvPresetButton("室外", 2.0f, envFactor) {
                    viewModel.setEnvFactor(2.0f)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TxPower 显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "1米参考RSSI (TxPower)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "在设备1米处校准获得准确值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$txPower dBm",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 关于 ──
        SettingsSection(title = "关于") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "版本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 设置分组容器
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * 扫描间隔选择器
 */
@Composable
private fun ScanIntervalSelector(
    currentInterval: Long,
    onIntervalSelected: (Long) -> Unit
) {
    val intervals = listOf(
        1000L to "1秒 (快速)",
        2000L to "2秒 (标准)",
        5000L to "5秒 (省电)"
    )

    Column {
        intervals.forEach { (interval, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentInterval == interval,
                    onClick = { onIntervalSelected(interval) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 环境预设按钮
 */
@Composable
private fun EnvPresetButton(
    label: String,
    value: Float,
    currentValue: Float,
    onClick: () -> Unit
) {
    val isSelected = abs(currentValue - value) < 0.01f
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = "$label\n$value",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun abs(value: Float): Float = if (value < 0) -value else value
