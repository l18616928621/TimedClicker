package com.workbuddy.timedclicker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * 定时广播接收器 —— 时间到了，触发无障碍服务去点击
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "TimedClicker.Alarm"
        private const val ALARM_REQUEST_CODE = 1001
        const val ACTION_TRIGGER = "com.workbuddy.timedclicker.ALARM_TRIGGER"

        /**
         * 设置一次性精确定时闹钟。
         * @param context 上下文
         * @param hour 小时 (0-23)
         * @param minute 分钟 (0-59)
         * @param second 秒 (0-59)
         * @param buttonText 要点击的按钮文字
         * @param offsetMs 提前量补偿（毫秒），正数=提前触发，负数=延后触发
         */
        fun schedule(context: Context, hour: Int, minute: Int, second: Int,
                     buttonText: String, offsetMs: Int = 0) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, second)
                set(Calendar.MILLISECOND, 0)
                // 如果时间已过，设置为明天
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // 应用提前量补偿：正数 offsetMs = 提前触发
            val triggerTime = calendar.timeInMillis - offsetMs

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER
                putExtra(ClickAccessibilityService.EXTRA_BUTTON_TEXT, buttonText)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent, flags
            )

            // 使用 setExactAndAllowWhileIdle 保证在打盹模式下也能准时触发
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Log.d(TAG, "定时闹钟已设置：${calendar.time} → 点击「$buttonText」(提前量: ${offsetMs}ms, 实际触发: ${java.util.Date(triggerTime)})")

            // 保存状态
            val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            pref.edit().apply {
                putBoolean("task_active", true)
                putInt("task_hour", hour)
                putInt("task_minute", minute)
                putInt("task_second", second)
                putString("task_button", buttonText)
                putLong("task_trigger_time", triggerTime)
                putInt("task_offset_ms", offsetMs)
                apply()
            }
        }

        /**
         * 取消定时任务
         */
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent, flags
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()

            val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            pref.edit().clear().apply()

            Log.d(TAG, "定时闹钟已取消")
        }

        /** 检查是否有活跃的定时任务 */
        fun isScheduled(context: Context): Boolean {
            return context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
                .getBoolean("task_active", false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER) {
            val buttonText = intent.getStringExtra(ClickAccessibilityService.EXTRA_BUTTON_TEXT)
                ?: "确定"

            Log.i(TAG, "⏰ 定时触发！目标按钮：「$buttonText」")

            // 通过无障碍服务执行点击
            val serviceIntent = Intent(context, ClickAccessibilityService::class.java).apply {
                action = ClickAccessibilityService.ACTION_FIND_AND_CLICK
                putExtra(ClickAccessibilityService.EXTRA_BUTTON_TEXT, buttonText)
            }

            // 如果服务正在运行，startService 会把 intent 传给 onStartCommand
            context.startService(serviceIntent)

            // 清除定时状态（这是一次性任务）
            val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            pref.edit().putBoolean("task_active", false).apply()
        }
    }
}
