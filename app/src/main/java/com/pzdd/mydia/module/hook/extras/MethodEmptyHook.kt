package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 方法置空。对应 Dia 的 method / method_empty（让指定方法变成空方法，不执行原逻辑）。
 *
 * 用途：禁掉 App 里某些不想执行的方法（如广告 SDK 的初始化、上报、检测方法），
 * 找到方法后 beforeHookedMethod 直接 setResult(null)，原方法体不执行。
 *
 * 配置（method_empty key）格式：每行一个方法签名，支持：
 *  - 全限定名: com.foo.Bar.init(Landroid/content/Context;)V
 *  - 简单类名+方法（用 dexkit 模糊匹配，本骨架简化为精确类名）
 *
 * 注意：和 MethodRewriteHook 不同 —— 那个是改返回值，这个是直接置空（不执行+返回默认）。
 *
 * SP key：method(总开关) / method_empty(方法签名列表，每行一个)
 */
class MethodEmptyHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("method", false)) return
        val text = prefs.getString("method_empty", "") ?: ""
        if (text.isEmpty()) return

        var count = 0
        for (line in text.lines()) {
            val sig = line.trim()
            if (sig.isEmpty()) continue
            runCatching {
                val parsed = parseSignature(sig) ?: return@runCatching
                val cls = Class.forName(parsed.className)
                XposedBridge.hookAllMethods(cls, parsed.methodName, object : MethodHook() {
                    override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        param.result = null  // 置空，不执行原方法
                    }
                })
                count++
                Module.log("MethodEmpty: hooked $sig")
            }.onFailure { Module.log("MethodEmpty: failed $sig: ${it.message}") }
        }
        Module.log("MethodEmpty: total $count methods emptied")
    }

    /** 解析 "com.foo.Bar.method(params)ret" → 取类名和方法名 */
    private data class Sig(val className: String, val methodName: String)
    private fun parseSignature(sig: String): Sig? {
        val paren = sig.indexOf('(')
        if (paren < 0) return null
        val head = sig.substring(0, paren)  // com.foo.Bar.method
        val dot = head.lastIndexOf('.')
        if (dot < 0) return null
        return Sig(head.substring(0, dot), head.substring(dot + 1))
    }
}
