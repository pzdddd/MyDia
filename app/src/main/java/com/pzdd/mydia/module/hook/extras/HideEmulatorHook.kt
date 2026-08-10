package com.pzdd.mydia.module.hook.extras

import android.os.Build
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XposedHelpers

/**
 * 隐藏模拟器特征。对应 Dia 反检测的 hide_emulator（原类空壳，逻辑加密）。
 *
 * 反模拟器检测的常见手段：查 Build.FINGERPRINT/PRODUCT/MODEL/HARDWARE/MANUFACTURER
 * 是否含 generic / google_sdk / sdk / goldfish / ranchu / Genymotion 等字样。
 * 本 hook 在 loadPackage 阶段直接把这些静态字段改成「真机」样子。
 *
 * SP key：hide_emulator(总开关)
 */
class HideEmulatorHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("hide_emulator", false)) return
        // 一组看起来像真机的值（拿个常见旗舰机样子）
        val set: (String, String) -> Unit = { field, value ->
            runCatching { XposedHelpers.setStaticObjectField(Build::class.java, field, value) }
        }
        if ((Build.FINGERPRINT ?: "").contains("generic") ||
            (Build.PRODUCT ?: "").contains("sdk") ||
            (Build.HARDWARE ?: "").contains("goldfish")) {
            set("FINGERPRINT", "google/redfin/redfin:14/UQ1A.240205.004/12345678:user/release-keys")
            set("PRODUCT", "redfin")
            set("DEVICE", "redfin")
            set("MODEL", "Pixel 5")
            set("MANUFACTURER", "Google")
            set("BRAND", "google")
            set("HARDWARE", "redfin")
            set("BOARD", "redfin")
            Module.log("HideEmulator: Build fields patched to Pixel 5")
        } else {
            Module.log("HideEmulator: no emulator fingerprint detected, skip")
        }
    }
}
