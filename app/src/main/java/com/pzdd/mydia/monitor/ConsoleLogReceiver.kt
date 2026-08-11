package com.pzdd.mydia.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收被注入进程通过广播回传的模块日志（远程控制台）。
 * 在 AndroidManifest.xml 里静态注册（exported=true，目标 App 可发）。
 *
 * 数据存入 [ConsoleLogStore]，UI 由 [com.pzdd.mydia.ui.ConsoleLogActivity] 展示。
 */
class ConsoleLogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val msg = intent.getStringExtra(EXTRA_MSG) ?: return
        ConsoleLogStore.append(
            time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis()),
            pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "?",
            msg = msg,
        )
    }

    companion object {
        const val ACTION = "com.pzdd.mydia.ACTION_CONSOLE_LOG"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_MSG = "message"
        const val EXTRA_TIME = "time"
    }
}

/** 一条控制台日志。 */
data class ConsoleLogEntry(
    val time: Long,
    val pkg: String,
    val msg: String,
    /** 功能分类（由 [ConsoleLogStore] 从消息推断，UI 按此分组过滤） */
    val category: LogCategory,
)

/** 日志分类（UI 顶部分类 tab）。 */
enum class LogCategory(val label: String) {
    /** 注入流程 / 模块加载 */
    Inject("注入"),
    /** 对话框 / 弹窗 */
    Dialog("对话框"),
    /** 按钮 / 点击 */
    Button("按钮"),
    /** 反检测 / 隐藏 */
    AntiDetection("反检测"),
    /** 模拟 / 伪装（时间/设备/网络/位置…） */
    Fake("模拟"),
    /** 监控（Shell/算法/SQL/Intent/HTTP…） */
    Monitor("监控"),
    /** Frida */
    Frida("Frida"),
    /** 其它（无法归类的通用日志） */
    Other("其它"),
}

/** 内存日志仓库（App 进程内，UI 读 [snapshot]）。 */
object ConsoleLogStore {
    private val buffer = java.util.concurrent.CopyOnWriteArrayList<ConsoleLogEntry>()
    private const val MAX = 600

    fun append(time: Long, pkg: String, msg: String) {
        buffer.add(ConsoleLogEntry(time, pkg, msg, inferCategory(msg)))
        while (buffer.size > MAX) buffer.removeAt(0)
    }

    fun snapshot(): List<ConsoleLogEntry> = buffer.toList()

    @Synchronized
    fun clear() { buffer.clear() }

    /**
     * 从日志消息推断功能分类（hook 类名前缀 → 分类）。
     * 各 hook 的 Module.log 都以「XxxHook: ...」开头，按类名关键词归类。
     */
    fun inferCategory(msg: String): LogCategory {
        val m = msg.lowercase()
        return when {
            // 注入流程 / 模块加载
            "loading hook module" in m || "injected" in m || "application ready" in m ||
                "module disabled" in m || "skip" in m || "inject" in m -> LogCategory.Inject
            // 对话框 / 弹窗（DialogCancel / AlertClose / AlertDisable / 全局取消）
            "dialogcancel" in m || "alertclose" in m || "alertdisable" in m ||
                "global_alert" in m || "disable_alert" in m || "alert " in m -> LogCategory.Dialog
            // 按钮（HideButton / DisableButton / AutoClick）
            "hidebutton" in m || "disablebutton" in m || "autoclick" in m ||
                "hide_btn" in m || "dis_btn" in m || "click_btn" in m -> LogCategory.Button
            // 反检测 / 隐藏（HideXposed / FakeXposed / HideRoot / AntiDebug / HideEmulator…）
            "hidexposed" in m || "fakexposed" in m || "hideroot" in m || "hideemulator" in m ||
                "hidemulti" in m || "hideonbackground" in m || "protect" in m ||
                "hide_dex" in m || "stack" in m && "filter" in m || "waitfordebug" in m ||
                "debug" in m && "hook" in m -> LogCategory.AntiDetection
            // 模拟 / 伪装（FakeTime / FakeNetwork / DeviceProps / GPS / Sensor / Proxy）
            "faketime" in m || "fakenetwork" in m || "deviceprops" in m || "forceproxy" in m ||
                "httpproxy" in m || "locationhook" in m || "sensorhook" in m ||
                "timezone" in m || "wifihook" in m || "settingsfake" in m || "vpn" in m -> LogCategory.Fake
            // 监控（Shell / Algorithm / SQL / Intent / HTTP / AndroidJson / ClassLoader）
            "shellmonitor" in m || "algorithm" in m || "sql" in m || "intentmonitor" in m ||
                "httpmonitor" in m || "androidjson" in m || "classloader" in m ||
                "monitor" in m || "trace" in m || "stackhook" in m || "threadhook" in m -> LogCategory.Monitor
            // Frida
            "frida" in m -> LogCategory.Frida
            else -> LogCategory.Other
        }
    }
}
