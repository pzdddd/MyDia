package com.pzdd.mydia.ui

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 目标 App 的进程控制工具（启动 / 停止 / 重启）。
 *
 * - **启动**：用 Android 标准 API（`getLaunchIntentForPackage` + `startActivity`），
 *   不需要 root，也不需要命令行（`sh -c "am start"` 在 App 进程里不可靠，`su` 还会
 *   因授权对话框/输出为空导致误判）。
 * - **停止 / 重启**：`am force-stop` 需要 FORCE_STOP_PACKAGES 权限（第三方 App 没有），
 *   必须 Root。用退出码判断成功（force-stop 成功时无输出，不能按输出内容判断）。
 *
 * 用户可在配置页菜单里切换「使用 Root 执行」（per-app 开关 app_control_root）。
 */
object AppController {

    /**
     * 启动目标 App（无需 Root）。
     * 优先 launcher intent，fallback 到包内第一个可启动 activity。
     */
    suspend fun launch(context: Context, pkg: String): String = withContext(Dispatchers.Main) {
        val pm = context.packageManager
        // 1) 标准 launcher（绝大多数 App 都有）
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent != null) return@withContext tryStart(context, intent, pkg)
        // 2) fallback：包内 MAIN action 的第一个 activity（无 launcher 的 App）
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { setPackage(pkg) }
        val ri = runCatching { pm.resolveActivity(mainIntent, 0) }.getOrNull()
        if (ri != null) {
            return@withContext tryStart(context, Intent(Intent.ACTION_MAIN).apply {
                setClassName(pkg, ri.activityInfo.name)
            }, pkg)
        }
        "启动失败：$pkg 没有可启动的入口"
    }

    private fun tryStart(context: Context, intent: Intent, pkg: String): String {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "已启动 $pkg"
        } catch (e: Exception) {
            "启动失败：${e.message}"
        }
    }

    /** 停止目标 App（am force-stop，需要 Root）。 */
    suspend fun stop(pkg: String, useRoot: Boolean): String = withContext(Dispatchers.IO) {
        if (!useRoot) return@withContext "停止需要 Root（请先开启菜单里的「使用 Root 执行」）"
        val r = exec("am force-stop $pkg", useRoot)
        when {
            r.exitCode == 0 -> "已停止 $pkg"
            r.output.contains("Permission") || r.output.contains("SecurityException") ->
                "停止无权限：${r.output}"
            else -> "停止失败（exit=${r.exitCode}）\n${r.output}"
        }
    }

    /** 重启目标 App（force-stop + 重新启动，需要 Root 停止）。 */
    suspend fun restart(context: Context, pkg: String, useRoot: Boolean): String = withContext(Dispatchers.IO) {
        if (!useRoot) return@withContext "重启需要 Root（请先开启菜单里的「使用 Root 执行」）"
        val r = exec("am force-stop $pkg", useRoot)
        if (r.exitCode != 0) {
            return@withContext "重启失败（force-stop 退出码 ${r.exitCode}）\n${r.output}"
        }
        Thread.sleep(400) // 等进程完全退出
        // 启动用标准 API（无需 root）
        withContext(Dispatchers.Main) { launch(context, pkg) }
    }

    /** 命令执行结果。 */
    private data class ExecResult(val exitCode: Int, val output: String)

    /**
     * 执行命令：Root 用 `su -c`（Magisk 授权后直接执行，无授权对话框阻塞）。
     * 用线程分别读 stdout/stderr 防管道阻塞，返回退出码 + 合并输出。
     */
    private fun exec(cmd: String, useRoot: Boolean): ExecResult {
        return try {
            val process = if (useRoot) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            }
            val sb = StringBuilder()
            val t1 = Thread {
                runCatching { process.inputStream.bufferedReader().forEachLine { sb.append(it).append('\n') } }
            }.also { it.start() }
            val t2 = Thread {
                runCatching { process.errorStream.bufferedReader().forEachLine { sb.append(it).append('\n') } }
            }.also { it.start() }
            val code = process.waitFor()
            t1.join(2000)
            t2.join(2000)
            ExecResult(code, sb.toString().trim())
        } catch (t: Throwable) {
            ExecResult(-1, t.message ?: "exec error: ${t.javaClass.simpleName}")
        }
    }
}
