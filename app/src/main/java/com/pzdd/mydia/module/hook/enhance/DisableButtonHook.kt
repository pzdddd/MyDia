package com.pzdd.mydia.module.hook.enhance

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 取消按钮禁用。对应 Dia 的 dis_btn + ClickViewHook。
 *
 * 描述：「When the application wants to set a button unclickable, force it to be clickable」
 * 原理（对齐 Dia ClickViewHook）：
 *  - hook 4 个方法：setEnabled / setClickable / setLongClickable / setContextClickable，
 *    把应用传入的 false 改回 true。
 *  - 额外 hook View 构造器：任何 View 创建后强制 setEnabled(true)；
 *    若是 Button/Spinner/ProgressBar/EditText，再强制 setClickable/setLongClickable/
 *    setContextClickable(true)（Dia 的 afterHookedMethod 逻辑）。
 *
 * 这样 App 想置灰禁用的按钮（如「确认」在条件未满足时禁用）会被强制可点。
 *
 * 开关：prefs dis_btn。
 */
class DisableButtonHook : DiaHookEntry() {

    override fun installImpl() {
        if (!prefs.getBoolean("dis_btn", false)) {
            Module.log("DisableButtonHook: disabled"); return
        }
        // 1) 4 个 setter 的 false -> true（对齐 Dia ClickViewHook.beforeHookedMethod）
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
        XposedBridge.hookAllMethods(View::class.java, "setLongClickable", forceTrue)
        XposedBridge.hookAllMethods(View::class.java, "setContextClickable", forceTrue)

        // 2) View 构造器后强制（对齐 Dia ClickViewHook.afterHookedMethod）
        XposedBridge.hookAllConstructors(View::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val v = param.thisObject as? View ?: return
                runCatching { v.isEnabled = true }
                // 按钮类再强制可点（含长按/上下文）
                if (v is Button || v is Spinner || v is ProgressBar || v is EditText) {
                    runCatching { v.isClickable = true }
                    runCatching { v.isLongClickable = true }
                    runCatching { v.isContextClickable = true }
                }
            }
        })

        Module.log("DisableButtonHook: hook setEnabled/setClickable/setLongClickable/setContextClickable + View ctor OK")
    }
}
