package com.workbuddy.timedclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 静态注册的广播接收器 —— 即使 App 在后台也能接收点击结果并持久化。
 */
class ClickResultReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "TimedClicker.Result"
        private const val MAX_LOGS = 50
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val status = intent?.getStringExtra("status") ?: return
        val buttonText = intent.getStringExtra("button_text") ?: "未知"
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = dateFormat.format(Date(timestamp))

        val icon = when (status) {
            "success" -> "✅"
            "not_found" -> "⚠️"
            "error" -> "❌"
            else -> "📋"
        }
        val msg = when (status) {
            "success" -> "点击成功 →「$buttonText」"
            "not_found" -> "未找到按钮 →「$buttonText」"
            "error" -> "点击失败 → $buttonText"
            else -> "$status: $buttonText"
        }

        val entry = "$timeStr  $icon $msg"
        Log.i(TAG, entry)

        // 持久化到 SharedPreferences
        val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
        val saved = pref.getString("log_entries", "")
        val logs = if (saved.isNullOrEmpty()) mutableListOf<String>() else saved.split("\n").toMutableList()
        logs.add(0, entry)
        if (logs.size > MAX_LOGS) logs.removeAt(logs.size - 1)
        pref.edit().putString("log_entries", logs.joinToString("\n")).apply()
    }
}
