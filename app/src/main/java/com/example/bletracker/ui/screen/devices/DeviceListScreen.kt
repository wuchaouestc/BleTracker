package com.example.bletracker.ui.screen.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bletracker.ui.components.DeviceCard
import com.example.bletracker.viewmodel.BleScanViewModel

/**
 * 设备列表页
 *
 * 显示扫描到的蓝牙设备列表，支持收藏操作。
 * 与追踪页共享同一 ViewModel 实例，列表保持完全一致。
 */
@Composable
fun DeviceListScreen(
    viewModel: BleScanViewModel
) {
    val deviceList by viewModel.deviceList.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val deviceCount by viewModel.deviceCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ── 标题区域 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设备列表",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isScanning || deviceCount > 0) {
                Text(
                    text = if (isScanning) "扫描中 · $deviceCount 台"
                    else "$deviceCount 台设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 设备列表 ──
        if (deviceList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "未发现设备",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isScanning) "正在扫描中..."
                        else "请返回追踪页开启扫描",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = deviceList,
                    key = { it.mac }
                ) { device ->
                    DeviceCard(
                        device = device,
                        onToggleFavorite = {
                            try {
                                viewModel.toggleFavorite(device.mac)
                            } catch (e: Exception) {
                                android.util.Log.e("DeviceList", "Toggle favorite error", e)
                            }
                        },
                        onCardClick = {}
                    )
                }
            }
        }
    }
}
