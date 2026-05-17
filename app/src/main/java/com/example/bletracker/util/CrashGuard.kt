package com.example.bletracker.util

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * 全局错误处理工具
 *
 * 所有未捕获异常最终到达这里，显示错误对话框而非闪退
 */
object CrashGuard {

    internal const val TAG = "CrashGuard"

    /** 当前前台 Activity（用于显示对话框） */
    @Volatile
    var currentActivity: Activity? = null

    /**
     * 初始化全局异常捕获（在 Application.onCreate 调用）
     */
    fun install() {
        // 1. 线程级未捕获异常
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            try {
                showErrorDialog(
                    "应用错误",
                    "很抱歉，发生了意外错误。应用将尝试恢复。\n\n${throwable.message ?: "未知错误"}"
                )
                // 不调用 defaultHandler.uncaughtException() — 阻止闪退！
                // 让进程存活，用户体验优于闪退
            } catch (e: Exception) {
                Log.e(TAG, "Even crash handler failed", e)
                defaultHandler.uncaughtException(thread, throwable)
            }
        }

        // 2. 协程级异常处理器（可配置到各 ViewModel scope）
        Log.i(TAG, "CrashGuard installed")
    }

    /**
     * 创建协程异常处理器
     */
    fun coroutineHandler(tag: String): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            Log.e("$TAG/$tag", "Coroutine exception", throwable)
            showErrorDialog(
                "操作失败",
                "${throwable.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 安全执行，异常时显示对话框
     */
    inline fun <T> guard(tag: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            Log.e("CrashGuard/$tag", "Guard caught", e)
            showErrorDialog("操作失败", e.message ?: "未知错误")
            null
        }
    }

    /**
     * 安全执行 suspend，异常时显示对话框
     */
    suspend inline fun <T> suspendGuard(tag: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            Log.e("CrashGuard/$tag", "Suspend guard caught", e)
            showErrorDialog("操作失败", e.message ?: "未知错误")
            null
        }
    }

    /**
     * 显示错误对话框（必须在主线程调用）
     */
    fun showErrorDialog(title: String, message: String) {
        val activity = currentActivity ?: return
        try {
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    try {
                        AlertDialog.Builder(activity)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
                            .setCancelable(true)
                            .show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot show dialog", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showErrorDialog failed", e)
        }
    }
}
