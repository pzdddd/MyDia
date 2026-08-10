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

/** 一条控制台日志 */
data class ConsoleLogEntry(
    val time: Long,
    val pkg: String,
    val msg: String,
)

/** 内存日志仓库（App 进程内，UI 读 [snapshot]）。 */
object ConsoleLogStore {
    private val buffer = java.util.concurrent.CopyOnWriteArrayList<ConsoleLogEntry>()
    private const val MAX = 400

    fun append(time: Long, pkg: String, msg: String) {
        buffer.add(ConsoleLogEntry(time, pkg, msg))
        while (buffer.size > MAX) buffer.removeAt(0)
    }

    fun snapshot(): List<ConsoleLogEntry> = buffer.toList()

    @Synchronized
    fun clear() { buffer.clear() }
}
