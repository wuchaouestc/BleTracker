package com.example.bletracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航项定义
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Tracker : BottomNavItem(
        route = "tracker",
        title = "追踪",
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar
    )

    data object Devices : BottomNavItem(
        route = "devices",
        title = "设备",
        selectedIcon = Icons.Filled.Devices,
        unselectedIcon = Icons.Outlined.Devices
    )

    data object History : BottomNavItem(
        route = "history",
        title = "历史",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    )

    data object Settings : BottomNavItem(
        route = "settings",
        title = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

/**
 * 所有导航项
 */
val bottomNavItems = listOf(
    BottomNavItem.Tracker,
    BottomNavItem.Devices,
    BottomNavItem.History,
    BottomNavItem.Settings
)
