package com.pzdd.mydia.module.hook.extras

import android.app.Activity
import android.widget.Toast
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * Activity 名提示。对应 Dia 的 activity_show_name（开发调试用）。
 *
 * hook Activity.onResume，弹 Toast 显示当前 Activity 全类名。
 * 用于逆向时快速定位「这个界面是哪个 Activity」。
 *
 * SP key：activity_show_name(总开关)
 */
class ActivityShowNameHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("activity_show_name", false)) return
        XposedBridge.hookAllMethods(Activity::class.java, "onResume", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val act = param.thisObject as? Activity ?: return
                runCatching {
                    Toast.makeText(act, "Activity: ${act.javaClass.name}", Toast.LENGTH_SHORT).show()
                }
                Module.log("ActivityName: ${act.javaClass.name}")
            }
        })
        Module.log("ActivityShowName: installed")
    }
}
