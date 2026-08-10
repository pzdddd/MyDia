package com.pzdd.mydia.module.hook.extras

import android.view.View
import android.view.WindowManager
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 窗口操作监控 / 拦截。对应 Dia 的 WindowManagerHook。
 *
 * 监控 App 的窗口添加/移除（悬浮窗、弹层、状态栏覆盖等）：
 *  - 记录 addView 的 view 类型 + 窗口参数（logcat）
 *  - 可配置黑名单：命中 view 类名则阻止添加（拦截特定悬浮窗）
 *
 * SP key：window_monitor(总开关) / window_block(要阻止的 view 类名，空格分隔)
 */
class WindowManagerHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("window_monitor", false)) return
        val block = (prefs.getString("window_block", "") ?: "")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        XposedBridge.hookAllMethods(WindowManager::class.java, "addView", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.args.getOrNull(0) as? View ?: return
                val clsName = view.javaClass.name
                Module.log("WindowManagerHook addView: $clsName")
                if (block.any { clsName.startsWith(it) }) {
                    param.result = null
                    Module.log("WindowManagerHook: blocked window $clsName")
                }
            }
        })
        runCatching {
            XposedBridge.hookAllMethods(WindowManager::class.java, "removeView", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.args.getOrNull(0) as? View ?: return
                    Module.log("WindowManagerHook removeView: ${view.javaClass.name}")
                }
            })
        }

        Module.log("WindowManagerHook ACTIVE (block=${block.size} patterns).")
    }
}
