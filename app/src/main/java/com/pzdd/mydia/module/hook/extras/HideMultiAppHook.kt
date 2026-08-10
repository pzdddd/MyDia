package com.pzdd.mydia.module.hook.extras

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 隐藏多开/分身痕迹。对应 Dia 反检测的 hide_multi_app。
 *
 * 多开 App（平行空间、双开助手等）的检测原理：App 的进程名/包名/文件路径会落在
 * 多开容器的特征目录下（如 com.lbe.parallel、com.excelliance.dualaid）。目标 App
 * 通过 PackageManager / ActivityManager 查询到这些包，就知道自己被多开了。
 *
 * 本 hook 三个拦截点：
 *  1. `getInstalledApplications/getInstalledPackages` → 从结果里移除多开容器包
 *  2. `getPackageInfo/getApplicationInfo` → 查询容器包时抛 NameNotFound
 *  3. `getRunningAppProcesses` → 过滤进程名含容器特征的进程
 *
 * SP key：hide_multi_app(总开关) / hide_multi_app_select(额外容器包名，逗号分隔)
 */
class HideMultiAppHook : DiaHook() {

    private val defaultContainers = listOf(
        "com.lbe.parallel", "com.excelliance.dualaid", "com.ludashi.dualspace",
        "com.bly.dkplat", "com.jumobile.multiapp", "com.google.android.parallel",
        "com.parallel.space.lite", "com.qihoo.magic", "com.vphone.helper",
    )

    override fun install() {
        if (!prefs.getBoolean("hide_multi_app", false)) return

        val extra = (prefs.getString("hide_multi_app_select", "") ?: "")
            .split(",", "，", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val hidden = defaultContainers + extra

        // 1) 已安装应用列表：移除容器包（hook 基类，覆盖 ApplicationPackageManager 子类实现）
        runCatching {
            XposedBridge.hookAllMethods(PackageManager::class.java, "getInstalledApplications", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.result as? MutableList<*> ?: return
                    list.removeAll { (it as? ApplicationInfo)?.packageName in hidden }
                }
            })
        }
        runCatching {
            XposedBridge.hookAllMethods(PackageManager::class.java, "getInstalledPackages", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.result as? MutableList<*> ?: return
                    list.removeAll { (it as? PackageInfo)?.packageName in hidden }
                }
            })
        }

        // 2) 查询容器包信息：伪装成未安装
        val notFound = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val pkg = param.args.getOrNull(0) as? String ?: return
                if (pkg in hidden) {
                    param.throwable = android.content.pm.PackageManager.NameNotFoundException(pkg)
                }
            }
        }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getPackageInfo", notFound) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getApplicationInfo", notFound) }

        // 3) 运行进程列表：过滤容器进程
        runCatching {
            XposedBridge.hookAllMethods(ActivityManager::class.java, "getRunningAppProcesses", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.result as? MutableList<*> ?: return
                    list.removeAll { proc ->
                        val name = (proc as? ActivityManager.RunningAppProcessInfo)?.processName ?: return@removeAll false
                        hidden.any { name.startsWith(it) }
                    }
                }
            })
        }

        Module.log("HideMultiAppHook ACTIVE (hide=${hidden.size} containers).")
    }
}
