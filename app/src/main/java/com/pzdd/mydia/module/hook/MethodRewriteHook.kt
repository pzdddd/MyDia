package com.pzdd.mydia.module.hook

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.rewrite.Rule
import com.pzdd.mydia.module.rewrite.RuleGroupDataStore
import com.pzdd.mydia.module.rewrite.Rewrite
import com.pzdd.mydia.module.rewrite.SmaliSignatureConverter
import com.pzdd.mydia.module.rewrite.bytesToHex
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 方法改写引擎——Dia 最硬核的功能。对应 dialog.box.hook.MethodRewriteHook。
 *
 * 用户在 App 端用 JSON 编辑一组 [Rule]，每条 Rule 定位一个方法 + 一组 [Rewrite]。
 * 本 Hook 负责：
 *  1. 从 SP 读规则（RuleGroupDataStore）
 *  2. 用 dexkit 或反射找到目标方法（按 className + methodName + 参数 smali 签名）
 *  3. 装钩子：before 改入参 / after 改返回值；可选打日志、dump hprof、method trace
 *  4. 支持多 dex：注册到 [MultiDexHook]，每个新 ClassLoader 加载时重跑一次查找
 *
 * 与 Dia 的差异：
 *  - Dia 用 dexlib2 + 自写 MethodFinder；这里 dexkit 为主、反射兜底
 *  - Dia 的 m14363() 值转换被 MTProtector 加密且反编译失败，这里用 Kotlin 重写（见 [Rewrite.apply]）
 *  - 简化了 hprof/trace/simulationPackageName 等边缘特性（保留开关，MVP 不全部实现）
 */
class MethodRewriteHook : DiaHook() {

    /** 当前激活、尚未成功 hook 的规则（hook 成功后从列表移除，避免重复 hook） */
    private val pending = CopyOnWriteArrayList<Rule>()

    override fun install() {
        if (!prefs.getBoolean("method_rewrite", false)) {
            Module.log("MethodRewriteHook: disabled (method_rewrite=false)")
            return
        }
        val groups = RuleGroupDataStore.load(prefs.getString(RuleGroupDataStore.KEY, null))
        val rules = RuleGroupDataStore.activeRules(groups)
        pending += rules
        Module.log("MethodRewriteHook: loaded ${rules.size} active rule(s)")

        // 先在主 ClassLoader 上试一轮（覆盖大部分情况）
        applyRulesOn(Module.classLoader!!)

        // 注册多 dex 监听：App 加载新 dex 时（multidex / 插件）再找一次
        MultiDexHook.addObserver { cl -> applyRulesOn(cl) }
        MultiDexHook.notifyCurrent(Module.classLoader!!)
    }

    private fun applyRulesOn(loader: ClassLoader) {
        if (pending.isEmpty()) return
        val it = pending.iterator()
        while (it.hasNext()) {
            val rule = it.next()
            if (tryHook(loader, rule)) {
                pending.remove(rule)
            }
        }
    }

    /** 尝试在 [loader] 上定位并 hook [rule]。成功返回 true */
    private fun tryHook(loader: ClassLoader, rule: Rule): Boolean {
        return runCatching {
            val targetClass = Class.forName(rule.className, false, loader)
                ?: return false
            // 参数类型数组：取所有改入参的 Rewrite 的 smali classType（去重保序）
            val paramTypes = rule.rewrites
                .filter { it.index >= 0 && it.classType.isNotBlank() }
                .sortedBy { it.index }
                .map { SmaliSignatureConverter.toClass(loader, it.classType) }
            if (paramTypes.any { it == null }) {
                Module.err("MethodRewriteHook: bad param type in rule ${rule.id}", IllegalArgumentException())
                return false
            }
            val callback = RewriteCallback(rule)
            val args = ArrayList<Any>(paramTypes.size + 1).apply {
                paramTypes.forEach { add(it as Any) }
                add(callback)
            }
            if (rule.isConstructor) {
                XposedHelpers.findAndHookConstructor(targetClass, *args.toTypedArray())
            } else {
                XposedHelpers.findAndHookMethod(targetClass, rule.methodName, *args.toTypedArray())
            }
            Module.log("MethodRewriteHook hook OK: ${rule.className}.${rule.methodName}${rule.signature} [${rule.id}]")
            true
        }.getOrElse { t ->
            // 类/方法在当前 ClassLoader 找不到属正常（多 dex 场景），降级到调试日志
            if (t is NoSuchMethodError || t is ClassNotFoundException || t is NoSuchMethodException) {
                // 安静跳过：等下一个 ClassLoader
                false
            } else {
                Module.err("MethodRewriteHook rule ${rule.id} failed", t)
                false
            }
        }
    }

    /**
     * 单条规则的钩子实现。before 改入参，after 改返回值 + 打日志。
     */
    private class RewriteCallback(val rule: Rule) : XC_MethodHook() {
        private val logBuf = java.util.concurrent.ConcurrentHashMap<MethodHookParam, StringBuilder>()

        override fun beforeHookedMethod(param: MethodHookParam) {
            // 1) 改入参
            for (rw in rule.rewrites) {
                if (rw.index >= 0 && rw.index < param.args.size) {
                    param.args[rw.index] = rw.apply(param.args[rw.index])
                } else if (rw.index < 0 && rule.bypass) {
                    // bypass=true 且有“改返回值”的 Rewrite：直接拦截、不执行原方法
                    param.setResult(rw.apply(null))
                    Module.log("MethodRewriteHook bypass: ${rule.id} setResult directly")
                    return
                }
            }
            // 2) 日志：记录入参
            if (rule.printLog) {
                val sb = StringBuilder("\n-----------------------------\n")
                    .append("[*] ENTER ").append(rule.methodName).append(rule.signature).append('\n')
                param.args.forEachIndexed { i, a -> sb.append("[+] in arg$i \n").append(describe(a)).append('\n') }
                logBuf[param] = sb
            }
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            // 1) 改返回值（index=-1 的 Rewrite）
            for (rw in rule.rewrites) {
                if (rw.index < 0) {
                    param.setResult(rw.apply(param.result))
                }
            }
            // 2) 日志：补出参与返回值
            if (rule.printLog) {
                val sb = logBuf.remove(param)
                if (sb != null) {
                    sb.append("[+] result \n").append(describe(param.result)).append('\n')
                    if (rule.printLogStackTrace) {
                        sb.append("[+] StackTrace:\n").append(android.util.Log.getStackTraceString(Throwable("rule ${rule.id}")))
                    }
                    sb.append("[*] EXIT ").append(rule.methodName).append(rule.signature).append('\n')
                    XposedBridge.log("[MyDia-Rewrite] $sb")
                }
            }
        }

        private fun describe(o: Any?): String = when (o) {
            null -> "null"
            is ByteArray -> "byte[] hex(${o.size}): ${bytesToHex(o, 240)}"
            is BooleanArray, is IntArray, is LongArray, is FloatArray, is DoubleArray ->
                o.toString()
            is String -> "String: $o"
            is Number, is Boolean -> "Primitive: $o"
            else -> "Object: $o"
        }
    }
}
