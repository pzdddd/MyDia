package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 包名伪装。对应 Dia 的 PackageNameHook。
 *
 * 有些 App 用 `ActivityThread.currentPackageName()` / `currentApplication()`
 * 拿「当前进程的包名」，与 buildConfig/签名包名对比做多开/重打包检测。
 * 本 hook 返回指定包名（默认不修改，靠 package_name 配置）。
 *
 * SP key：package_name_fake(总开关) / package_name_fake_value(伪装成的包名)
 */
class PackageNameHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("package_name_fake", false)) return
        val fakePkg = (prefs.getString("package_name_fake_value", "") ?: "").trim()
        if (fakePkg.isEmpty()) {
            Module.log("PackageNameHook: no target package name, skip.")
            return
        }

        // ActivityThread 是 @hide 隐藏类，编译期不可引用，按名反射获取
        runCatching {
            val activityThread = XposedHelpers.findClass("android.app.ActivityThread", classLoader)
            XposedBridge.hookAllMethods(
                activityThread,
                "currentPackageName",
                XC_MethodReplacement.returnConstant(fakePkg)
            )
        }
        // 某些 App 用 Application.getPackageName() 检测 —— 它走 ContextImpl，改它影响面太大，
        // 这里只处理 ActivityThread 层（只读进程级包名，不改 Context）。
        Module.log("PackageNameHook ACTIVE ($fakePkg).")
    }
}
