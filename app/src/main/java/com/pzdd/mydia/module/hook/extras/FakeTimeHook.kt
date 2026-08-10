package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.net.URLConnection
import java.util.Date

/**
 * 系统时间伪造。对应 Dia 的 dialog.box.hook.FakeTimeHook。
 *
 * 两种模式（照搬 Dia）：
 *  - 偏移模式：time_keep=false，在原值上加 time_difference 毫秒
 *  - 固定模式：time_keep=true，永远返回 time_keep_value
 *
 * hook 点：System.currentTimeMillis / Date() / URLConnection.getDate。
 * native_time（libc 的 gettimeofday）需要原生模块，本骨架不实现，留 TODO。
 *
 * SP key：time(总开关) / time_difference / time_keep / time_keep_value / native_time
 */
class FakeTimeHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("time", false)) return
        val keep = prefs.getBoolean("time_keep", false)
        val diff = prefs.getLong("time_difference", 0L)
        val keepValue = prefs.getLong("time_keep_value", System.currentTimeMillis())
        if (!keep && diff == 0L) return

        val transform: (Long) -> Long = { original -> if (keep) keepValue else original + diff }

        // System.currentTimeMillis —— 几乎所有 Java 时间都走这里
        runCatching {
            XposedBridge.hookAllMethods(System::class.java, "currentTimeMillis", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val r = param.result as? Long ?: return
                    param.result = transform(r)
                }
            })
        }
        // new Date() / Date.getTime()
        runCatching {
            XposedBridge.hookAllMethods(Date::class.java, "getTime", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val r = param.result as? Long ?: return
                    param.result = transform(r)
                }
            })
        }
        // URLConnection.getDate —— HTTP 响应头里的时间
        runCatching {
            XposedBridge.hookAllMethods(URLConnection::class.java, "getDate", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val r = param.result as? Long ?: return
                    param.result = transform(r)
                }
            })
        }
        Module.log("FakeTime: installed keep=$keep diff=$diff keepValue=$keepValue")
        if (prefs.getBoolean("native_time", false)) {
            Module.log("FakeTime: native_time requested but native layer not implemented in MyDia (TODO)")
        }
    }
}
