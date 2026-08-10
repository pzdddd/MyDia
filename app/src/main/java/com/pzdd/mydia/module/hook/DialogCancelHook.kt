package com.pzdd.mydia.module.hook

import android.app.Activity
import android.app.Dialog
import android.view.Window
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 灵魂功能：强制任何 App 的对话框可取消（可点外部 / 可返回键关闭）。
 * 对应 Dia 的 Module.alertClose()。
 *
 * 原理：把所有「setCancelable / setCanceledOnTouchOutside / setFinishOnTouchOutside …」
 * 方法的第一个 boolean 参数强制改成 true。
 */
class DialogCancelHook : DiaHook() {

    override fun install() {
        // 读开关。先用 per-App 配置，没设就回退全局。
        val enabled = prefs.getBoolean(
            "alert_close",
            prefs.getBoolean("global_alert_close", false)
        )
        if (!enabled) {
            Module.log("DialogCancelHook disabled.")
            return
        }

        val forceTrue = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args.isNotEmpty()) param.args[0] = true
            }
        }

        // 框架类，随时可 hook
        safeHook { XposedBridge.hookAllMethods(Dialog::class.java, "setCancelable", forceTrue) }
        safeHook { XposedBridge.hookAllMethods(Dialog::class.java, "setCanceledOnTouchOutside", forceTrue) }
        safeHook { XposedBridge.hookAllMethods(Activity::class.java, "setFinishOnTouchOutside", forceTrue) }
        // Window 的几个方法在不同 API 版本上名字/有无不同，逐个 try
        safeHook { XposedBridge.hookAllMethods(Window::class.java, "setCloseOnTouchOutside", forceTrue) }
        safeHook { XposedBridge.hookAllMethods(Window::class.java, "setCloseOnTouchOutsideIfNotSet", forceTrue) }
        safeHook { XposedBridge.hookAllMethods(Window::class.java, "setCloseOnSwipeEnabled", forceTrue) }

        // androidx.appcompat 的 AlertDialog$Builder（老 AppCompat），按名反射 hook
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady { ctx ->
            runCatching {
                val cl = ctx.classLoader
                listOf(
                    "androidx.appcompat.app.AlertDialog\$Builder",
                    "android.support.v7.app.AlertDialog\$Builder"
                ).forEach { name ->
                    runCatching {
                        val k = Class.forName(name, false, cl)
                        safeHook { XposedBridge.hookAllMethods(k, "setCancelable", forceTrue) }
                    }
                }
            }
        }

        Module.log("DialogCancelHook ACTIVE.")
    }

    private inline fun safeHook(block: () -> Unit) = runCatching { block() }
}
