package com.pzdd.mydia.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.pzdd.mydia.module.rewrite.bytesToHex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App 端：接收注入侧回传的算法监控数据。
 * 对应 Dia 的 com.mhook.dialog.task.receiver.AlgorithmMonitorReceiver。
 *
 * 注入侧（[com.pzdd.mydia.algorithm.AlgorithmHookManager]）hook 到加解密调用后，
 * 通过显式广播（component 指向本 receiver）把数据发回 MyDia 进程。
 *
 * 这里把数据追加写到一个内存日志缓冲 + 持久化文件，供 [com.pzdd.mydia.ui.AlgorithmLogActivity] 展示。
 *
 * 注意：receiver 必须 exported=true 并对目标 App 可发——见 AndroidManifest 里的声明。
 * 进程隔离：本 receiver 跑在 MyDia 进程，与注入侧是两个进程，所以只能靠广播/IPC 传数据。
 */
class AlgorithmMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.hasExtra("al_name")) return
        val info = MonitorRecord(
            time = System.currentTimeMillis(),
            algo = intent.getStringExtra("al_name") ?: "?",
            pkg = intent.getStringExtra("package_name") ?: "",
            process = intent.getStringExtra("process") ?: "",
            thread = intent.getStringExtra("thread") ?: "",
            stack = intent.getStringExtra("stack") ?: "",
            data = intent.b64("al_data"),
            ret = intent.b64("return"),
            key = intent.b64("al_key"),
            iv = intent.b64("al_iv"),
        )
        MonitorLogStore.append(context, info)
    }

    private fun Intent.b64(key: String): ByteArray? =
        if (hasExtra(key)) runCatching { Base64.decode(getStringExtra(key), Base64.NO_WRAP) }.getOrNull()
        else null
}

/** 一条监控记录 */
data class MonitorRecord(
    val time: Long,
    val algo: String,
    val pkg: String,
    val process: String,
    val thread: String,
    val stack: String,
    val data: ByteArray?,
    val ret: ByteArray?,
    val key: ByteArray?,
    val iv: ByteArray?,
) {
    fun format(): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(time))
        return buildString {
            append("[$ts] $algo  pkg=$pkg proc=$process tid=$thread\n")
            data?.let { append("  in  : ").append(bytesToHex(it, 256)).append('\n') }
            ret?.let { append("  out : ").append(bytesToHex(it, 256)).append('\n') }
            key?.let { append("  key : ").append(bytesToHex(it, 256)).append('\n') }
            iv?.let { append("  iv  : ").append(bytesToHex(it, 256)).append('\n') }
            if (stack.isNotBlank()) append("  --- stack ---\n").append(stack.lines().take(12).joinToString("\n")).append('\n')
            append('\n')
        }
    }
}

/**
 * 内存 + 文件双缓冲的日志仓库。UI 层直接读 [snapshot]。
 * 文件落盘在 cacheDir，避免污染用户可见目录。
 */
object MonitorLogStore {
    private val buffer = java.util.concurrent.CopyOnWriteArrayList<MonitorRecord>()
    private const val MAX = 500

    @Synchronized
    fun append(ctx: Context, r: MonitorRecord) {
        buffer.add(r)
        while (buffer.size > MAX) buffer.removeAt(0)
        runCatching {
            ctx.cacheDir.resolve("algorithm_monitor.log").appendText(r.format())
        }
    }

    fun snapshot(): List<MonitorRecord> = buffer.toList()

    @Synchronized
    fun clear() { buffer.clear() }
}
