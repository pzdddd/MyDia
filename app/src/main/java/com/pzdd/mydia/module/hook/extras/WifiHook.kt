package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * WiFi 信息伪造。对应 Dia 的 WifiHook + mod_ex_fake 的 ecei_fake_wifi。
 *
 * hook WifiInfo.getSSID / getBSSID，替换成用户填的假值。
 *  - getSSID 返回值带引号（如 "\"MyWiFi\""），Dia 也这么处理
 *  - getBSSID 返回 MAC 格式字符串
 *
 * SP key：wifi(总开关) / wifi_name(SSID) / wifi_mac(BSSID/MAC)
 */
class WifiHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("wifi", false)) return
        val name = prefs.getString("wifi_name", "") ?: ""
        val mac = prefs.getString("wifi_mac", "") ?: ""
        if (name.isEmpty() && mac.isEmpty()) return

        runCatching {
            val cls = Class.forName("android.net.wifi.WifiInfo")
            if (name.isNotEmpty()) {
                XposedBridge.hookAllMethods(cls, "getSSID", object : MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = "\"$name\"" }
                })
            }
            if (mac.isNotEmpty()) {
                XposedBridge.hookAllMethods(cls, "getBSSID", object : MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = mac }
                })
                // MAC 地址字段也有 App 通过 getMacAddress 读（旧 API）
                XposedBridge.hookAllMethods(cls, "getMacAddress", object : MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = mac }
                })
            }
            Module.log("Wifi: faked ssid=$name bssid=$mac")
        }
    }
}
