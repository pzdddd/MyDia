package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 方法级调用追踪（对齐 dialog.box.expand.trace.Trace + TraceDataStore）。
 *
 * 对指定类的方法做「进入/退出」追踪：记录方法名、入参、返回值、耗时，
 * 输出到 logcat（TAG=MyDia/Trace）。用于分析目标 App 关键方法的调用链。
 *
 * 与 [PrintMethodStackHook] 区别：那个只打印一次调用栈；这个对每个调用
 * 持续输出入参/出参/耗时，更适合跟踪协议/加解密调用序列。
 *
 * SP key：trace(总开关) / trace_list(每行「类名=方法名」或「类名」=全部方法)
 */
class TraceHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("trace", false)) return
        val spec = (prefs.getString("trace_list", "") ?: "")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (spec.isEmpty()) {
            Module.log("TraceHook: no targets, skip.")
            return
        }

        for (line in spec) {
            val idx = line.indexOf('=')
            val clsName = if (idx > 0) line.substring(0, idx).trim() else line.trim()
            val methodName = if (idx > 0) line.substring(idx + 1).trim() else null
            runCatching {
                val cls = XposedHelpers.findClass(clsName, classLoader)
                XposedBridge.hookAllMethods(cls, methodName ?: "<all>", object : XC_MethodHook() {
                    private val startTimes = java.util.concurrent.ConcurrentHashMap<MethodHookParam, Long>()

                    override fun beforeHookedMethod(param: MethodHookParam) {
                        startTimes[param] = System.currentTimeMillis()
                        val args = param.args.joinToString(", ") { a ->
                            when (a) {
                                null -> "null"
                                is ByteArray -> "byte[${a.size}]"
                                is String -> if (a.length > 100) "\"${a.take(100)}…\"" else "\"$a\""
                                else -> a.toString().take(100)
                            }
                        }
                        Module.log("Trace >>> ${param.method.name}($args)")
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val start = startTimes.remove(param) ?: 0L
                        val cost = System.currentTimeMillis() - start
                        val ret = when (val r = param.result) {
                            null -> "null"
                            is ByteArray -> "byte[${r.size}]"
                            is String -> if (r.length > 100) "\"${r.take(100)}…\"" else "\"$r\""
                            else -> r.toString().take(100)
                        }
                        Module.log("Trace <<< ${param.method.name} = $ret (${cost}ms)")
                    }
                })
                Module.log("TraceHook: hooked $line")
            }.onFailure { Module.err("TraceHook: hook $line failed", it) }
        }
    }
}
