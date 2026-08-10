package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 调用栈隐藏。对应 Dia 的 StackHook（隐藏指定类的调用痕迹）。
 *
 * 场景：App 的检测逻辑里 `Thread.currentThread().getStackTrace()` 检查调用来源，
 * 发现自己的方法是被 Xposed 模块的 hook 代码调用的（栈里有模块类名）就判定被 hook。
 * 本 hook 从 [StackTraceElement] 数组里过滤掉模块包名的帧。
 *
 * SP key：stack_filter(总开关) / stack_filter_pkg(要过滤的包名前缀，空格分隔)
 */
class StackHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("stack_filter", false)) return
        val pkgs = (prefs.getString("stack_filter_pkg", "") ?: "")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (pkgs.isEmpty()) {
            Module.log("StackHook: no packages to filter, skip.")
            return
        }

        val filterHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val frames = param.result as? Array<*> ?: return
                val kept = frames.filter { f ->
                    val cn = (f as? StackTraceElement)?.className ?: return@filter false
                    pkgs.none { cn.startsWith(it) }
                }
                param.result = kept.toTypedArray()
            }
        }
        runCatching { XposedBridge.hookAllMethods(Thread::class.java, "getStackTrace", filterHook) }
        // Throwable.getStackTrace 也是常用入口
        runCatching { XposedBridge.hookAllMethods(Throwable::class.java, "getStackTrace", filterHook) }

        Module.log("StackHook ACTIVE (filter $pkgs).")
    }
}
