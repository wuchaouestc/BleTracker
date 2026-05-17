package com.example.bletracker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bletracker.ui.navigation.AppNavGraph
import com.example.bletracker.ui.navigation.bottomNavItems
import com.example.bletracker.ui.theme.BleTrackerTheme
import com.example.bletracker.util.CrashGuard
import com.example.bletracker.viewmodel.BleScanViewModel
import com.example.bletracker.viewmodel.DeviceTrackerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 主 Activity（全路径异常保护)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val bleScanViewModel: BleScanViewModel by viewModels()
    private val trackerViewModel: DeviceTrackerViewModel by viewModels()

    private val bluetoothManager by lazy {
        CrashGuard.guard("btManager") { getSystemService(BluetoothManager::class.java) }
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        CrashGuard.guard("btAdapter") { bluetoothManager?.adapter }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            if (permissions.values.all { it }) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    CrashGuard.suspendGuard("refreshAfterPerm") {
                        bleScanViewModel.refreshDevices()
                    }
                }
            } else {
                Toast.makeText(this, "需要蓝牙权限才能扫描", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Permission callback error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.currentActivity = this

        try {
            requestRequiredPermissions()
            if (bluetoothAdapter?.isEnabled != true) {
                enableBluetooth()
            }
            // 启动时加载历史
            lifecycleScope.launch {
                CrashGuard.suspendGuard("initLoadDevices") {
                    bleScanViewModel.refreshDevices()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate error", e)
            CrashGuard.showErrorDialog("启动错误", e.message ?: "应用启动失败")
        }

        setContent {
            BleTrackerTheme {
                MainScreen(
                    bleScanViewModel = bleScanViewModel,
                    trackerViewModel = trackerViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CrashGuard.currentActivity = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (CrashGuard.currentActivity == this) {
            CrashGuard.currentActivity = null
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED
                ) permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
                ) permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                @Suppress("DEPRECATION")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (_: Exception) {}
        if (permissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun enableBluetooth() {
        try {
            if (bluetoothAdapter?.isEnabled != true) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } catch (_: Exception) {}
    }
}

/**
 * 错误降级界面 — Compose 完全崩溃时显示
 */
@Composable
private fun ErrorFallbackScreen(message: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚠️ 发生错误",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "应用正在尝试恢复，请稍候或重启应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    bleScanViewModel: BleScanViewModel,
    trackerViewModel: DeviceTrackerViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            try {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MainScreen", "Navigation error", e)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon
                                else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AppNavGraph(
                navController = navController,
                bleScanViewModel = bleScanViewModel,
                trackerViewModel = trackerViewModel
            )
        }
    }
}
