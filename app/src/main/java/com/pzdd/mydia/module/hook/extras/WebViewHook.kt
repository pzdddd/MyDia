package com.pzdd.mydia.module.hook.extras

import android.webkit.WebView
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * WebView URL 拦截 / JS 注入。对应 Dia 的 WebViewHook + injectjs.WebViewHook。
 *
 * 功能：
 *  1. `loadUrl` 前记录目标 URL（logcat），可选重定向到指定 URL
 *  2. `evaluateJavascript` 前记录脚本
 *  3. JS 注入：hook `WebViewClient.onPageFinished` 时执行注入脚本
 *     （可注入到所有页面，用于调试 / 自动化）
 *
 * SP key：webview_hook(总开关) / webview_redirect(重定向 URL，空=不重定向) /
 *          webview_js(注入的 JS 代码)
 */
class WebViewHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("webview_hook", false)) return
        val redirect = (prefs.getString("webview_redirect", "") ?: "").trim()
        val js = (prefs.getString("webview_js", "") ?: "").trim()

        // 记录 loadUrl + 可选重定向
        XposedBridge.hookAllMethods(WebView::class.java, "loadUrl", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.args.getOrNull(0) as? String ?: return
                Module.log("WebViewHook loadUrl: $url")
                if (redirect.isNotEmpty()) {
                    param.args[0] = redirect
                    Module.log("WebViewHook redirect -> $redirect")
                }
            }
        })

        // 记录 evaluateJavascript
        runCatching {
            XposedBridge.hookAllMethods(WebView::class.java, "evaluateJavascript", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val script = param.args.getOrNull(0) as? String ?: return
                    Module.log("WebViewHook evaluateJavascript: ${script.take(200)}")
                }
            })
        }

        // JS 注入：onPageFinished 后执行注入脚本
        if (js.isNotEmpty()) {
            runCatching {
                val clientCls = Class.forName("android.webkit.WebViewClient")
                XposedBridge.hookAllMethods(clientCls, "onPageFinished", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.args.getOrNull(0) as? WebView ?: return
                        runCatching { view.evaluateJavascript(js, null) }
                    }
                })
            }
        }

        Module.log("WebViewHook ACTIVE (redirect=${redirect.ifEmpty { "none" }}, js=${js.isNotEmpty()}).")
    }
}
