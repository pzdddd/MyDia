package com.pzdd.mydia.module.hook.enhance

import android.view.View
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 取消按钮禁用。对应 Dia 的 dis_btn。
 *
 * 描述：「When the application wants to set a button unclickable, force it to be clickable」
 * 原理：hook `View.setEnabled` / `View.setClickable`，把应用传入的 false 改回 true。
 * 这样 App 想置灰禁用的按钮（如「确认」在条件未满足时禁用）会被强制可点。
 *
 * 开关：prefs dis_btn。
 */
class DisableButtonHook : DiaHookEntry() {

    override fun installImpl() {
        if (!prefs.getBoolean("dis_btn", false)) {
            Module.log("DisableButtonHook: disabled"); return
        }
        val forceTrue = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val v = param.args.getOrNull(0) as? Boolean ?: return
                if (!v) {
                    param.args[0] = true
                    Module.log("DisableButtonHook: ${param.method.name} false -> true on ${param.thisObject.javaClass.simpleName}")
                }
            }
        }
        XposedBridge.hookAllMethods(View::class.java, "setEnabled", forceTrue)
        XposedBridge.hookAllMethods(View::class.java, "setClickable", forceTrue)
        Module.log("DisableButtonHook: hook setEnabled/setClickable OK")
    }
}
