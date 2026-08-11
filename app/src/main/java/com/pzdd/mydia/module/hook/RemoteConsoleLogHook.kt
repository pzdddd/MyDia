package com.pzdd.mydia.module.hook

import android.content.Context
import android.content.Intent
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 远程日志控制台。对应 Dia 的 RemoteConsoleLogHook + LogHook（悬浮控制台）。
 *
 * 把模块在目标 App 进程里的日志（Module.log → XposedBridge.log）通过广播回传
 * MyDia 进程，由 [com.pzdd.mydia.monitor.ConsoleLogReceiver] 存储，
 * UI 在「日志控制台」页查看——不需要 logcat 也能看到注入侧发生了什么。
 *
 *   目标 App 进程                              MyDia 进程
 *   ──────────────                            ──────────────
 *   Module.log / XposedBridge.log 被 hook
 *      │ 过滤 TAG=MyDia 的行
 *      └ sendBroadcast(ACTION_CONSOLE_LOG)
 *                  ─────────────────────────►  ConsoleLogReceiver → ConsoleLogStore
 *
 * SP key：log_console(总开关)
 */
class RemoteConsoleLogHook : DiaHook() {

    override fun install() {
        // 【关键】log_console 是全局开关（设置页写在 digXposed），必须读 globalPrefs——
        // 之前读 prefs(per-app SP) 永远 false，hook 从不安装，日志从未被拦截
        if (!globalPrefs.getBoolean("log_console", false)) return
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady { ctx ->
            hookBridge(ctx)
        } ?: Module.err("RemoteConsoleLogHook: ApplicationHook not registered", IllegalStateException())
    }

    private fun hookBridge(ctx: Context) {
        runCatching {
            // XposedBridge.log(String) 是模块日志的统一出口（Module.log 全部走这）
            XposedBridge.hookAllMethods(XposedBridge::class.java, "log", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val msg = param.args.getOrNull(0) as? String ?: return
                    if (!msg.contains("[MyDia]")) return
                    runCatching {
                        // 【关键】必须显式指定 component：Android 8+ manifest 静态注册的
                        // receiver 收不到其他 app 的隐式广播（之前日志全丢的根因）
                        ctx.sendBroadcast(Intent(ACTION).apply {
                            component = android.content.ComponentName(
                                "com.pzdd.mydia",
                                "com.pzdd.mydia.monitor.ConsoleLogReceiver"
                            )
                            putExtra(EXTRA_PACKAGE, ctx.packageName)
                            putExtra(EXTRA_MSG, msg)
                            putExtra(EXTRA_TIME, System.currentTimeMillis())
                        })
                    }
                }
            })
            // XposedBridge.log(Throwable) 错误日志
            XposedBridge.hookAllMethods(XposedBridge::class.java, "log", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val t = param.args.getOrNull(0) as? Throwable ?: return
                    runCatching {
                        ctx.sendBroadcast(Intent(ACTION).apply {
                            component = android.content.ComponentName(
                                "com.pzdd.mydia",
                                "com.pzdd.mydia.monitor.ConsoleLogReceiver"
                            )
                            putExtra(EXTRA_PACKAGE, ctx.packageName)
                            putExtra(EXTRA_MSG, "ERROR: ${t.message}")
                            putExtra(EXTRA_TIME, System.currentTimeMillis())
                        })
                    }
                }
            })
            Module.log("RemoteConsoleLogHook ACTIVE (relay to MyDia console).")
        }.onFailure { Module.err("RemoteConsoleLogHook install failed", it) }
    }

    companion object {
        const val ACTION = "com.pzdd.mydia.ACTION_CONSOLE_LOG"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_MSG = "message"
        const val EXTRA_TIME = "time"
    }
}
