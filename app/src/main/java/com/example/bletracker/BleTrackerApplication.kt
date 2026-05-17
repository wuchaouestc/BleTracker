package com.example.bletracker

import android.app.Application
import android.util.Log
import com.example.bletracker.util.CrashGuard
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口
 *
 * 安装全局异常捕获，确保任何未预期异常都显示对话框而非闪退
 */
@HiltAndroidApp
class BleTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // ═══ 全局崩溃保护 — 最后防线 ═══
        CrashGuard.install()
        Log.i("BleTracker", "Application initialized with crash guard")
    }
}
