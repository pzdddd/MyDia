package com.pzdd.mydia.module.hook

import android.content.Context
import android.content.Intent
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 监控目标 App 执行的 Shell 命令，并通过广播回传给 MyDia App 显示。
 *
 * 这是 Dia「监控类」功能的标准范式，对应 Dia 的 com.mhook.dialog.task.hook.RuntimeHook
 * + ShellMonitorReceiver：
 *
 *   目标 App 进程                              MyDia 进程
 *   ──────────────                            ──────────────
 *   Runtime.exec(cmd) 被 hook
 *      │ 收集 command / 输出
 *      └ sendBroadcast(ACTION_SHELL_MONITOR)
 *                  ─────────────────────────►  ShellMonitorReceiver
 *                                                → 这里你可接 RecyclerView 实时显示
 *
 * 需要 Context 才能 sendBroadcast，所以通过 ApplicationHook 等 App 就绪后再装。
 */
class ShellMonitorHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("shell_monitor", false)) return

        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady { ctx ->
            hookExec(ctx)
        } ?: Module.err("ShellMonitorHook: ApplicationHook not registered", IllegalStateException())
    }

    private fun hookExec(ctx: Context) {
        runCatching {
            // hook 两个重载：exec(String[]) 和 exec(String, String[], File)
            XposedBridge.hookAllMethods(
                Runtime::class.java,
                "exec",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val cmd = describeCommand(param.args)
                        Module.log("Shell exec captured: $cmd")
                        runCatching {
                            val i = Intent(ACTION).apply {
                                putExtra(EXTRA_PACKAGE, ctx.packageName)
                                putExtra(EXTRA_COMMAND, cmd)
                                // 注：Process 对象无法直接序列化，这里只回传命令；
                                // 真实实现可在 hook 里把输出读完再广播（会阻塞，建议起子线程）。
                            }
                            ctx.sendBroadcast(i)
                        }.onFailure { Module.err("broadcast shell failed", it) }
                    }
                }
            )
            Module.log("ShellMonitorHook ACTIVE.")
        }.onFailure { Module.err("ShellMonitorHook install failed", it) }
    }

    /** 从 exec 的参数里尽量还原命令字符串 */
    private fun describeCommand(args: Array<Any?>): String {
        val a = args.firstOrNull()
        return when (a) {
            is String -> a
            is Array<*> -> a.joinToString(" ") { it.toString() }
            else -> args.joinToString(" ") { it?.toString() ?: "null" }
        }
    }

    companion object {
        // 与 monitor.ShellMonitorReceiver 保持一致
        const val ACTION = "com.pzdd.mydia.ACTION_SHELL_MONITOR"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_COMMAND = "command"
    }
}
