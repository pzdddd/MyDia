package com.pzdd.mydia.module.hook.extras

import android.net.ConnectivityManager
import android.net.NetworkInfo
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.net.Socket
import java.util.Timer
import java.util.TimerTask

/**
 * 启动时短暂禁用网络。对应 Dia 的 SocketForDisableNetworkHook + mod_ex_misc 的 ecei_disable_network_on_start。
 *
 * 用途：某些 App 启动时会联网上报/校验，启动后网络禁用一段时间（network_time 毫秒，默认 5000）
 * 可绕过启动期检测。超时后恢复。
 *
 * 实现简化版：
 *  - hook ConnectivityManager.getActiveNetworkInfo/getAllNetworkInfo 在禁用期返回 null/空
 *  - 超时后取消 hook 影响（用一个 @Volatile 标志位）
 *
 * Dia 完整版还 hook Socket 构造/connect，本骨架只做 ConnectivityManager 层（够多数场景）。
 *
 * SP key：network(总开关) / network_time(毫秒) / network_host(可选，指定 host 时才禁)
 */
class DisableNetworkOnStartHook : DiaHook() {

    @Volatile private var blocking = true

    override fun install() {
        if (!prefs.getBoolean("network", false)) return
        val delay = prefs.getString("network_time", "5000")?.toLongOrNull() ?: 5000

        val cm = ConnectivityManager::class.java
        XposedBridge.hookAllMethods(cm, "getActiveNetworkInfo", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (blocking) { param.result = null; Module.log("DisableNet: getActiveNetworkInfo -> null") }
            }
        })
        XposedBridge.hookAllMethods(cm, "getAllNetworkInfo", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (blocking) param.result = emptyArray<NetworkInfo>()
            }
        })
        XposedBridge.hookAllMethods(cm, "getNetworkInfo", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (blocking) param.result = null
            }
        })
        // Socket connect 兜底（禁用期 connect 失败）
        runCatching {
            XposedBridge.hookAllMethods(Socket::class.java, "connect", object : MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    if (blocking) {
                        param.throwable = java.net.ConnectException("network disabled on start")
                    }
                }
            })
        }

        // 延迟恢复
        Timer().schedule(object : TimerTask() {
            override fun run() {
                blocking = false
                Module.log("DisableNet: blocking released after ${delay}ms")
            }
        }, delay)
        Module.log("DisableNet: blocking for ${delay}ms")
    }
}
