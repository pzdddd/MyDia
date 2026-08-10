package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * HTTP 代理设置。对应 Dia 的 mod_ex_misc 的 ecei_http_proxy（原 HttpProxyHook 是空壳）。
 *
 * 原理：install 时直接用 System.setProperty 设置 http.proxyHost/Port/https.proxyHost，
 * 再 hook System.getProperty 兜底（防止 App 用 clearProperty 清掉）。
 *
 * SP key：http_proxy(总开关) / http_proxy_host / http_proxy_port
 */
class HttpProxyHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("http_proxy", false)) return
        val host = prefs.getString("http_proxy_host", "") ?: ""
        val port = prefs.getString("http_proxy_port", "") ?: ""
        if (host.isEmpty() || port.isEmpty()) return

        // 主动设置系统代理属性（影响 HttpURLConnection、OkHttp 等默认走代理）
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port)
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port)

        // 兜底：hook getProperty 防止 App 清除
        XposedBridge.hookAllMethods(System::class.java, "getProperty", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val key = param.args.getOrNull(0) as? String ?: return
                when (key) {
                    "http.proxyHost", "https.proxyHost" -> param.result = host
                    "http.proxyPort", "https.proxyPort" -> param.result = port
                }
            }
        })
        Module.log("HttpProxy: set $host:$port")
    }
}
