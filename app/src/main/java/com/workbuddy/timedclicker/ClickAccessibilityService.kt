package com.workbuddy.timedclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务 —— 核心功能：
 * 1. 扫描屏幕上的所有 UI 节点
 * 2. 按文字查找目标按钮
 * 3. 模拟点击（优先手势 API，兼容降级到 performAction）
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "TimedClicker"
        const val ACTION_FIND_AND_CLICK = "com.workbuddy.timedclicker.FIND_AND_CLICK"
        const val EXTRA_BUTTON_TEXT = "button_text"

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        /** 检查无障碍服务是否开启 */
        fun isRunning(context: Context): Boolean {
            val pref = context.getSharedPreferences("timed_clicker", Context.MODE_PRIVATE)
            return pref.getBoolean("service_enabled", false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        instance = this

        // 保存服务状态
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            saveServiceState(true)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        saveServiceState(true)
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        saveServiceState(false)
        Log.d(TAG, "无障碍服务已销毁")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FIND_AND_CLICK) {
            val buttonText = intent.getStringExtra(EXTRA_BUTTON_TEXT) ?: "确定"
            findAndClick(buttonText)
        }
        return START_STICKY
    }

    /**
     * 在主屏幕上查找指定文字的按钮并点击。
     * 搜索策略：
     * 1. 精确匹配文字
     * 2. 包含匹配文字
     * 3. contentDescription 匹配
     * 优先找可点击的；如果找到了但不可点击，尝试点它的父节点
     */
    fun findAndClick(buttonText: String) {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "无法获取当前窗口根节点")
            broadcastResult("error", "无法获取屏幕内容，请确认无障碍服务已开启")
            return
        }

        try {
            val candidates = mutableListOf<AccessibilityNodeInfo>()

            // 广度优先遍历，收集所有匹配的节点
            collectMatchingNodes(root, buttonText, candidates)

            if (candidates.isEmpty()) {
                Log.w(TAG, "未找到按钮: $buttonText")
                broadcastResult("not_found", buttonText)
                return
            }

            // 优先选可点击的
            var targetNode = candidates.firstOrNull { it.isClickable }

            // 如果没有直接可点击的，找最近的可点击父节点
            if (targetNode == null) {
                for (node in candidates) {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            targetNode = parent
                            break
                        }
                        parent = parent.parent
                    }
                    if (targetNode != null) break
                }
            }

            // 实在找不到可点击的，就用第一个候选节点本身的坐标
            if (targetNode == null) {
                targetNode = candidates.first()
            }

            // 获取点击坐标
            val rect = Rect()
            targetNode.getBoundsInScreen(rect)
            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()

            Log.d(TAG, "找到按钮「$buttonText」，坐标($centerX, $centerY)，准备点击")

            // 使用手势 API 点击（Android 7+ 支持，比 performAction 更可靠）
            performClick(centerX, centerY, buttonText)

        } catch (e: Exception) {
            Log.e(TAG, "findAndClick 异常: ${e.message}", e)
            broadcastResult("error", "点击过程出错: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    /** 递归收集匹配文字的节点 */
    private fun collectMatchingNodes(
        node: AccessibilityNodeInfo,
        target: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        if (text.equals(target, ignoreCase = true) ||
            desc.equals(target, ignoreCase = true) ||
            text.contains(target, ignoreCase = true) ||
            desc.contains(target, ignoreCase = true)
        ) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMatchingNodes(child, target, result)
        }
    }

    /** 用无障碍手势 API 执行点击（Android 7+） */
    private fun performClick(x: Float, y: Float, buttonText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x, y)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "✅ 手势点击成功")
                    broadcastResult("success", buttonText)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "手势点击被取消，尝试降级方案")
                    fallbackClick(x, y, buttonText)
                }
            }, null)
        } else {
            fallbackClick(x, y, buttonText)
        }
    }

    /** 降级方案：直接对根节点某个位置模拟点击（兼容旧设备） */
    private fun fallbackClick(x: Float, y: Float, buttonText: String) {
        val root = rootInActiveWindow
        if (root != null) {
            try {
                // 用 ACTION_CLICK 对根节点某个位置执行
                val success = root.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK,
                    android.os.Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, 0)
                    }
                )
                if (success) {
                    broadcastResult("success", buttonText)
                } else {
                    broadcastResult("error", "所有点击方式均失败")
                }
            } catch (e: Exception) {
                broadcastResult("error", "降级点击失败: ${e.message}")
            } finally {
                root.recycle()
            }
        }
    }

    /** 发送广播通知主界面结果 */
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
