package com.pzdd.mydia.module.hook.extras

import android.net.NetworkCapabilities
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 隐藏 VPN 连接。对应 Dia 的 VPNHook（原类逻辑完整，本实现照搬）。
 *
 * App 检测 VPN 的常见点 & 应对：
 *  - NetworkCapabilities.hasTransport(TRANSPORT_VPN=15) → 强制返回 true（伪装成有其他传输）
 *  - NetworkCapabilities.hasCapability(NET_CAPABILITY_NOT_VPN) → 返回 true
 *  - NetworkInterface.getName() 含 tun0/ppp0/pptp → 改成 net0
 *  - ConnectivityManager.getActiveNetworkInfo().getType() == TYPE_VPN(17) → 改成 TYPE_WIFI(1)
 *
 * SP key：vpn(总开关)
 */
class VPNHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("vpn", false)) return

        runCatching {
            val cls = NetworkCapabilities::class.java
            // hasTransport(int)：TRANSPORT_VPN=15 时返回 true 让 App 以为在普通网络
            XposedBridge.hookAllMethods(cls, "hasTransport", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    if ((param.args[0] as Int) == 15) param.result = false
                }
            })
            // hasCapability(int)：NET_CAPABILITY_NOT_VPN=15 时返回 true（注意值同 15 但语义不同）
            XposedBridge.hookAllMethods(cls, "hasCapability", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val cap = param.args[0] as Int
                    // TRANSPORT_VPN 的 capability 检测
                    if (cap == 15) param.result = true
                }
            })
        }

        // 网络接口名 tun0/ppp0 → net0
        runCatching {
            val cls = Class.forName("java.net.NetworkInterface")
            XposedBridge.hookAllMethods(cls, "getName", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val name = param.result as? String ?: return
                    val lower = name.lowercase()
                    if (lower.contains("tun0") || lower.contains("ppp0") || name.contains("pptp")) {
                        param.result = "net0"
                        Module.log("VPNHook: getName $name -> net0")
                    }
                }
            })
            XposedBridge.hookAllMethods(cls, "getDisplayName", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val name = param.result as? String ?: return
                    if (name.lowercase().contains("tun") || name.lowercase().contains("ppp")) {
                        param.result = "net0"
                    }
                }
            })
        }

        // NetworkInfo.getType() == TYPE_VPN(17) → 改 TYPE_WIFI(1)
        runCatching {
            XposedBridge.hookAllMethods(android.net.NetworkInfo::class.java, "getType", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    if ((param.result as Int) == 17) param.result = 1
                }
            })
        }
        Module.log("VPNHook: installed")
    }
}
