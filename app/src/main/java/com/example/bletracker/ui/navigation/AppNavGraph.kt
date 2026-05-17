package com.example.bletracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.bletracker.ui.screen.devices.DeviceListScreen
import com.example.bletracker.ui.screen.detail.DetailPlaceholderScreen
import com.example.bletracker.ui.screen.history.HistoryPlaceholderScreen
import com.example.bletracker.ui.screen.settings.SettingsScreen
import com.example.bletracker.ui.screen.tracker.TrackerScreen
import com.example.bletracker.viewmodel.BleScanViewModel
import com.example.bletracker.viewmodel.DeviceTrackerViewModel

/**
 * 应用导航图
 *
 * 所有页面共享 Activity 级别的 ViewModel 实例
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    bleScanViewModel: BleScanViewModel,
    trackerViewModel: DeviceTrackerViewModel
) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Tracker.route
    ) {
        composable(BottomNavItem.Tracker.route) {
            TrackerScreen(
                bleScanViewModel = bleScanViewModel,
                trackerViewModel = trackerViewModel
            )
        }
        composable(BottomNavItem.Devices.route) {
            DeviceListScreen(viewModel = bleScanViewModel)
        }
        composable(BottomNavItem.History.route) {
            HistoryPlaceholderScreen()
        }
        composable(BottomNavItem.Settings.route) {
            SettingsScreen()
        }
    }
}
