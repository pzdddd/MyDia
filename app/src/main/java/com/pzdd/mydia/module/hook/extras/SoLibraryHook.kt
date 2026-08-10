package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 禁用 so 库加载。对应 Dia 的 SoLibraryHook + mod_ex_misc 的 ecei_disable_so_library。
 *
 * 原理：hook Runtime.loadLibrary0 / System.loadLibrary，加载名命中黑名单时 setResult null（静默跳过）。
 * 用于绕过 App 里某些会做反调试/检测的 so（如 libmsaoaidsec.so 某些加固的检测库）。
 *
 * SP key：disable_so_library(总开关) / disable_so_library_list(空格分隔 so 名，如 "msaoaidsec secneo")
 */
class SoLibraryHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("disable_so_library", false)) return
        val list = prefs.getString("disable_so_library_list", "") ?: ""
        if (list.isEmpty()) return
        val blockNames = list.split(" ").filter { it.isNotEmpty() }

        val block = object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val lib = param.args.lastOrNull()?.toString()?.lowercase() ?: return
                if (blockNames.any { lib.contains(it.lowercase()) }) {
                    param.result = null
                    Module.log("SoLibrary: blocked load '$lib'")
                }
            }
        }
        // Runtime.loadLibrary0(Class, ClassLoader, String) / loadLibrary0(Class, String)
        runCatching { XposedBridge.hookAllMethods(Runtime::class.java, "loadLibrary0", block) }
        // System.loadLibrary(String) 实际也走 Runtime，兜底再 hook 一层
        runCatching { XposedBridge.hookAllMethods(System::class.java, "loadLibrary", block) }
        Module.log("SoLibrary: installed blocklist=$blockNames")
    }
}
