package com.pzdd.mydia.module.hook.extras

import android.Manifest
import android.location.Location
import android.location.LocationManager
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * GPS 位置伪造。对应 Dia 的 LocationHook（原类逻辑完整，本实现照搬）。
 *
 * gps_location 格式："纬度,经度"（如 "31.2304,121.4737" 上海）。
 *
 * hook 点：
 *  - Location.getLatitude/getLongitude：返回假坐标
 *  - Location.setLatitude/setLongitude：App 主动设置时也改成假值（防止 App 用真实值覆盖）
 *  - Location.set/reset：改 mLatitude/mLongitude 字段
 *  - Context.checkPermission/checkSelfPermission：ACCESS_FINE/COARSE_LOCATION 返回 GRANTED(0)
 *
 * SP key：gps(总开关) / gps_open_server / gps_open_permission(权限检测也骗) / gps_location("纬,经")
 */
class LocationHook : DiaHook() {

    private var enabled = false
    private var fakePerms = false
    private var lat = 0.0
    private var lng = 0.0

    override fun install() {
        if (!prefs.getBoolean(LocationManager.GPS_PROVIDER, false)) return  // key 就是 "gps"
        enabled = true
        fakePerms = prefs.getBoolean("gps_open_permission", false)
        val loc = prefs.getString("gps_location", "") ?: ""
        if (loc.isEmpty()) return
        val parts = loc.split(",")
        if (parts.size < 2) return
        runCatching {
            lat = parts[0].trim().toDouble()
            lng = parts[1].trim().toDouble()
        }.onFailure {
            enabled = false; return
        }
        if (!enabled) return

        // Location.getLatitude / getLongitude
        XposedBridge.hookAllMethods(Location::class.java, "getLatitude", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = lat }
        })
        XposedBridge.hookAllMethods(Location::class.java, "getLongitude", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) { param.result = lng }
        })
        // App 主动 setLatitude/setLongitude 时改成假值
        XposedBridge.hookAllMethods(Location::class.java, "setLatitude", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) { param.args[0] = lat }
        })
        XposedBridge.hookAllMethods(Location::class.java, "setLongitude", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) { param.args[0] = lng }
        })
        // set/reset 改字段
        val fixCb = object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val loc2 = param.thisObject as? Location ?: return
                XposedHelpers.setDoubleField(loc2, "mLatitude", lat)
                XposedHelpers.setDoubleField(loc2, "mLongitude", lng)
            }
        }
        XposedBridge.hookAllMethods(Location::class.java, "set", fixCb)
        XposedBridge.hookAllMethods(Location::class.java, "reset", fixCb)

        // 权限检测欺骗
        if (fakePerms) {
            XposedBridge.hookAllMethods(android.content.Context::class.java, "checkPermission", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val perm = param.args.getOrNull(0) as? String ?: return
                    if (perm == Manifest.permission.ACCESS_FINE_LOCATION ||
                        perm == Manifest.permission.ACCESS_COARSE_LOCATION) {
                        param.result = 0  // PERMISSION_GRANTED
                    }
                }
            })
            XposedBridge.hookAllMethods(android.content.Context::class.java, "checkSelfPermission", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val perm = param.args.getOrNull(0) as? String ?: return
                    if (perm == Manifest.permission.ACCESS_FINE_LOCATION ||
                        perm == Manifest.permission.ACCESS_COARSE_LOCATION) {
                        param.result = 0
                    }
                }
            })
        }
        Module.log("LocationHook: faked ($lat, $lng) fakePerms=$fakePerms")
    }
}
