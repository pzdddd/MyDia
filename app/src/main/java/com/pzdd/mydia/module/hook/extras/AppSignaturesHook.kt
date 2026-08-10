package com.pzdd.mydia.module.hook.extras

import android.content.pm.PackageInfo
import android.content.pm.Signature
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 应用签名伪造。对应 Dia 的 appsignatures.AppSignaturesHook。
 *
 * 用途：绕过 App 的签名校验（很多 App 自检签名防二次打包，hook 后伪造原签名通过校验）。
 *
 * 原理：hook PackageManager.getPackageInfo，命中目标包名时把返回值的 signatures /
 * signingInfo 替换成用户填的原签名（hex）。 Dia 完整版还代理了 IPackageManager（binder 层），
 * 本骨架实现 PackageManager 层（够应对绝大多数 Java 层签名校验）。
 *
 * app_signatures_select 配置格式（JSON）："包名=hex签名" 多组用换行；hex 是签名字节数组的十六进制。
 * 完整格式参考 Dia 的 ByteUtil 解析（先 1 字节长度 + 多段），这里简化为直接 hex → Signature。
 *
 * SP key：app_signatures_fake(总开关) / app_signatures_select(配置文本)
 */
class AppSignaturesHook : DiaHook() {

    /** 包名 -> 假签名 hex 的映射 */
    private val fakeSigns = mutableMapOf<String, String>()

    override fun install() {
        if (!prefs.getBoolean("app_signatures_fake", false)) return
        parseConfig(prefs.getString("app_signatures_select", "") ?: "")
        if (fakeSigns.isEmpty()) return

        runCatching {
            val pm = Class.forName("android.app.ApplicationPackageManager")
            XposedBridge.hookAllMethods(pm, "getPackageInfo", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val pkg = param.args.getOrNull(0) as? String ?: return
                    val hex = fakeSigns[pkg] ?: return
                    val info = param.result as? PackageInfo ?: return
                    applyFakeSign(info, hex)
                    Module.log("AppSignatures: replaced sign for $pkg")
                }
            })
        }
        Module.log("AppSignatures: installed targets=${fakeSigns.keys}")
    }

    private fun parseConfig(text: String) {
        // 每行 "包名=hex签名"
        for (line in text.lines()) {
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            val pkg = line.substring(0, idx).trim()
            val hex = line.substring(idx + 1).trim()
            if (pkg.isNotEmpty() && hex.isNotEmpty()) fakeSigns[pkg] = hex
        }
    }

    private fun applyFakeSign(info: PackageInfo, hex: String) {
        runCatching {
            val bytes = hex.hexDecoded()
            info.signatures = arrayOf(Signature(bytes))
        }.onFailure {
            // signingInfo (API 28+) —— 简化处理，只改 signatures 数组
            Module.log("AppSignatures: applyFakeSign failed: ${it.message}")
        }
    }
}

/** hex 解码工具（手动实现，避免实验性 API） */
private fun String.hexDecoded(): ByteArray {
    require(length % 2 == 0) { "hex length must be even: $length" }
    return ByteArray(length / 2) { i ->
        ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
    }
}
