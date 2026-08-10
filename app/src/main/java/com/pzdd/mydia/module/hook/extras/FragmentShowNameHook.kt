package com.pzdd.mydia.module.hook.extras

import android.widget.Toast
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * Fragment 名提示。对应 Dia 的 fragment_show_name（开发调试用）。
 *
 * hook Fragment.onCreate，弹 Toast 显示 Fragment 全类名。
 * 用于逆向定位 Fragment（很多 App 用 Fragment 组织界面，单看 Activity 定位不到）。
 *
 * SP key：fragment_show_name(总开关)
 */
class FragmentShowNameHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("fragment_show_name", false)) return
        runCatching {
            // androidx.Fragment
            val cls = Class.forName("androidx.fragment.app.Fragment")
            XposedBridge.hookAllMethods(cls, "onCreate", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val f = param.thisObject ?: return
                    val ctx = runCatching { f.javaClass.getMethod("requireActivity").invoke(f) as? android.app.Activity }.getOrNull()
                    runCatching { Toast.makeText(ctx, "Fragment: ${f.javaClass.name}", Toast.LENGTH_SHORT).show() }
                    Module.log("FragmentName: ${f.javaClass.name}")
                }
            })
        }
        runCatching {
            // 原生 Fragment（旧 App）
            val cls = Class.forName("android.app.Fragment")
            XposedBridge.hookAllMethods(cls, "onCreate", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    Module.log("FragmentName(native): ${param.thisObject.javaClass.name}")
                }
            })
        }
        Module.log("FragmentShowName: installed")
    }
}
