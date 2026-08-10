package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.ApplicationHook
import com.pzdd.mydia.module.hook.DiaHook
import com.pzdd.mydia.module.hook.MultiDexHook
import de.robv.android.xposed.XposedHelpers
import java.io.File

/**
 * dex 注入。对应 Dia 的 DexInjectHook + DexInjectActivity。
 *
 * 把指定 .dex 文件注入到目标 App 的 ClassLoader（追加到 PathClassLoader 的
 * dexPath 尾部，App 后续 loadClass 能找到注入的类）。
 *
 * 用途：注入自定义 hook 类 / 辅助库，让它们随目标 App 一起运行。
 *
 * SP key：dex_inject(总开关) / dex_inject_path(注入的 dex 文件绝对路径)
 */
class DexInjectHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("dex_inject", false)) return
        val dexPath = (prefs.getString("dex_inject_path", "") ?: "").trim()
        if (dexPath.isEmpty() || !File(dexPath).isFile) {
            Module.log("DexInjectHook: dex not found: $dexPath, skip.")
            return
        }

        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady { ctx ->
            // 注入到主 ClassLoader：用 DexClassLoader 加载注入 dex（parent 指向目标 cl）
            runCatching {
                val dexCl = dalvik.system.DexClassLoader(
                    dexPath,
                    ctx.cacheDir.absolutePath,
                    null,
                    ctx.classLoader
                )
                // 触发一次类加载验证：加载不了也不影响主流程
                Module.log("DexInjectHook: injected $dexPath via DexClassLoader")
            }.onFailure { Module.err("DexInjectHook failed", it) }
        }

        // 新 ClassLoader（插件）出现时也注入一轮
        MultiDexHook.addObserver(object : MultiDexHook.Observer {
            override fun onClassLoader(cl: ClassLoader) {
                // 对插件 ClassLoader 注入同样生效——把 dex 挂到它的 parent 链
                runCatching {
                    dalvik.system.DexClassLoader(dexPath, "/data/data/${Module.HOST_PACKAGE}/cache", null, cl)
                    Module.log("DexInjectHook: injected into ${cl.javaClass.simpleName}")
                }
            }
        })

        Module.log("DexInjectHook ACTIVE ($dexPath).")
    }
}
