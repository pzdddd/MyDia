package com.pzdd.mydia.module.hook.extras

import android.content.pm.PackageInfo
import android.os.Build
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 应用版本伪装。对应 Dia 高级分类的「模拟应用版本」（ExpandActivity 里 version_code/version_name）。
 *
 * Dia 原版 UI 描述（resources.arsc）：
 *  > The first version number is for the internal identification of the device,
 *  > it must be a number! The second is for the user to see, can be any string!
 *  > Some software update is to identify the internal version number, some is to
 *  > identify the external version number, specific changes please test yourself!
 *
 * 即：
 *  - **内部版本号**（versionCode，数字）：App 升级检测、版本比较常用
 *  - **外部版本号**（versionName，字符串）：展示给用户看的，如 "1.2.3"
 *  有的 App 用 versionCode 判升级，有的用 versionName，两个都改最稳妥。
 *
 * 原理：hook `PackageManager.getPackageInfo` 与 `PackageInfo.getLongVersionCode`，
 * 当查询的是【本 App 自身】时，把返回值的 versionName / versionCode / longVersionCode
 * 改成用户填的假值，从而骗过 App 的版本自检 / 强制升级逻辑。
 *
 * SP key（与 Dia 完全对齐）：
 *  - app_version_fake : 总开关
 *  - version_code     : 假内部版本号（数字字符串，留空不改）
 *  - version_name     : 假外部版本号（任意字符串，留空不改）
 */
class AppVersionHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("app_version_fake", false)) {
            Module.log("AppVersionHook: disabled (app_version_fake=false)")
            return
        }
        val fakeName = prefs.getString("version_name", "")?.trim() ?: ""
        val fakeCodeStr = prefs.getString("version_code", "")?.trim() ?: ""
        val fakeCode = fakeCodeStr.toLongOrNull()
        if (fakeName.isEmpty() && fakeCode == null) {
            Module.log("AppVersionHook: no version set, skip")
            return
        }

        val self = Module.packageName   // 只伪装「本 App 自身」的版本，避免误伤
        Module.log("AppVersionHook: ON self=$self name='$fakeName' code=$fakeCode")

        // 1. hook PackageManager.getPackageInfo —— 改返回的 PackageInfo 字段
        runCatching {
            val pmCls = Class.forName("android.app.ApplicationPackageManager")
            XposedBridge.hookAllMethods(pmCls, "getPackageInfo", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val pkg = param.args.getOrNull(0) as? String ?: return
                    if (pkg != self) return
                    val info = param.result as? PackageInfo ?: return
                    patch(info, fakeName, fakeCode)
                }
            })
        }.onFailure { Module.err("AppVersionHook: hook getPackageInfo failed", it) }

        // 2. hook PackageInfo.getLongVersionCode —— API 28+ 版本检测常走这个方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fakeCode != null) {
            runCatching {
                XposedBridge.hookAllMethods(PackageInfo::class.java, "getLongVersionCode", object : MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        param.result = fakeCode
                    }
                })
            }.onFailure { Module.err("AppVersionHook: hook getLongVersionCode failed", it) }
        }

        Module.log("AppVersionHook ACTIVE.")
    }

    /** 把假版本号写进 PackageInfo 各字段（兼容老 versionCode 字段 + 新 longVersionCode 字段）。 */
    private fun patch(info: PackageInfo, fakeName: String, fakeCode: Long?) {
        runCatching {
            if (fakeName.isNotEmpty()) {
                XposedHelpers.setObjectField(info, "versionName", fakeName)
            }
            if (fakeCode != null) {
                // 老的 int 字段（API < 28 主用，28+ 仍存在但 deprecated）
                @Suppress("DEPRECATION")
                info.versionCode = fakeCode.toInt()
                XposedHelpers.setIntField(info, "versionCode", fakeCode.toInt())
                // 新的 long 字段（API 28+，含 versionCodeMajor 高位）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    XposedHelpers.setLongField(info, "longVersionCode", fakeCode)
                }
            }
        }.onFailure { Module.err("AppVersionHook: patch failed", it) }
    }
}
