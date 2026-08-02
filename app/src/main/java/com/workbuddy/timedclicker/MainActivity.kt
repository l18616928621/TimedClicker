package com.workbuddy.timedclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "TimedClicker.Main"
    }

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnEnableService: MaterialButton
    private lateinit var etButtonText: com.google.android.material.textfield.TextInputEditText
    private lateinit var etOffsetMs: com.google.android.material.textfield.TextInputEditText
    private lateinit var etPreScanMs: com.google.android.material.textfield.TextInputEditText
    private lateinit var npHour: NumberPicker
    private lateinit var npMinute: NumberPicker
    private lateinit var npSecond: NumberPicker
    private lateinit var btnSchedule: MaterialButton
    private lateinit var tvTaskStatus: TextView
    private lateinit var btnCancel: MaterialButton
    private lateinit var cardTaskStatus: androidx.cardview.widget.CardView
    private lateinit var tvLog: TextView

    private val logEntries = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var serviceEnabled = false

    /** 从 SharedPreferences 恢复日志 */
    private fun loadLogs() {
        val pref = getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
        val saved = pref.getString("log_entries", null)
        if (saved != null) {
            logEntries.addAll(saved.split("\n"))
        }
    }

    /** 持久化日志到 SharedPreferences */
    private fun saveLogs() {
        val pref = getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
        pref.edit().putString("log_entries", logEntries.joinToString("\n")).apply()
    }

    /** 接收点击结果的广播 */
    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: return
            val buttonText = intent.getStringExtra("button_text") ?: "未知"
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val timeStr = dateFormat.format(Date(timestamp))

            when (status) {
                "success" -> {
                    addLog("✅ $timeStr 点击成功 →「$buttonText」")
                    Toast.makeText(this@MainActivity, "✅ 已点击「$buttonText」", Toast.LENGTH_SHORT).show()
                }
                "not_found" -> {
                    addLog("⚠️ $timeStr 未找到按钮 →「$buttonText」")
                    Toast.makeText(this@MainActivity, "⚠️ 未找到「$buttonText」", Toast.LENGTH_SHORT).show()
                }
                "error" -> {
                    addLog("❌ $timeStr 点击失败 → $buttonText")
                    Toast.makeText(this@MainActivity, "❌ 点击失败", Toast.LENGTH_SHORT).show()
                }
            }

            // 点击完成后更新 UI
            updateTaskStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadLogs()
        setupNumberPickers()
        restoreOffsetValue()
        setupListeners()
        registerResultReceiver()

        // 初始更新状态
        updateServiceStatus()
        updateTaskStatus()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页回来，刷新服务状态
        updateServiceStatus()
        updateTaskStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(resultReceiver) } catch (_: Exception) {}
    }

    private fun initViews() {
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnEnableService = findViewById(R.id.btnEnableService)
        etButtonText = findViewById(R.id.etButtonText)
        etOffsetMs = findViewById(R.id.etOffsetMs)
        etPreScanMs = findViewById(R.id.etPreScanMs)
        npHour = findViewById(R.id.npHour)
        npMinute = findViewById(R.id.npMinute)
        npSecond = findViewById(R.id.npSecond)
        btnSchedule = findViewById(R.id.btnSchedule)
        tvTaskStatus = findViewById(R.id.tvTaskStatus)
        btnCancel = findViewById(R.id.btnCancel)
        cardTaskStatus = findViewById(R.id.cardTaskStatus)
        tvLog = findViewById(R.id.tvLog)
    }

    private fun setupNumberPickers() {
        npHour.apply {
            minValue = 0
            maxValue = 23
            value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
        }
        npMinute.apply {
            minValue = 0
            maxValue = 59
            value = Calendar.getInstance().get(Calendar.MINUTE)
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
        }
        npSecond.apply {
            minValue = 0
            maxValue = 59
            value = 0
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
        }
    }

    private fun setupListeners() {
        // 开启无障碍服务
        btnEnableService.setOnClickListener {
            openAccessibilitySettings()
        }

        // 设置定时
        btnSchedule.setOnClickListener {
            if (!serviceEnabled) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                openAccessibilitySettings()
                return@setOnClickListener
            }

            val buttonText = etButtonText.text?.toString()?.trim()
            if (buttonText.isNullOrBlank()) {
                Toast.makeText(this, "请输入目标按钮文字", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hour = npHour.value
            val minute = npMinute.value
            val second = npSecond.value
            val offsetMs = etOffsetMs.text?.toString()?.toIntOrNull() ?: 0
            val preScanMs = etPreScanMs.text?.toString()?.toIntOrNull() ?: 2000

            // 请求电池优化白名单
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    requestIgnoreBatteryOptimizations()
                }
            }

            AlarmReceiver.schedule(this, hour, minute, second, buttonText, offsetMs, preScanMs)

            // 单独持久化提前量，方便下次打开自动填入
            getSharedPreferences("timed_clicker", Context.MODE_PRIVATE).edit()
                .putInt("task_offset_ms", offsetMs)
                .putInt("task_prescan_ms", preScanMs)
                .apply()

            val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second)
            val offsetStr = if (offsetMs != 0) " (时钟补偿 ${offsetMs}ms)" else ""
            addLog("⏰ 已设定时：$timeStr$offsetStr (提前${preScanMs}ms预扫描) → 点击「$buttonText」")
            Toast.makeText(this, "✅ 定时已设置：$timeStr$offsetStr", Toast.LENGTH_SHORT).show()

            updateTaskStatus()
        }

        // 取消定时
        btnCancel.setOnClickListener {
            AlarmReceiver.cancel(this)
            addLog("❌ 已取消定时任务")
            Toast.makeText(this, "已取消定时任务", Toast.LENGTH_SHORT).show()
            updateTaskStatus()
        }
    }

    private fun restoreOffsetValue() {
        val pref = getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
        val savedOffset = pref.getInt("task_offset_ms", 0)
        val savedPreScan = pref.getInt("task_prescan_ms", 2000)
        if (savedOffset != 0) {
            etOffsetMs.setText(savedOffset.toString())
        }
        etPreScanMs.setText(savedPreScan.toString())
    }

    private fun updateServiceStatus() {
        serviceEnabled = ClickAccessibilityService.isRunning(this)
        if (serviceEnabled) {
            tvServiceStatus.text = getString(R.string.service_running)
            tvServiceStatus.setTextColor(0xFF4CAF50.toInt())
            btnEnableService.text = "已开启 ✓"
        } else {
            tvServiceStatus.text = getString(R.string.service_stopped)
            tvServiceStatus.setTextColor(0xFFAAAAAA.toInt())
            btnEnableService.text = getString(R.string.enable_accessibility)
        }
    }

    private fun updateTaskStatus() {
        if (AlarmReceiver.isScheduled(this)) {
            val pref = getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            val hour = pref.getInt("task_hour", 0)
            val minute = pref.getInt("task_minute", 0)
            val second = pref.getInt("task_second", 0)
            val button = pref.getString("task_button", "") ?: ""
            val offsetMs = pref.getInt("task_offset_ms", 0)
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second)
            val offsetStr = if (offsetMs != 0) " (提前 ${offsetMs}ms)" else ""

            tvTaskStatus.text = "⏰ 将在 $timeStr$offsetStr 点击「$button」"
            cardTaskStatus.visibility = android.view.View.VISIBLE
            btnSchedule.text = "更新定时"
        } else {
            cardTaskStatus.visibility = android.view.View.GONE
            btnSchedule.text = getString(R.string.schedule_button)
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "无法请求电池优化豁免: ${e.message}")
            }
        }
    }

    private fun registerResultReceiver() {
        val filter = IntentFilter("com.workbuddy.timedclicker.CLICK_RESULT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resultReceiver, filter)
        }
    }

    private fun addLog(message: String) {
        val timeStr = dateFormat.format(Date())
        val entry = "$timeStr  $message"
        logEntries.add(0, entry)
        // 最多保留 50 条
        if (logEntries.size > 50) {
            logEntries.removeAt(logEntries.size - 1)
        }
        tvLog.text = logEntries.joinToString("\n")
        saveLogs()
    }
}
