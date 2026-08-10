package com.pzdd.mydia.module.hook.enhance

import android.view.KeyEvent
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 增强版对话框取消。对应 Dia 的 alert_close_ex（试用功能）。
 *
 * 普通版 [com.pzdd.mydia.module.hook.DialogCancelHook] hook 的是
 * Dialog.setCancelable / setCanceledOnTouchOutside 等【公开 API】。
 * 但有些 App 直接用反射改 Dialog 内部字段，或走 AppCompat AlertController——
 * 公开 API 就失效了。
 *
 * 增强版做法（show 之后强制改内部状态）：
 *  1. 反射改 Dialog.mCancelable / mCanceleable = true（原生 Dialog）
 *  2. 【关键】AppCompat AlertDialog 的返回键处理在 AlertController.onKeyDown，
 *     检查的是【AlertController 自己的 mCancelable】，不是 Dialog.mCancelable！
 *     所以递归找 dialog 内部的 AlertController 实例，把名字含 cancelable 的
 *     boolean 字段全设 true。
 *  3. 清 Window 的 FLAG_NOT_TOUCH_MODAL + 调 setCanceledOnTouchOutside(true)
 *  4. 兜底：hook Dialog.dispatchKeyEvent，BACK 键强制 cancel（无视一切 mCancelable）
 *
 * 开关：prefs alert_close_ex。稳定性一般（Dia 也标为试用），默认关。
 */
class AlertCloseExHook : DiaHookEntry() {

    override fun installImpl() {
        if (!prefs.getBoolean("alert_close_ex", false)) {
            Module.log("AlertCloseExHook: disabled"); return
        }
        try {
            XposedBridge.hookAllMethods(android.app.Dialog::class.java, "show", showHook)
            Module.log("AlertCloseExHook: hook Dialog.show OK")
        } catch (t: Throwable) { Module.err("AlertCloseExHook install failed", t) }

        // 兜底：BACK 键强制取消（AppCompat/自定义弹窗都可能绕过 mCancelable）
        try {
            XposedBridge.hookAllMethods(android.app.Dialog::class.java, "dispatchKeyEvent", backHook)
            Module.log("AlertCloseExHook: hook Dialog.dispatchKeyEvent OK")
        } catch (t: Throwable) { Module.err("AlertCloseExHook dispatchKeyEvent failed", t) }
    }

    private val showHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
            val dialog = param.thisObject ?: return
            try {
                val cls = dialog.javaClass
                // 1) 原生 Dialog 字段
                setFieldRecursive(cls, dialog, "mCancelable", true)
                setFieldRecursive(cls, dialog, "mCanceleable", true)
                // 2) AppCompat AlertController 内部字段（返回键真正检查的）
                (dialog as? android.app.Dialog)?.let { setAlertControllerCancelable(it, cls) }
                // 3) Window：允许外部点击取消 + 触摸取消
                val window = dialog.javaClass.getMethod("getWindow").invoke(dialog) as? android.view.Window
                window?.let { w ->
                    runCatching {
                        w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                        val m = android.app.Dialog::class.java.getDeclaredMethod(
                            "setCanceledOnTouchOutside", Boolean::class.javaPrimitiveType
                        )
                        m.isAccessible = true
                        m.invoke(dialog, true)
                    }
                }
                Module.log("AlertCloseExHook: forced cancelable on ${cls.name}")
            } catch (t: Throwable) {
                Module.err("AlertCloseExHook force failed", t)
            }
        }
    }

    /** BACK 键兜底：无论 mCancelable 是什么，BACK 键一律强制关闭弹窗。 */
    private val backHook = object : XC_MethodHook() {
        private val handled = java.util.concurrent.ConcurrentHashMap<Int, Int>()

        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            val event = param.args.getOrNull(0) as? KeyEvent ?: return
            if (event.action != KeyEvent.ACTION_DOWN) return
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return
            val dialog = param.thisObject as? android.app.Dialog ?: return
            if (!dialog.isShowing) return
            // 只处理首次（防重复 dismiss）
            val id = System.identityHashCode(dialog)
            val n = handled.merge(id, 1) { a, b -> a + b } ?: 1
            if (n > 3) return
            runCatching {
                // cancel() 可能被 AppCompat/自定义子类覆盖不生效，用 dismiss() 强制关闭
                //（dismiss 直接移除窗口，不走 OnCancelListener）
                dialog.dismiss()
                Module.log(
                    "AlertCloseExHook: BACK forced dismiss on ${dialog.javaClass.name} " +
                        "(showing=${dialog.isShowing})"
                )
                // 【关键】必须用 setResult()（compat 层据此跳过原方法执行）——
                // 直接 param.result = true 不置 resultSet 标志，原 dispatchKeyEvent
                // 会继续执行（可能重弹或走框架自己的逻辑抵消 dismiss）。
                param.setResult(true)
            }.onFailure { t ->
                Module.err("AlertCloseExHook: BACK dismiss failed", t)
            }
        }
    }

    /** 递归改 AppCompat AlertController 的 mCancelable（名字含 cancelable 的 boolean 字段全设 true）。 */
    private fun setAlertControllerCancelable(dialog: android.app.Dialog, start: Class<*>) {
        try {
            // 找 dialog 里类型为 AlertController 的字段
            var cls: Class<*>? = start
            while (cls != null) {
                for (f in cls.declaredFields) {
                    if (f.type.name.contains("AlertController") || f.type.simpleName == "b" || f.type.simpleName == "c") {
                        runCatching {
                            f.isAccessible = true
                            val ctrl = f.get(dialog) ?: continue
                            setCancelableFields(ctrl)
                        }
                    }
                }
                cls = cls.superclass
            }
        } catch (_: Throwable) {
        }
    }

    /** 把实例上所有名字含 cancelable 的 boolean 字段设 true。 */
    private fun setCancelableFields(obj: Any) {
        var cls: Class<*>? = obj.javaClass
        while (cls != null) {
            for (f in cls.declaredFields) {
                val fn = f.name.lowercase()
                if (f.type == Boolean::class.javaPrimitiveType && fn.contains("cancelable")) {
                    runCatching {
                        f.isAccessible = true
                        f.setBoolean(obj, true)
                    }
                }
            }
            cls = cls.superclass
        }
    }

    /** 沿继承链查找字段并赋值（兼容 private + 父类字段） */
    private fun setFieldRecursive(start: Class<*>, obj: Any, name: String, value: Any?) {
        var cls: Class<*>? = start
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                f.set(obj, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
    }
}
