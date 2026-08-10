package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 方法调用栈打印。对应 Dia 的 PrintMethodStackHook + dev/PrintMethodStackHook。
 *
 * 开发者工具：把指定类的指定方法每次调用都打印调用栈到 logcat，
 * 用于定位「谁调用了这个方法」。
 *
 * SP key：print_stack(总开关) / print_stack_list(每行「类名=方法名」，或「类名」=全部方法)
 */
class PrintMethodStackHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("print_stack", false)) return
        val spec = (prefs.getString("print_stack_list", "") ?: "")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (spec.isEmpty()) {
            Module.log("PrintMethodStackHook: no targets, skip.")
            return
        }

        val stackPrinter = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val stack = Thread.currentThread().stackTrace
                    .joinToString("\n") { "    at $it" }
                Module.log("PrintMethodStackHook: called ${param.method}\n$stack")
            }
        }

        for (line in spec) {
            val idx = line.indexOf('=')
            val clsName = if (idx > 0) line.substring(0, idx).trim() else line.trim()
            val methodName = if (idx > 0) line.substring(idx + 1).trim() else null
            runCatching {
                val cls = XposedHelpers.findClass(clsName, classLoader)
                if (methodName.isNullOrEmpty()) {
                    XposedBridge.hookAllConstructors(cls, stackPrinter)
                } else {
                    XposedBridge.hookAllMethods(cls, methodName, stackPrinter)
                }
                Module.log("PrintMethodStackHook: hooked $line")
            }.onFailure { Module.err("PrintMethodStackHook: hook $line failed", it) }
        }
    }
}
