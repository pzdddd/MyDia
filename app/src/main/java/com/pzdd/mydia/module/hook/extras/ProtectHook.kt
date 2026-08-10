package com.pzdd.mydia.module.hook.extras

import android.content.pm.PackageManager
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 模块自身防护。对应 Dia 的 ProtectHook（部分）。
 *
 * 场景：目标 App 检测「Xposed 模块是否装在手机上」—— 查 `com.pzdd.mydia` 这个
 * 模块包是否存在 / 是否被禁用。本 hook 把模块自身从 PackageManager 查询结果里抹掉，
 * 让 App 查不到这个包。
 *
 * SP key：protect(总开关) / protect_pkg(要保护的包名，默认本模块包名)
 */
class ProtectHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("protect", false)) return
        val pkg = (prefs.getString("protect_pkg", "") ?: "").trim()
            .ifEmpty { com.pzdd.mydia.module.Module.HOST_PACKAGE }

        val filter = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                when (val result = param.result) {
                    is android.content.pm.PackageInfo -> if (result.packageName == pkg) param.result = null
                    is android.content.pm.ApplicationInfo -> if (result.packageName == pkg) param.result = null
                    is MutableList<*> -> result.removeAll { (it as? android.content.pm.PackageInfo)?.packageName == pkg }
                }
            }
        }
        val notFound = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args.getOrNull(0) == pkg) {
                    param.throwable = android.content.pm.PackageManager.NameNotFoundException(pkg)
                }
            }
        }

        // hook PackageManager 基类：hookAllMethods 会同时 hook 到 ApplicationPackageManager
        // 等所有子类的同名实现（@hide 的 ApplicationPackageManager 编译期不可引用，基类等效）
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getPackageInfo", filter) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getApplicationInfo", filter) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getInstalledPackages", filter) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getInstalledApplications", filter) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getPackageArchiveInfo", notFound) }

        Module.log("ProtectHook ACTIVE (hide $pkg).")
    }
}
