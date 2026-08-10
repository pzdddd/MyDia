package com.pzdd.mydia.module.hook.extras

import android.os.Build
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File

/**
 * 隐藏 Root 痕迹。对应 Dia 反检测的 hide 开关（Dia 原类是空壳，逻辑加密）。
 *
 * 反 Root 检测的常见手段 & 本 hook 的应对：
 *  1. Runtime.exec("su") / exec("which su") 探测
 *     → hook Runtime.exec，命令含 su 时抛 IOException
 *  2. File("/system/xbin/su").exists() 等一堆路径探测
 *     → hook File.exists，命中 su/busybox/magisk 路径返回 false
 *  3. Build.TAGS = "test-keys"（工程机特征）
 *     → 直接 setStaticObjectField 改成 "release-keys"
 *  4. /system/app/Superuser.apk、Magisk 文件等
 *     → 同 File.exists 过滤
 *
 * SP key：hide(总开关)
 */
class HideRootHook : DiaHook() {

    /** 反检测常扫的 su/busybox/magisk 路径 */
    private val suPaths = listOf(
        "/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su",
        "/system/sbin/su", "/vendor/bin/su", "/system/xbin/busybox",
        "/system/bin/busybox", "/system/app/Superuser.apk",
        "/data/adb/magisk", "/sbin/.magisk", "/data/adb/modules"
    )

    override fun install() {
        if (!prefs.getBoolean("hide", false)) return

        // 1. Build.TAGS 去 test-keys
        if (Build.TAGS?.contains("test-keys") == true) {
            runCatching { XposedHelpers.setStaticObjectField(Build::class.java, "TAGS", "release-keys") }
        }

        // 2. Runtime.exec 拦截 su/which
        runCatching {
            XposedBridge.hookAllMethods(Runtime::class.java, "exec", object : MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val cmd = param.args.getOrNull(0)?.toString() ?: return
                    if (cmd.contains("su") || cmd.contains("busybox") || cmd.contains("magisk") ||
                        cmd.contains("which")) {
                        param.throwable = java.io.IOException("Permission denied")
                        Module.log("HideRoot: blocked exec '$cmd'")
                    }
                }
            })
        }

        // 3. File.exists 过滤 su 路径
        runCatching {
            XposedBridge.hookAllMethods(File::class.java, "exists", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    if (param.result != true) return
                    val f = param.thisObject as? File ?: return
                    val p = f.absolutePath.lowercase()
                    if (suPaths.any { p.contains(it.lowercase()) } ||
                        p.contains("supersu") || p.contains("magisk")) {
                        param.result = false
                        Module.log("HideRoot: File.exists($p) -> false")
                    }
                }
            })
        }
        Module.log("HideRoot: installed")
    }
}
