package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 类加载监控。对应 Dia 的 ClassLoaderHook + ClassLoaderWrapper。
 *
 * 监控 / 拦截类加载：
 *  - 记录 `ClassLoader.loadClass` 的目标类名（logcat）
 *  - 可配置黑名单：命中则抛 ClassNotFoundException（App 查不到这些类）
 *
 * SP key：class_loader_monitor(总开关) / class_loader_hide(要隐藏的类名前缀，空格分隔)
 */
class ClassLoaderHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("class_loader_monitor", false)) return
        val hide = (prefs.getString("class_loader_hide", "") ?: "")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        XposedBridge.hookAllMethods(ClassLoader::class.java, "loadClass", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val name = param.args.getOrNull(0) as? String ?: return
                if (hide.isNotEmpty() && hide.any { name.startsWith(it) }) {
                    param.throwable = ClassNotFoundException(name)
                    Module.log("ClassLoaderHook: hidden class $name")
                    return
                }
                if (hide.isEmpty()) {
                    // 纯监控模式：只记录目标类
                    Module.log("ClassLoaderHook loadClass: $name")
                }
            }
        })

        Module.log("ClassLoaderHook ACTIVE (hide=${hide.size} patterns).")
    }
}
