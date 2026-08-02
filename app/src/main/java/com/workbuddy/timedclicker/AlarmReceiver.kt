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
         * @param preScanMs 预扫描提前量（毫秒），闹钟提前触发用以扫描锁定按钮，到 targetTime 才真正点击
         */
        fun schedule(context: Context, hour: Int, minute: Int, second: Int,
                     buttonText: String, offsetMs: Int = 0, preScanMs: Int = 2000) {
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

            // 精确目标时间 = 设定时间 - 时钟补偿
            val targetTime = calendar.timeInMillis - offsetMs
            // 闹钟触发时间 = 目标时间 - 预扫描提前量（提前唤醒，锁定按钮）
            val alarmTime = targetTime - preScanMs

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER
                putExtra(ClickAccessibilityService.EXTRA_BUTTON_TEXT, buttonText)
                putExtra(ClickAccessibilityService.EXTRA_TARGET_TIME, targetTime)
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
                    alarmTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
                )
            }

            Log.d(TAG, "定时闹钟已设置：" +
                    "设定=${java.util.Date(calendar.timeInMillis)} " +
                    "时补=${offsetMs}ms " +
                    "预扫提前=${preScanMs}ms " +
                    "目标点击=${java.util.Date(targetTime)} " +
                    "闹钟=${java.util.Date(alarmTime)} " +
                    "按钮「$buttonText」")

            // 保存状态
            val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            pref.edit().apply {
                putBoolean("task_active", true)
                putInt("task_hour", hour)
                putInt("task_minute", minute)
                putInt("task_second", second)
                putString("task_button", buttonText)
                putLong("task_trigger_time", alarmTime)
                putLong("task_target_time", targetTime)
                putInt("task_offset_ms", offsetMs)
                putInt("task_prescan_ms", preScanMs)
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
            val targetTime = intent.getLongExtra(ClickAccessibilityService.EXTRA_TARGET_TIME, 0L)

            Log.i(TAG, "⏰ 定时触发！目标按钮：「$buttonText」，精确点击时间: ${java.util.Date(targetTime)}")

            // 通过无障碍服务执行点击（携带 targetTime，服务内部会扫描锁定并等到精确时刻）
            val serviceIntent = Intent(context, ClickAccessibilityService::class.java).apply {
                action = ClickAccessibilityService.ACTION_FIND_AND_CLICK
                putExtra(ClickAccessibilityService.EXTRA_BUTTON_TEXT, buttonText)
                putExtra(ClickAccessibilityService.EXTRA_TARGET_TIME, targetTime)
            }

            // 如果服务正在运行，startService 会把 intent 传给 onStartCommand
            context.startService(serviceIntent)

            // 清除定时状态（这是一次性任务）
            val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            pref.edit().putBoolean("task_active", false).apply()
        }
    }
}
