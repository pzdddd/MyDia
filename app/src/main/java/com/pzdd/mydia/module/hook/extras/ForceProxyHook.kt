package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 强制代理。对应 Dia 的 ForceProxyHook。
 *
 * 场景：抓包/调试时需要把目标 App 的所有 HTTP(S) 流量强制走本地代理
 * （配合 Charles / Fiddler / Burp）。
 *
 * 拦截点：
 *  - `HttpURLConnection.setProxy` 无效时，hook `HttpURLConnection.connect` 前
 *    反射设置内部 okhttp / Proxy 字段（简化版）
 *  - `System.setProperty(http.proxyHost/http.proxyPort)` 全局代理
 *  - 记录每个连接目标（logcat）
 *
 * SP key：force_proxy(总开关) / force_proxy_host / force_proxy_port
 */
class ForceProxyHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("force_proxy", false)) return
        val host = (prefs.getString("force_proxy_host", "") ?: "").trim()
        val port = (prefs.getString("force_proxy_port", "") ?: "").trim().toIntOrNull() ?: 8080
        if (host.isEmpty()) {
            Module.log("ForceProxyHook: no host, skip.")
            return
        }

        // 1) 系统级代理属性（HttpURLConnection 默认读取）
        runCatching { System.setProperty("http.proxyHost", host); System.setProperty("http.proxyPort", port.toString()) }
        runCatching { System.setProperty("https.proxyHost", host); System.setProperty("https.proxyPort", port.toString()) }

        // 2) hook connect：记录目标 + 反射设置 proxy 字段（API 差异时跳过）
        XposedBridge.hookAllMethods(HttpURLConnection::class.java, "connect", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val conn = param.thisObject as? HttpURLConnection ?: return
                Module.log("ForceProxyHook: connect to ${conn.url}")
                // 反射设置内部 proxy 字段（URLConnection.proxy 是 @hide）
                runCatching {
                    val f = java.net.URLConnection::class.java.getDeclaredField("proxy")
                    f.isAccessible = true
                    f.set(conn, Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                }
            }
        })

        Module.log("ForceProxyHook ACTIVE ($host:$port).")
    }
}
