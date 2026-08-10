package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 隐藏 Xposed 痕迹。对应 Dia 的 HideXposedHook（原类是空壳，逻辑在加密的 otherModEx 里）。
 *
 * 反 Xposed 检测的常见手段 & 本 hook 的应对：
 *  1. 遍历 stack trace 找 de.robv.android.xposed / com.android.xposed 类
 *     → hook [Thread.getStackTrace] / [Throwable.getStackTrace]，过滤掉含 xposed 关键字的帧
 *  2. ClassLoader.loadClass("de.robv.android.xposed.XposedBridge") 探测
 *     → hook loadClass，遇 xposed 包名抛 ClassNotFoundException
 *  3. PackageManager 查 xposed installer / LSPosed manager 是否安装
 *     → hook getInstalledPackages / getPackageInfo，过滤掉 xposed 系包名
 *  4. 检查 /system/bin/dexopt 等环境变量、native 探测
 *     → 本骨架暂不处理 native 探测（需原生模块）
 *
 * SP key：hide_xposed(总开关)
 */
class HideXposedHook : DiaHook() {

    private val fingerprints = listOf(
        "de.robv.android.xposed", "com.android.xposed",
        "org.lsposed.manager", "org.meowcat.edxposed",
        "de.robv.xposed", "xposedbridge"
    )

    override fun install() {
        if (!prefs.getBoolean("hide_xposed", false)) return

        // 1. 过滤 stack trace
        runCatching {
            XposedBridge.hookAllMethods(Thread::class.java, "getStackTrace", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val trace = param.result as? Array<*> ?: return
                    val filtered = trace.filter { st ->
                        val s = st.toString()
                        fingerprints.none { s.contains(it) }
                    }.toTypedArray()
                    param.result = filtered
                }
            })
        }
        runCatching {
            XposedBridge.hookAllMethods(Throwable::class.java, "getStackTrace", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    @Suppress("UNCHECKED_CAST")
                    val trace = param.result as? Array<StackTraceElement> ?: return
                    param.result = trace.filter { st ->
                        fingerprints.none { st.className.contains(it) }
                    }.toTypedArray()
                }
            })
        }

        // 2. loadClass 拦截
        runCatching {
            XposedBridge.hookAllMethods(ClassLoader::class.java, "loadClass", object : MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val name = param.args.getOrNull(0) as? String ?: return
                    if (fingerprints.any { name.startsWith(it) }) {
                        param.throwable = ClassNotFoundException(name)
                    }
                }
            })
        }

        // 3. PackageManager 隐藏 xposed 系 App
        runCatching {
            val pm = Class.forName("android.app.ApplicationPackageManager")
            XposedBridge.hookAllMethods(pm, "getInstalledPackages", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val list = param.result as? MutableList<*> ?: return
                    list.removeAll { it.toString().contains("xposed", ignoreCase = true) ||
                                     it.toString().contains("edxposed", ignoreCase = true) ||
                                     it.toString().contains("lsposed", ignoreCase = true) }
                }
            })
            XposedBridge.hookAllMethods(pm, "getPackageInfo", object : MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val pkg = param.args.getOrNull(0) as? String ?: return
                    if (fingerprints.any { pkg.contains(it) }) {
                        param.throwable = Class.forName("android.content.pm.PackageManager\$NameNotFoundException").newInstance() as Throwable
                    }
                }
            })
        }
        Module.log("HideXposed: installed")
    }
}
