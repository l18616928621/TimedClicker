package com.workbuddy.timedclicker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent

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
        // 记录状态，方便 MainActivity 读取
        getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            .edit().putBoolean("service_enabled", true).apply()
        Log.i(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        instance = this
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            .edit().putBoolean("service_enabled", false).apply()
        Log.d(TAG, "无障碍服务已销毁")
    }
}
