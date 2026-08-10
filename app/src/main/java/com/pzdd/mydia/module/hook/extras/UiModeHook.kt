package com.pzdd.mydia.module.hook.extras

import android.content.res.Configuration
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * UiMode 伪造。对应 Dia 的 UiModeHook + mod_ex_misc 的 ui_mode。
 *
 * ui_mode 值（对齐 Dia arrays）：
 *  - 0 = 不改
 *  - 1 = 强制深色模式（UI_MODE_NIGHT_YES）
 *  - 2 = 强制浅色模式（UI_MODE_NIGHT_NO）
 *
 * hook Resources.getConfiguration / Configuration.uiMode，改 night 模式位。
 *
 * SP key：ui_mode
 */
class UiModeHook : DiaHook() {

    override fun install() {
        val mode = prefs.getString("ui_mode", "0")?.toIntOrNull() ?: 0
        if (mode == 0) return
        val nightBits = if (mode == 1) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO

        runCatching {
            val cls = Class.forName("android.content.res.Resources")
            XposedBridge.hookAllMethods(cls, "getConfiguration", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val c = param.result as? Configuration ?: return
                    c.uiMode = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
                }
            })
        }
        // UiModeManager.getNightMode 兜底
        runCatching {
            val cls = Class.forName("android.app.UiModeManager")
            XposedBridge.hookAllMethods(cls, "getNightMode", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // MODE_NIGHT_YES=2 / MODE_NIGHT_NO=1
                    param.result = if (mode == 1) 2 else 1
                }
            })
        }
        Module.log("UiMode: faked mode=$mode (${if (mode==1) "dark" else "light"})")
    }
}
