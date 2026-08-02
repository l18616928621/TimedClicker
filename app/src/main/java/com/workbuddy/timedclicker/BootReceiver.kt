package com.workbuddy.timedclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机广播接收器 —— 重启手机后恢复定时任务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d("TimedClicker.Boot", "手机重启，检查定时任务...")

        val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
        if (pref.getBoolean("task_active", false)) {
            val hour = pref.getInt("task_hour", 0)
            val minute = pref.getInt("task_minute", 0)
            val second = pref.getInt("task_second", 0)
            val buttonText = pref.getString("task_button", "确定") ?: "确定"
            val offsetMs = pref.getInt("task_offset_ms", 0)

            Log.d("TimedClicker.Boot", "恢复定时任务：$hour:$minute:$second → 「$buttonText」(提前量: ${offsetMs}ms)")
            AlarmReceiver.schedule(context, hour, minute, second, buttonText, offsetMs)
        }
    }
}
