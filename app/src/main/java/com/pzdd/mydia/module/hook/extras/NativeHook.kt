package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 原生 hook 层。对应 Dia 的 dialog.box.nativehook.NativeHook + native_init。
 *
 * 通过 [System.loadLibrary] 加载 libmydia_hook.so（assets/native_init 指向它，
 * 框架在注入时自动预加载）。加载后 JNI_OnLoad 自动安装：
 *  - gettimeofday 伪造（配合 FakeTimeHook 的 native_time 开关）
 *  - exit 拦截记录
 *  - fopen 敏感文件打点
 *
 * SP key：native_hook(总开关) —— 注意：so 由 native_init 自动加载，本开关只控制
 *          Java 层是否调用 [init] 主动设置时间（不加载 so）。
 */
class NativeHook : DiaHook() {

    companion object {
        init {
            runCatching { System.loadLibrary("mydia_hook") }
        }
    }

    private external fun nativeSetTimeOffset(ms: Long)
    private external fun nativeSetTimeFixed(ms: Long)
    private external fun nativeIsHooked(): Boolean

    override fun install() {
        if (!prefs.getBoolean("native_hook", false)) return
        if (!nativeIsHooked()) {
            Module.log("NativeHook: libmydia_hook not loaded, skip")
            return
        }

        // 联动 FakeTimeHook 的时间配置：native_time 开关开启时同步原生层
        if (prefs.getBoolean("native_time", false) && prefs.getBoolean("time", false)) {
            val keep = prefs.getBoolean("time_keep", false)
            val keepValue = prefs.getString("time_keep_value", "")?.toLongOrNull() ?: -1L
            val diff = prefs.getString("time_difference", "")?.toLongOrNull() ?: 0L
            if (keep && keepValue > 0) nativeSetTimeFixed(keepValue)
            else nativeSetTimeOffset(diff)
            Module.log("NativeHook: native time hooked (fixed=$keepValue, offset=$diff)")
        }
        Module.log("NativeHook ACTIVE.")
    }
}
