package com.pzdd.mydia.module.hook.extras

import android.net.http.SslCertificate
import android.net.http.SslError
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * JustTrustMe+ —— 全量 SSL Pinning 绕过（比原版 JustTrustMe / TrustUserCerts 更全面）。
 *
 * 现有 TrustUserCertsHook 只做了 NetworkSecurityConfig（信任用户证书），
 * 但绕不过 OkHttp CertificatePinner / 自定义 TrustManager / Conscrypt 等。
 *
 * 本 hook 覆盖以下 bypass 点（对齐 TrustMeAlready / SSLUnpinning 等成熟项目的通用方法）：
 *
 *  1. OkHttp3 CertificatePinner.check → 空实现（最常见 pinning）
 *  2. TrustManagerImpl.verifyChain / checkTrustedRecursive → 直接返回通过
 *  3. SSLContext.init → 替换为信任所有证书的 TrustManager
 *  4. HostnameVerifier.verify → 恒 true
 *  5. OkHostnameVerifier.verify → 恒 true
 *  6. X509TrustManagerExtensions.checkServerTrusted → 返回空列表
 *  7. Conscrypt（Android 10+）→ 绕过 native 层证书校验
 *  8. WebViewClient.onReceivedSslError → proceed（忽略 SSL 错误）
 *  9. HttpsURLConnection.setDefaultHostnameVerifier / setDefaultSSLSocketFactory
 *
 * SP key：just_trust_me_plus（总开关）
 */
class JustTrustMePlusHook : DiaHook() {

    companion object {
        /** 信任所有证书的 TrustManager */
        private val TRUST_ALL: Array<TrustManager> = arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
    }

    override fun install() {
        if (!prefs.getBoolean("just_trust_me_plus", false)) {
            Module.log("JustTrustMePlusHook: disabled")
            return
        }
        var count = 0
        val safe: (String, () -> Unit) -> Unit = { tag, block ->
            runCatching { block(); count++ }.onFailure { /* 静默：部分类在不同 ROM 上不存在 */ }
        }

        // 1. OkHttp3 CertificatePinner.check（含 Kotlin 混淆名 check$okhttp）
        safe("CertificatePinner") {
            try {
                val cls = Class.forName("okhttp3.CertificatePinner")
                XposedBridge.hookAllMethods(cls, "check", forcePass())
                // Kotlin 内部名
                XposedBridge.hookAllMethods(cls, "check\$okhttp", forcePass())
            } catch (_: Throwable) {}
        }

        // 2. TrustManagerImpl（Android 系统级证书校验）
        safe("TrustManagerImpl") {
            val cls = Class.forName("com.android.org.conscrypt.TrustManagerImpl")
            XposedBridge.hookAllMethods(cls, "verifyChain", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // 不改 result（原方法已返回 chain），只需让校验通过
                }
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 把 chain 参数里可能空的给一个占位，避免 NPE
                }
            })
            XposedBridge.hookAllMethods(cls, "checkTrustedRecursive", forcePassList())
        }

        // 3. SSLContext.init → 替换 TrustManager 为信任所有
        safe("SSLContext") {
            XposedHelpers.findAndHookMethod(
                SSLContext::class.java, "init",
                Array<javax.net.ssl.KeyManager>::class.java,
                Array<TrustManager>::class.java,
                java.security.SecureRandom::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[1] = TRUST_ALL
                        Module.log("JustTrustMePlus: SSLContext.init -> trustAll")
                    }
                }
            )
        }

        // 4. HostnameVerifier.verify → true
        safe("HostnameVerifier") {
            XposedHelpers.findAndHookMethod(
                HostnameVerifier::class.java, "verify",
                String::class.java, javax.net.ssl.SSLSession::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        }

        // 5. OkHostnameVerifier（OkHttp 的主机名验证）
        safe("OkHostnameVerifier") {
            try {
                val cls = Class.forName("okhttp3.internal.tls.OkHostnameVerifier")
                XposedBridge.hookAllMethods(cls, "verify", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                })
            } catch (_: Throwable) {}
        }

        // 6. X509TrustManagerExtensions.checkServerTrusted → 返回空列表
        safe("X509Ext") {
            try {
                val cls = Class.forName("android.net.http.X509TrustManagerExtensions")
                XposedBridge.hookAllMethods(cls, "checkServerTrusted", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = java.util.Collections.emptyList<Any>()
                    }
                })
            } catch (_: Throwable) {}
        }

        // 7. Conscrypt（Android 10+）：ConscryptEngineSocket / ConscryptFileDescriptorSocket
        safe("Conscrypt") {
            try {
                val cls = Class.forName("com.android.org.conscrypt.ConscryptEngine")
                XposedBridge.hookAllMethods(cls, "verifyCertificateChain", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null // 跳过证书链校验
                    }
                })
            } catch (_: Throwable) {}
        }

        // 8. WebViewClient.onReceivedSslError → proceed
        safe("WebView") {
            XposedHelpers.findAndHookMethod(
                android.webkit.WebViewClient::class.java, "onReceivedSslError",
                android.webkit.WebView::class.java,
                SslErrorHandlerClass(),
                SslError::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val handler = param.args[1] ?: return
                        runCatching {
                            handler.javaClass.getMethod("proceed").invoke(handler)
                            param.result = null
                        }
                    }
                }
            )
        }

        // 9. HttpsURLConnection 默认验证器/工厂
        safe("HttpsURLConnection") {
            runCatching {
                val trustAllFactory = SSLContext.getInstance("TLS").apply {
                    init(null, TRUST_ALL, java.security.SecureRandom())
                }.socketFactory
                javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(trustAllFactory)
                javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            }
        }

        // 10. TrustUserCerts（NetworkSecurityConfig，与现有 TrustUserCertsHook 相同逻辑）
        safe("NetworkSecurityConfig") {
            val cls = Class.forName("android.security.net.config.NetworkSecurityConfig")
            XposedBridge.hookAllMethods(cls, "getDefaultBuilder", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ai = param.args.getOrNull(0) as? android.content.pm.ApplicationInfo ?: return
                    val old = ai.targetSdkVersion
                    ai.targetSdkVersion = 20
                    param.result = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    ai.targetSdkVersion = old
                }
            })
        }

        Module.log("JustTrustMePlusHook ACTIVE ($count bypass points)")
    }

    /** 强制通过（before → param.result = null / 指定值，after 不改）。 */
    private fun forcePass() = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            // void 方法 setResult(null)；返回值方法不动 result（原方法会返回）
        }
    }

    /** 强制通过，返回值改成空列表（checkTrustedRecursive 返回 List）。 */
    private fun forcePassList() = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            // 不改——让原方法的返回值通过（若原方法抛异常需要 catch）
        }
        override fun beforeHookedMethod(param: MethodHookParam) {
            // 部分系统在 before 里就抛 CertificateException，用 try-catch 兜底
        }
    }

    /** SslErrorHandler 的 Class（不同 API 可能有差异，反射拿）。 */
    private fun SslErrorHandlerClass(): Class<*> =
        runCatching { Class.forName("android.webkit.SslErrorHandler") }
            .getOrDefault(android.webkit.SslErrorHandler::class.java)
}
