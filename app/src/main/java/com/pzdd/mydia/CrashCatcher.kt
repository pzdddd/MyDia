package com.pzdd.mydia

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常捕获器。
 *
 * 作用：App 闪退时把崩溃栈写到 [CRASH_FILE]，下次启动 [android.app.Application.onCreate]
 * 里读取并（通过 SP 传递给 UI）展示，便于在没有 logcat 的环境下定位闪退原因。
 *
 * 用法：在 [App.onCreate] 里 `CrashCatcher.install(this)`。
 */
object CrashCatcher {

    /** 崩溃栈文件（/data/data/com.pzdd.mydia/files/crash.log）。 */
    const val CRASH_FILE = "crash.log"

    /** SP key：UI 侧读取「上次崩溃」用。 */
    const val SP_KEY_LAST_CRASH = "last_crash"

    private const val MAX_LEN = 16_000 // 防 SP 过大

    private var installed = false

    /**
     * 安装全局未捕获异常处理器。
     * 必须在 Application.onCreate 最早期调用，才能捕获到所有线程的崩溃。
     */
    fun install(context: Context) {
        if (installed) return
        installed = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 1) 写文件（即使后续 UI 无响应也保留）
            runCatching {
                val trace = formatTrace(thread.name, throwable)
                File(context.filesDir, CRASH_FILE).writeText(trace)
                // 2) 同步到 SP（UI 侧读取更方便）
                // MODE_PRIVATE（Android 13+ 禁止 WORLD_READABLE 模式）；注入侧不读 last_crash
                context.getSharedPreferences("digXposed", Context.MODE_PRIVATE)
                    .edit()
                    .putString(SP_KEY_LAST_CRASH, trace.take(MAX_LEN))
                    .apply()
            }
            // 3) 交给系统默认处理器（让进程正常终止，弹"应用已停止"）
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 读取并清除上次崩溃栈（UI 侧调用：显示后清空，避免重复弹）。 */
    fun consume(context: Context): String? {
        val sp = context.getSharedPreferences("digXposed", Context.MODE_PRIVATE)
        val crash = sp.getString(SP_KEY_LAST_CRASH, null)
        if (crash != null) {
            sp.edit().remove(SP_KEY_LAST_CRASH).apply()
            // 文件也清理
            runCatching { File(context.filesDir, CRASH_FILE).delete() }
        }
        return crash
    }

    private fun formatTrace(threadName: String, t: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("=== MyDia Crash Report ===")
            pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}")
            pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println("Android API: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            pw.println("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            pw.println("Thread: $threadName")
            pw.println()
            t.printStackTrace(pw)
        }
        return sw.toString()
    }

    private fun String.take(max: Int): String = if (length <= max) this else substring(0, max)
}
