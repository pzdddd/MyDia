package com.pzdd.mydia.module.hook.extras

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 启动时清数据。对应 Dia 的 ClearDataOnStartHook + clear_data_onstart。
 *
 * 用途：调试时每次启动目标 App 都自动清空数据，保证干净环境（类似「重置应用」）。
 *
 * 实现：hook Application.attachBaseContext（最早能拿到 Context 的点），
 * 反射调 ActivityManager.clearApplicationUserData（@hide，走反射）清当前 App 数据。
 * 注意：clearApplicationUserData 是异步的，且会 kill 进程；某些 ROM/版本需系统权限。
 * 本 hook 做 best-effort，失败只记日志不影响启动。
 *
 * SP key：clear_data_onstart(总开关)
 */
class ClearDataOnStartHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("clear_data_onstart", false)) return
        XposedBridge.hookAllMethods(Application::class.java, "attachBaseContext", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val ctx = param.thisObject as? Context ?: return
                runCatching {
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
                    // clearApplicationUserData(String, IPackageDataObserver) @hide
                    val m = ActivityManager::class.java.getMethod(
                        "clearApplicationUserData", String::class.java,
                        Class.forName("android.content.pm.IPackageDataObserver")
                    )
                    // 用一个空 Observer（需动态代理接口）
                    val observer = java.lang.reflect.Proxy.newProxyInstance(
                        javaClass.classLoader,
                        arrayOf(Class.forName("android.content.pm.IPackageDataObserver"))
                    ) { _, _, _ -> /* no-op */ }
                    m.invoke(am, ctx.packageName, observer)
                    Module.log("ClearData: clearApplicationUserData invoked for ${ctx.packageName}")
                }.onFailure { Module.log("ClearData: failed ${it.message}") }
            }
        })
        Module.log("ClearDataOnStart: installed")
    }
}
