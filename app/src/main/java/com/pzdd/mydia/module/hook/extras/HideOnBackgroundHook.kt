package com.pzdd.mydia.module.hook.extras

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 后台隐藏（从最近任务列表隐藏当前 App）。对应 Dia 的 HideOnBackgroundHook。
 *
 * 原理：hook Activity.onCreate，拿 ActivityManager.AppTask 调 setExcludeFromRecents(true)，
 * 让目标 App 不出现在最近任务里（防偷窥/防被划掉）。
 *
 * SP key：hide_on_background(总开关)
 */
class HideOnBackgroundHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("hide_on_background", false)) return

        XposedBridge.hookAllMethods(Activity::class.java, "onCreate", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val ctx = param.thisObject as? Activity ?: return
                runCatching {
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
                    val tasks = am.appTasks
                    if (tasks.isNotEmpty()) {
                        tasks[0].setExcludeFromRecents(true)
                        Module.log("HideOnBackground: ${ctx.javaClass.simpleName} excluded from recents")
                    }
                }
            }
        })
        Module.log("HideOnBackground: installed")
    }
}
