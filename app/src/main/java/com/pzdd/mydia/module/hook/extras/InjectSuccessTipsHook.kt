package com.pzdd.mydia.module.hook.extras

import android.app.Activity
import android.widget.Toast
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.ApplicationHook
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XposedHelpers

/**
 * 注入成功提示。对应 Dia 的 InjectSuccessTipsHook。
 *
 * 模块注入到目标 App 成功后，弹一个 Toast 提示（可配文案），
 * 方便确认「这个 App 被注入了」。
 *
 * SP key：inject_tips(总开关) / inject_tips_text(提示文案)
 */
class InjectSuccessTipsHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("inject_tips", false)) return
        val text = (prefs.getString("inject_tips_text", "") ?: "")
            .ifEmpty { "已注入 MyDia" }

        // 等 Application 就绪后，在首个 Activity resume 时弹 Toast
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady { ctx ->
            val appCls = XposedHelpers.findClass("android.app.Application", ctx.classLoader)
            runCatching {
                XposedHelpers.findAndHookMethod(appCls, "onCreate", object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runCatching {
                            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
        Module.log("InjectSuccessTipsHook ACTIVE (text=$text).")
    }
}
