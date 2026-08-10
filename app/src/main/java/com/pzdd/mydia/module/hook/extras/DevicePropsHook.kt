package com.pzdd.mydia.module.hook.extras

import android.os.Build
import android.provider.Settings
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.UUID

/**
 * 设备属性模拟。对应 Dia 的 DevicePropsHook + mod_ex_device_props.xml。
 *
 * 原理：
 *  - Build.MODEL / PRODUCT / BOARD / BRAND / MANUFACTURER / DEVICE 是 static String 字段，
 *    在 loadPackage 阶段直接用 [XposedHelpers.setStaticObjectField] 改值即可（不需要 hook 方法）
 *  - android_id 走 Settings.Secure.getString，需 hook 方法
 *  - device_id 走 TelephonyManager.getDeviceId / Settings.Secure.get（旧版），需 hook 方法
 *
 * 支持随机模式（device_props_random=true）：每次启动用 UUID 随机生成。
 *
 * SP key：device_props(总开关) / device_props_random / android_id / device_id / model / product / board / brand / manufacturer / device
 */
class DevicePropsHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("device_props", false)) return
        val random = prefs.getBoolean("device_props_random", false)

        // 1. Build.* 静态字段直接改值
        val fields = listOf("MODEL" to "model", "PRODUCT" to "product", "BOARD" to "board",
            "BRAND" to "brand", "MANUFACTURER" to "manufacturer", "DEVICE" to "device")
        for ((buildField, key) in fields) {
            val value = if (random) randomProp() else prefs.getString(key, "") ?: ""
            if (value.isNotEmpty()) {
                runCatching {
                    XposedHelpers.setStaticObjectField(Build::class.java, buildField, value)
                    Module.log("DeviceProps: Build.$buildField = $value")
                }
            }
        }
        // Build.VERSION.RELEASE 也顺手改一下（很多检测看这个）
        if (random) {
            runCatching { XposedHelpers.setStaticObjectField(Build.VERSION::class.java, "RELEASE", "13") }
        }

        val androidId = if (random) UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            else prefs.getString("android_id", "") ?: ""
        val deviceId = if (random) (100000000000L..999999999999L).random().toString()
            else prefs.getString("device_id", "") ?: ""

        // 2. hook Settings.Secure/System.getString —— 改 android_id
        if (androidId.isNotEmpty()) {
            runCatching {
                XposedHelpers.findAndHookMethod(Settings.Secure::class.java, "getString",
                    android.content.ContentResolver::class.java, String::class.java,
                    object : MethodHook() {
                        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                            if (Settings.Secure.ANDROID_ID == param.args[1]) {
                                param.result = androidId
                            }
                        }
                    })
            }
            // System 命名空间也兜一层（有些 App 读 Settings.System）
            runCatching {
                XposedHelpers.findAndHookMethod(Settings.System::class.java, "getString",
                    android.content.ContentResolver::class.java, String::class.java,
                    object : MethodHook() {
                        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                            if ("android_id" == param.args[1]) param.result = androidId
                        }
                    })
            }
            Module.log("DeviceProps: android_id hook OK ($androidId)")
        }

        // 3. hook TelephonyManager.getDeviceId / getDeviceId(int) / getImei
        if (deviceId.isNotEmpty()) {
            for (m in listOf("getDeviceId", "getImei", "getMeid")) {
                runCatching {
                    XposedBridge.hookAllMethods(
                        Class.forName("android.telephony.TelephonyManager"), m,
                        object : MethodHook() {
                            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = deviceId }
                        })
                }
            }
            Module.log("DeviceProps: device_id hook OK ($deviceId)")
        }
    }

    private companion object {
        val CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
        fun randomProp() = (1..8).map { CHARS.random() }.joinToString("")
    }
}
