package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.TimeZone

/**
 * 时区伪造。对应 Dia 的 TimeZoneHook + mod_ex_fake 的 ecei_antil_check_timezone。
 *
 * hook TimeZone.getTimeZone / getDefault，把返回值替换成用户选的时区 id（如 Asia/Shanghai）。
 *
 * SP key：time_zone(总开关) / time_zone_list(时区 id，如 "Asia/Shanghai")
 */
class TimeZoneHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("time_zone", false)) return
        val tzId = prefs.getString("time_zone_list", "") ?: ""
        if (tzId.isEmpty()) return
        val fake = TimeZone.getTimeZone(tzId)

        runCatching {
            XposedBridge.hookAllMethods(TimeZone::class.java, "getDefault", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = fake
                }
            })
        }
        // getTimeZone(String) —— App 主动查时区
        runCatching {
            XposedBridge.hookAllMethods(TimeZone::class.java, "getTimeZone", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = fake
                }
            })
        }
        // 顺手改默认时区缓存
        runCatching { TimeZone.setDefault(fake) }
        Module.log("TimeZone: faked -> $tzId (${fake.id})")
    }
}
