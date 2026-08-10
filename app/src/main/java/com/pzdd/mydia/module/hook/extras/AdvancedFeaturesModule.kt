package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 「高级功能」分类协调器。对应 Dia 的 mod_ex_advanced.xml 整页。
 *
 * 下辖 5 个子 hook（method_rewrite 已在主注册列表，作为独立大功能）：
 *  - TrustUserCertsHook   信任用户证书（抓 HTTPS 包）
 *  - AppSignaturesHook    应用签名伪造（绕过签名校验）
 *  - AppVersionHook       应用版本伪装
 *  - MethodEmptyHook      方法置空（让指定方法变空）
 *  - WebViewHook          WebView URL 拦截 / JS 注入
 *
 * SP key：trust_user_certs / app_signatures_fake(+select) / method(+method_empty) / method_rewrite(已有)
 */
class AdvancedFeaturesModule : DiaHook() {
    override fun install() {
        Module.log("AdvancedFeaturesModule: registering advanced hooks")
        DiaHook.register(
            TrustUserCertsHook::class.java,
            AppSignaturesHook::class.java,
            AppVersionHook::class.java,
            MethodEmptyHook::class.java,
            WebViewHook::class.java,
        )
    }
}
