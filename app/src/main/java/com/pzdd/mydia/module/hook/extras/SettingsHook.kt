package com.pzdd.mydia.module.hook.extras

import android.content.ContentResolver
import android.provider.Settings
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * Settings.Secure / Settings.System 伪装。对应 Dia 的 SettingsHook。
 *
 * 有些 App 通过 `Settings.Secure.getString(ANDROID_ID)` / `Settings.System.getInt(ADB_ENABLED)`
 * 检测调试环境。本 hook 拦截这些读取，按配置返回伪装值。
 *
 * 内置默认：adb_enabled → "0"（隐藏 USB 调试开启状态），可在 settings_list 里覆盖。
 *
 * SP key：settings_fake(总开关) / settings_list(每行「key=value」，逗号或换行分隔)
 */
class SettingsHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("settings_fake", false)) return

        val map = parseSettings(prefs.getString("settings_list", "") ?: "")
        if (map.isEmpty()) map["adb_enabled"] = "0"

        val stringHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = param.args.getOrNull(1) as? String ?: return
                map[key]?.let { param.result = it }
            }
        }
        val intHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = param.args.getOrNull(1) as? String ?: return
                map[key]?.toIntOrNull()?.let { param.result = it }
            }
        }

        runCatching { XposedBridge.hookAllMethods(Settings.Secure::class.java, "getString", stringHook) }
        runCatching { XposedBridge.hookAllMethods(Settings.System::class.java, "getString", stringHook) }
        runCatching { XposedBridge.hookAllMethods(Settings.Secure::class.java, "getInt", intHook) }
        runCatching { XposedBridge.hookAllMethods(Settings.System::class.java, "getInt", intHook) }
        // getStringForUser / getIntForUser（API 17+ 多用户变体）
        runCatching { XposedBridge.hookAllMethods(Settings.Secure::class.java, "getStringForUser", stringHook) }
        runCatching { XposedBridge.hookAllMethods(Settings.System::class.java, "getStringForUser", stringHook) }

        Module.log("SettingsHook ACTIVE (${map.size} keys).")
    }

    private fun parseSettings(raw: String): MutableMap<String, String> {
        val map = HashMap<String, String>()
        raw.split("\n", ",").forEach { line ->
            val idx = line.indexOf('=')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty()) map[k] = v
            }
        }
        return map
    }
}
