package com.pzdd.mydia.module.hook.extras

import android.app.Application
import android.content.pm.ApplicationInfo
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 信任用户证书（让 App 抓 HTTPS 包）。对应 Dia 的 TrustUserCertsHook。
 *
 * 原理（照搬 Dia）：Android 7+ (API 24+) 默认不信任用户安装的 CA 证书，只有 targetSdk<24 的 App 才信任。
 * hook `android.security.net.config.NetworkSecurityConfig.getDefaultBuilder(ApplicationInfo)`，
 * 调用前临时把 ApplicationInfo.targetSdkVersion 改成 20（<24），让生成的 config 信任用户 CA，
 * 调完恢复原值。这样所有 App 都像旧版一样信任用户装的 Charles/Fiddler/mitmproxy 证书。
 *
 * SP key：trust_user_certs(总开关)
 */
class TrustUserCertsHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("trust_user_certs", false)) return

        val candidates = listOf(
            "android.security.net.config.NetworkSecurityConfig",
            "android.security.net.config.ManifestConfigSource",
        )
        var hooked = false
        for (name in candidates) {
            if (hooked) break
            runCatching {
                val cls = Class.forName(name)
                XposedBridge.hookAllMethods(cls, "getDefaultBuilder", object : MethodHook() {
                    override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val ai = param.args.getOrNull(0) as? ApplicationInfo ?: return
                        // 临时降 targetSdk，调原方法拿结果，再恢复
                        val old = ai.targetSdkVersion
                        ai.targetSdkVersion = 20
                        param.result = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        ai.targetSdkVersion = old
                        Module.log("TrustUserCerts: getDefaultBuilder faked (sdk $old -> 20 -> $old)")
                    }
                })
                hooked = true
            }
        }
        if (!hooked) {
            // 兜底：直接 hook ApplicationInfo.targetSdkVersion 字段读取不可行，标记未生效
            Module.log("TrustUserCerts: getDefaultBuilder not found on this Android, hook skipped")
        }
        // 额外保险：hook Application 创建时记录，便于排查
        runCatching {
            XposedHelpers.findAndHookMethod(Application::class.java, "onCreate", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    if (hooked) Module.log("TrustUserCerts: active in ${param.thisObject.javaClass.name}")
                }
            })
        }
    }
}
