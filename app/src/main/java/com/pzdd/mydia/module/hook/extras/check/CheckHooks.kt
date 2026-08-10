package com.pzdd.mydia.module.hook.extras.check

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.File

/**
 * 环境检测记录器（对齐 Dia check 包的统一入口）。
 *
 * 目标 App 的反检测逻辑会调用若干「探测 API」判断是否被 hook / root / 模拟器 /
 * 调试器。本类 hook 这些 API，App 一旦调用就输出 logcat 日志，帮你定位
 * 「App 在检测什么」——便于针对性用反检测 hook（hide/hide_xposed 等）对抗。
 *
 * 由 [EmulatorCheckHook] 等子类注册具体检测点，本类只做公共逻辑。
 *
 * SP key：check_env(总开关，由 CheckManager 读取)
 */
abstract class CheckHookBase : DiaHook() {

    protected fun report(tag: String, detail: String) {
        Module.log("Check[$tag] >>> $detail")
    }
}

/**
 * check 体系总入口。对应 Dia 的 CheckManager + check 包各 CheckHook。
 *
 * 汇总注册所有检测点：
 *  - PathCheckHook     文件/路径探测（su/magisk/xposed 路径 exists）
 *  - MagiskCheckHook   magisk 相关
 *  - FridaCheckHook    frida 相关
 *  - EmulatorCheckHook 模拟器特征
 *  - DDI/ABDI 检测     调试器/代理检测
 *
 * SP key：check_env(总开关)
 */
class CheckManager : DiaHook() {
    override fun install() {
        if (!prefs.getBoolean("check_env", false)) {
            Module.log("CheckManager: check_env disabled, skip")
            return
        }
        Module.log("CheckManager: registering environment-detection hooks")
        DiaHook.register(
            PathCheckHook::class.java,
            MagiskCheckHook::class.java,
            FridaCheckHook::class.java,
            EmulatorCheckHook::class.java,
            DdiCheckHook::class.java,
        )
    }
}

/** 文件/路径探测记录：App 检查 su/magisk/xposed 相关路径时打点。 */
class PathCheckHook : CheckHookBase() {

    private val probePaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/data/local/su", "/data/local/bin/su", "/system/app/Superuser.apk",
        "/sbin/.magisk", "/data/adb/magisk",
        "/data/local/tmp/frida-server", "/data/local/tmp/frida",
        "/data/data/de.robv.android.xposed.installer",
    )

    override fun install() {
        if (!prefs.getBoolean("check_env", false)) return
        XposedBridge.hookAllMethods(File::class.java, "exists", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val f = param.thisObject as? File ?: return
                val path = f.absolutePath
                if (path in probePaths && param.result == true) {
                    report("PathCheck", "exists=$path")
                }
            }
        })
        report("PathCheck", "hooked File.exists (${probePaths.size} probe paths)")
    }
}

/** magisk 检测记录：App 读 magisk 属性 / 进程时打点。 */
class MagiskCheckHook : CheckHookBase() {
    override fun install() {
        if (!prefs.getBoolean("check_env", false)) return
        runCatching {
            // SystemProperties 是 @hide 隐藏类，按名反射
            val sp = de.robv.android.xposed.XposedHelpers.findClass("android.os.SystemProperties", classLoader)
            XposedBridge.hookAllMethods(sp, "get", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args.getOrNull(0) as? String ?: return
                    if (key.contains("magisk") || key.contains("ro.debuggable") ||
                        key.contains("ro.secure")) {
                        report("MagiskCheck", "$key=${param.result}")
                    }
                }
            })
        }
    }
}

/** frida 检测记录：App 读 maps / 进程列表找 frida 时打点。 */
class FridaCheckHook : CheckHookBase() {
    override fun install() {
        if (!prefs.getBoolean("check_env", false)) return
        runCatching {
            XposedBridge.hookAllMethods(Runtime::class.java, "exec", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cmd = when (val a0 = param.args.getOrNull(0)) {
                        is String -> a0
                        is Array<*> -> a0.joinToString(" ")
                        else -> ""
                    }
                    if (cmd.contains("frida") || cmd.contains("grep") || cmd.contains("ps")) {
                        report("FridaCheck", "exec=$cmd")
                    }
                }
            })
        }
    }
}

/** 模拟器检测记录：App 读 Build 特征 / 传感器时打点。 */
class EmulatorCheckHook : CheckHookBase() {
    override fun install() {
        if (!prefs.getBoolean("check_env", false)) return
        report("EmulatorCheck", "监控 Build.* 读取（App 常查 generic/sdk/goldfish 特征）")
        // Build 字段读取属于高频调用，只在字段是模拟器特征时打点
        runCatching {
            XposedBridge.hookAllMethods(android.os.Build::class.java, "getString", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args.getOrNull(0) as? String ?: return
                    if (key.contains("ro.product") || key.contains("ro.kernel.qemu")) {
                        report("EmulatorCheck", "prop=$key=${param.result}")
                    }
                }
            })
        }
    }
}

/** 调试器/代理检测记录：App 查调试状态 / 代理设置时打点。 */
class DdiCheckHook : CheckHookBase() {
    override fun install() {
        if (!prefs.getBoolean("check_env", false)) return
        runCatching {
            XposedBridge.hookAllMethods(android.os.Debug::class.java, "isDebuggerConnected", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    report("DDICheck", "isDebuggerConnected=${param.result}")
                }
            })
        }
        runCatching {
            XposedBridge.hookAllMethods(System::class.java, "getProperty", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args.getOrNull(0) as? String ?: return
                    if (key.startsWith("http.proxy")) report("DDICheck", "proxy $key=${param.result}")
                }
            })
        }
    }
}
