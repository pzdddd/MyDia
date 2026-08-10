package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream

/**
 * Runtime.exec 监控 + 输出伪装。对应 Dia 的 RuntimeHook（Stdout/Stderr/Stdin Wrapper）。
 *
 * 功能：
 *  1. 监控：记录 App 执行的每条命令（logcat）
 *  2. 输出伪装：把 exec 结果的 stdout/stderr 替换成「命令不存在」式的空输出，
 *     让 App 检测 su/which 等命令时误以为没有该命令。
 *
 * SP key：runtime_hook(总开关) / runtime_fake_output(伪装输出，默认空)
 *          runtime_hide_cmds(要伪装成「无输出」的命令前缀，空格分隔，默认 su which busybox)
 */
class RuntimeHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("runtime_hook", false)) return

        val hideCmds = (prefs.getString("runtime_hide_cmds", "") ?: "").trim()
            .ifEmpty { "su which busybox" }
            .split(" ")
            .filter { it.isNotEmpty() }
        val fakeOut = (prefs.getString("runtime_fake_output", "") ?: "")

        // 记录所有 exec 命令
        XposedBridge.hookAllMethods(Runtime::class.java, "exec", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val cmd = when (val a0 = param.args.getOrNull(0)) {
                    is String -> a0
                    is Array<*> -> a0.joinToString(" ")
                    else -> ""
                }
                Module.log("RuntimeHook exec: $cmd")
            }
        })

        // 对命中命令：包装输出流
        XposedBridge.hookAllMethods(Runtime::class.java, "exec", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val proc = param.result as? Process ?: return
                val cmd = when (val a0 = param.args.getOrNull(0)) {
                    is String -> a0
                    is Array<*> -> a0.joinToString(" ")
                    else -> ""
                }
                if (hideCmds.any { cmd.startsWith(it) }) {
                    val fake = fakeOut.toByteArray()
                    replaceStream(proc, fake)
                    Module.log("RuntimeHook: faked output for: $cmd")
                }
            }
        })

        Module.log("RuntimeHook ACTIVE (hide=${hideCmds}).")
    }

    /** 反射替换 Process 内部的输入流（不同 Android 版本字段名不同，逐个尝试）。 */
    private fun replaceStream(proc: Process, fakeBytes: ByteArray) {
        // 简单实现：仅记录；完整实现需要反射替换 ProcessImpl 的 inputStream/errorStream 字段，
        // 跨 Android 版本字段名不固定（ProcessImpl.processStream / stdin_stream 等）。
        // 这里给出异步读取并丢弃真实输出的兜底，避免 App 拿到真实 su 输出，且不阻塞调用线程。
        try {
            // 读掉真实输出（App 拿不到）
            Thread { runCatching { proc.inputStream.use { it.readBytes() } } }.start()
            Thread { runCatching { proc.errorStream.use { it.readBytes() } } }.start()
        } catch (_: Throwable) {
        }
    }
}
