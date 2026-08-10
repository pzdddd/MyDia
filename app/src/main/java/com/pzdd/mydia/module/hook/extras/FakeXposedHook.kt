package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Xposed 存在性伪装。对应 Dia 的 FakeXposedHook。
 *
 * 有些 App 通过反射检查 Xposed 相关的类（de.robv.android.xposed.XposedBridge 等）
 * 是否存在来判断是否被 hook。本 hook 拦截 ClassLoader.loadClass：
 *  - `de.robv.android.xposed.*` 类 → 抛 ClassNotFoundException
 *  - 但保留本模块自身运行所需的调用（由 [Module] 的类加载器绕过）
 *
 * 注意：Xposed 类本身由框架加载，App 内 `Class.forName("de.robv...")` 一定会成功
 * （类在 boot classpath / 模块 classpath）。所以更有效的做法是 hook
 * `Class.forName` + `ClassLoader.loadClass` 并拦截返回 Xposed 包名的类。
 * 这里对 Class.forName 的处理与 HideXposedHook 的栈过滤配合。
 *
 * SP key：fake_xposed(总开关)
 */
class FakeXposedHook : DiaHook() {

    private val xposedPrefix = "de.robv.android.xposed."

    override fun install() {
        if (!prefs.getBoolean("fake_xposed", false)) return

        // 拦截 ClassLoader.loadClass 返回 Xposed 类时抛 CNFE
        XposedBridge.hookAllMethods(ClassLoader::class.java, "loadClass", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val name = param.args.getOrNull(0) as? String ?: return
                if (name.startsWith(xposedPrefix)) {
                    param.result = null
                    param.throwable = ClassNotFoundException(name)
                }
            }
        })

        // 拦截 XposedBridge.log 等（App 若拿到 XposedBridge 实例调用）
        runCatching {
            val bridge = XposedHelpers.findClass("de.robv.android.xposed.XposedBridge", classLoader)
            XposedBridge.hookAllMethods(bridge, "log", XC_MethodReplacement.DO_NOTHING)
        }

        Module.log("FakeXposedHook ACTIVE.")
    }
}
