package com.workbuddy.timedclicker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务 —— 诊断版（极简骨架）
 * 只保留生命周期回调，不做任何功能。
 * 目的：验证 MIUI 是否接受这个服务正常连接。
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "TimedClicker"
        const val ACTION_FIND_AND_CLICK = "com.workbuddy.timedclicker.FIND_AND_CLICK"
        const val EXTRA_BUTTON_TEXT = "button_text"
        const val EXTRA_TARGET_TIME = "target_time"

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        fun isRunning(context: Context): Boolean {
            val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            return pref.getBoolean("service_enabled", false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        saveServiceState(true)
        Log.i(TAG, "✅ 无障碍服务已连接 (诊断版)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        instance = this
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        saveServiceState(false)
        Log.d(TAG, "onDestroy")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FIND_AND_CLICK) {
            val buttonText = intent.getStringExtra(EXTRA_BUTTON_TEXT) ?: "确定"
            val targetTime = intent.getLongExtra(EXTRA_TARGET_TIME, 0L)
            Log.i(TAG, "收到点击指令: 「$buttonText」, targetTime=$targetTime")
            broadcastResult("diagnostic", buttonText)
        }
        return START_NOT_STICKY
    }

    private fun broadcastResult(status: String, buttonText: String) {
        val intent = Intent("com.workbuddy.timedclicker.CLICK_RESULT").apply {
            putExtra("status", status)
            putExtra("button_text", buttonText)
            putExtra("timestamp", System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }

    private fun saveServiceState(enabled: Boolean) {
        val pref = getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
        pref.edit().putBoolean("service_enabled", enabled).apply()
    }
}
