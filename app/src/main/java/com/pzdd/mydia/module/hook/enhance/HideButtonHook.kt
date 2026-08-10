package com.pzdd.mydia.module.hook.enhance

import android.view.View
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 显示隐藏按钮。对应 Dia 的 hide_btn。
 *
 * 描述：「Force a button to be shown when the app wants to hide it」
 * 原理：hook `View.setVisibility`，把应用传入的 INVISIBLE(4)/GONE(8) 改回 VISIBLE(0)。
 * 这样 App 想隐藏的按钮（如「跳过广告」「关闭」常被 GONE 掉）会强制显示。
 *
 * 开关：prefs hide_btn。
 */
class HideButtonHook : DiaHookEntry() {

    override fun installImpl() {
        if (!prefs.getBoolean("hide_btn", false)) {
            Module.log("HideButtonHook: disabled"); return
        }
        XposedBridge.hookAllMethods(View::class.java, "setVisibility", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val visibility = param.args.getOrNull(0) as? Int ?: return
                if (visibility != View.VISIBLE) {
                    param.args[0] = View.VISIBLE
                    Module.log("HideButtonHook: setVisibility $visibility -> VISIBLE on ${param.thisObject.javaClass.simpleName}")
                }
            }
        })
        // 部分 ROM 用一个参数版 + LayoutParams；同时兜底 hook View 参数版已足够覆盖。
        Module.log("HideButtonHook: hook View.setVisibility OK")
    }
}
