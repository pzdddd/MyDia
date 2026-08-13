package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.File

/**
 * 隐藏敏感文件 / 目录。对应 Dia 的 FileHook。
 *
 * 有些 App 检查特定路径是否存在（su 可执行文件、Xposed 相关文件、busybox 等）
 * 来判断设备是否被 root / 被 Xposed。本 hook 让这些文件「看起来不存在」：
 *  - `File.exists()` → false
 *  - `File.isFile()` / `isDirectory()` → false
 *  - `File.canRead()` / `canExecute()` / `canWrite()` → false
 *  - `File.list()` / `listFiles()` → 过滤掉命中项
 *  - `File.length()` → 0
 *
 * 内置默认隐藏列表（对齐 Dia FileHook 的 su / xposedbridge.jar / libxposed_art.so /
 * xposed.prop / installer 目录），可用 file_hide_list 追加自定义路径。
 *
 * SP key：file_hide(总开关) / file_hide_list(自定义路径，空格分隔)
 */
class FileHook : DiaHook() {

    private val defaultHidden = listOf(
        "su", "busybox",
        "xposedbridge.jar", "libxposed_art.so", "xposed.prop",
        "de.robv.android.xposed.installer",
        "/data/adb/magisk", "/sbin/.magisk", "/data/adb/modules",
        "/data/adb/zygisk", "/debug_ramdisk", "magisk.db",
        "/data/adb/shamiko", "/data/adb/modules/zygisksu",
        "org.lsposed.manager", "org.meowcat.edxposed",
        "Superuser.apk", "superuser.apk",
    )

    override fun install() {
        if (!prefs.getBoolean("file_hide", false)) return
        val extra = (prefs.getString("file_hide_list", "") ?: "")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val hidden = defaultHidden + extra

        fun pathMatches(path: String): Boolean =
            hidden.any { path.contains(it) }

        // 布尔查询：伪装成不存在
        val falseResult = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val f = param.thisObject as? File ?: return
                if (pathMatches(f.absolutePath)) param.result = false
            }
        }
        runCatching { XposedBridge.hookAllMethods(File::class.java, "exists", falseResult) }
        runCatching { XposedBridge.hookAllMethods(File::class.java, "isFile", falseResult) }
        runCatching { XposedBridge.hookAllMethods(File::class.java, "isDirectory", falseResult) }
        runCatching { XposedBridge.hookAllMethods(File::class.java, "canRead", falseResult) }
        runCatching { XposedBridge.hookAllMethods(File::class.java, "canWrite", falseResult) }
        runCatching { XposedBridge.hookAllMethods(File::class.java, "canExecute", falseResult) }

        // length：命中返回 0
        runCatching {
            XposedBridge.hookAllMethods(File::class.java, "length", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val f = param.thisObject as? File ?: return
                    if (pathMatches(f.absolutePath)) param.result = 0L
                }
            })
        }

        // list：过滤命中项（返回 Array<String>）
        runCatching {
            XposedBridge.hookAllMethods(File::class.java, "list", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val r = param.result as? Array<String> ?: return
                    param.result = r.filter { name -> hidden.none { name.contains(it) } }.toTypedArray()
                }
            })
        }
        // listFiles：过滤命中项（返回 Array<File>）
        runCatching {
            XposedBridge.hookAllMethods(File::class.java, "listFiles", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val r = param.result as? Array<File> ?: return
                    param.result = r.filter { f -> hidden.none { f.name.contains(it) } }.toTypedArray()
                }
            })
        }

        Module.log("FileHook ACTIVE (hide ${hidden.size} patterns).")
    }
}
