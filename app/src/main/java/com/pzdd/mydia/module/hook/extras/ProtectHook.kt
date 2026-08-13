package com.pzdd.mydia.module.hook.extras

import android.content.pm.PackageManager
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 模块自身防护 + 风险包名隐藏。对应 Dia 的 ProtectHook。
 *
 * 场景：目标 App 检测「Xposed 模块 / Magisk / LSPosed 等风险包是否装在手机上」——
 * 查 PackageManager 是否返回这些包名。本 hook 把指定包从查询结果里抹掉，
 * 让 App 查不到。
 *
 * 内置风险包名单 + 用户自定义（protect_pkg，每行一个包名）：
 *  - 本模块包名（com.pzdd.mydia）
 *  - Magisk Manager（com.topjohnwu.magisk / io.github.huskydg.magisk / io.github.vvb2060.magisk）
 *  - LSPosed Manager（org.lsposed.manager / org.meowcat.edxposed）
 *  - Shamiko / HideMyApplist / Shizuku 等风险工具
 *
 * SP key：protect(总开关) / protect_pkg(自定义包名，每行一个)
 */
class ProtectHook : DiaHook() {

    companion object {
        /** 内置风险包名（Magisk / LSPosed / root 工具系） */
        private val RISK_PACKAGES = setOf(
            // Magisk
            "com.topjohnwu.magisk",
            "io.github.huskydg.magisk",
            "io.github.vvb2060.magisk",
            // LSPosed / EdXposed
            "org.lsposed.manager",
            "org.meowcat.edxposed.manager",
            "de.robv.android.xposed.installer",
            // 隐藏 / root 工具
            "com.tsng.hidemyapplist",
            "moe.shizuku.privileged.api",
            "com.kieronquinn.app.darq",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.noshufou.android.su",
            // 本模块（加入列表确保也被隐藏）
        )
    }

    override fun install() {
        if (!prefs.getBoolean("protect", false)) return

        // 构建完整隐藏名单：内置风险包 + 用户自定义 + 本模块
        val userPkgs = (prefs.getString("protect_pkg", "") ?: "")
            .split("\n", ",", "，", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val hiddenPackages = RISK_PACKAGES + userPkgs + Module.HOST_PACKAGE

        /** 检查包名是否在隐藏名单里 */
        fun isHidden(pkg: String?): Boolean {
            if (pkg == null) return false
            return hiddenPackages.any { pkg == it || pkg.startsWith("$it.") }
        }

        /** after：从查询结果里删掉命中的包 */
        val filter = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                when (val result = param.result) {
                    is android.content.pm.PackageInfo -> if (isHidden(result.packageName)) param.result = null
                    is android.content.pm.ApplicationInfo -> if (isHidden(result.packageName)) param.result = null
                    is MutableList<*> -> {
                        // getInstalledPackages 返回 List<PackageInfo>
                        result.removeAll { (it as? android.content.pm.PackageInfo)?.packageName?.let { p -> isHidden(p) } == true }
                        // getInstalledApplications 返回 List<ApplicationInfo>
                        result.removeAll { (it as? android.content.pm.ApplicationInfo)?.packageName?.let { p -> isHidden(p) } == true }
                    }
                }
            }
        }
        /** before：精确查询命中 → 抛 NameNotFoundException */
        val notFound = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val queriedPkg = param.args.getOrNull(0) as? String ?: return
                if (isHidden(queriedPkg)) {
                    param.throwable = PackageManager.NameNotFoundException(queriedPkg)
                }
            }
        }

        // hook PackageManager 基类（hookAllMethods 会同时 hook 到 ApplicationPackageManager 子类）
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getPackageInfo", notFound) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getApplicationInfo", notFound) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getInstalledPackages", filter) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getInstalledApplications", filter) }
        runCatching { XposedBridge.hookAllMethods(PackageManager::class.java, "getPackagesForUid", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val arr = param.result as? Array<String> ?: return
                param.result = arr.filter { !isHidden(it) }
            }
        }) }

        Module.log("ProtectHook ACTIVE (hide ${hiddenPackages.size} packages: ${hiddenPackages.take(5)}...)")
    }
}
