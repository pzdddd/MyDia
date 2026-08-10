package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * JSON 序列化监控。对应 Dia 的 AndroidJsonHook + Module.lambda$otherModEx$1。
 *
 * 拦截 Gson / fastjson 的序列化与反序列化，输出 JSON 内容到 logcat（监控 App 内部
 * 数据结构 / 协议体）。目标库不存在时自动跳过（App 没集成 Gson 就不 hook）。
 *
 * Gson：
 *  - `toJson(Object)` / `fromJson(String, Class)` / `fromJson(JsonElement, Type)`
 * fastjson：
 *  - `JSON.toJSONString(Object)` / `JSON.parseObject(String, Class)` /
 *    `SerializeWriter.write(...)` 系列
 *
 * SP key：json_monitor(总开关)
 */
class AndroidJsonHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("json_monitor", false)) return

        // Gson（App 可能用任何版本，按名反射）
        runCatching {
            val gson = Class.forName("com.google.gson.Gson", false, classLoader)
            XposedBridge.hookAllMethods(gson, "toJson", jsonHook("Gson.toJson"))
            XposedBridge.hookAllMethods(gson, "fromJson", jsonHook("Gson.fromJson"))
            Module.log("AndroidJsonHook: Gson hooked")
        }

        // fastjson
        runCatching {
            val jsonCls = Class.forName("com.alibaba.fastjson.JSON", false, classLoader)
            XposedBridge.hookAllMethods(jsonCls, "toJSONString", jsonHook("fastjson.toJSONString"))
            XposedBridge.hookAllMethods(jsonCls, "parseObject", jsonHook("fastjson.parseObject"))
            Module.log("AndroidJsonHook: fastjson hooked")
        }

        // fastjson SerializeWriter（更底层的字符串写出）
        runCatching {
            val sw = Class.forName("com.alibaba.fastjson.serializer.SerializeWriter", false, classLoader)
            XposedBridge.hookAllMethods(sw, "write", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.args.getOrNull(0)?.let {
                        if (it is String) Module.log("AndroidJsonHook SerializeWriter.write: ${it.take(500)}")
                    }
                }
            })
            Module.log("AndroidJsonHook: fastjson SerializeWriter hooked")
        }

        Module.log("AndroidJsonHook ACTIVE.")
    }

    private fun jsonHook(tag: String) = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            // toJson: args[0] 是对象；fromJson: args[0] 是 JSON 串
            val a0 = param.args.getOrNull(0)
            val info = when (a0) {
                is String -> if (a0.length > 2000) "${a0.take(2000)}…" else a0
                else -> param.result?.toString()?.take(2000)
            }
            if (!info.isNullOrEmpty()) Module.log("AndroidJsonHook $tag: $info")
        }
    }
}
