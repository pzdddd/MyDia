package com.pzdd.mydia.module.hook.extras

import android.net.NetworkInfo
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 网络类型伪造。对应 Dia 的 dialog.box.hook.FakeNetworkHook。
 *
 * fake_network 值：
 *  - 0 = 不伪造
 *  - 1 = 强制伪装成 WiFi（type=1），让 App 以为在 WiFi 下（省流量场景常见）
 *  - 2 = 强制伪装成移动网络（type=0）
 *
 * hook NetworkInfo.getType/getSubtype/getDetailedState + ConnectivityManager，
 * 把网络状态改成 CONNECTED 并改 type。
 *
 * SP key：fake_network
 */
class FakeNetworkHook : DiaHook() {

    override fun install() {
        val mode = prefs.getString("fake_network", "0")?.toIntOrNull() ?: 0
        if (mode == 0) return
        val target = if (mode == 1) 1 else 0  // 1=WIFI 0=MOBILE
        Module.log("FakeNetwork: mode=$mode targetType=$target")

        runCatching {
            val cls = NetworkInfo::class.java
            // 改 type：getType() 返回 0(MOBILE)/1(WIFI)/...
            XposedBridge.hookAllMethods(cls, "getType", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = target }
            })
            // 改 subtype（MOBILE 细分类型，WiFi 下通常为 0）
            if (target == 1) {
                XposedBridge.hookAllMethods(cls, "getSubtype", object : MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = 0 }
                })
            }
            // 强制可用+已连接
            XposedBridge.hookAllMethods(cls, "isAvailable", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = true }
            })
            XposedBridge.hookAllMethods(cls, "isConnected", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = true }
            })
            XposedBridge.hookAllMethods(cls, "isConnectedOrConnecting", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = true }
            })
            XposedBridge.hookAllMethods(cls, "getDetailedState", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = NetworkInfo.DetailedState.CONNECTED }
            })
        }
        // 直接改字段兜底（有些 App 反射读 NetworkInfo.mType）
        runCatching {
            XposedBridge.hookAllConstructors(NetworkInfo::class.java, object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val ni = param.thisObject as? NetworkInfo ?: return
                    XposedHelpers.setIntField(ni, "mType", target)
                    XposedHelpers.setObjectField(ni, "mState", NetworkInfo.State.CONNECTED)
                    XposedHelpers.setObjectField(ni, "mDetailedState", NetworkInfo.DetailedState.CONNECTED)
                }
            })
        }
    }
}
