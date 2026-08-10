package com.pzdd.mydia.module.hook.enhance

import android.app.Activity
import android.content.Intent
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 禁用指定 Activity。对应 Dia 的 disable_activity + DisableActivityHook。
 *
 * 描述：「Disable the opening of one or more active screen activities」
 * 原理：用户在黑名单里选若干 Activity 全类名，本 hook 在三处拦截它们：
 *  - Activity.onCreate：已经创建 → finish()
 *  - Activity.startActivity(Intent)：要启动黑名单 → setResult 拦截
 *  - Activity.startActivityForResult：同上
 * Dia 原版还做了 Intent 重定向（替换 component），这里简化为直接拦截。
 *
 * 开关：disable_activity + disable_activity_select（StringSet 黑名单）。
 */
class DisableActivityHook : DiaHookEntry() {

    private val blacklist = HashSet<String>()

    override fun installImpl() {
        if (!prefs.getBoolean("disable_activity", false)) {
            Module.log("DisableActivityHook: disabled"); return
        }
        blacklist.clear()
        blacklist += prefs.getStringSet("disable_activity_select", emptySet()) ?: emptySet()
        if (blacklist.isEmpty()) { Module.log("DisableActivityHook: blacklist empty, skip"); return }

        // 1) onCreate：命中则 finish
        XposedBridge.hookAllMethods(Activity::class.java, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val act = param.thisObject as? Activity ?: return
                if (act.javaClass.name in blacklist) {
                    Module.log("DisableActivityHook: finish ${act.javaClass.name}")
                    act.finish()
                }
            }
        })

        // 2) startActivity / startActivityForResult：拦截指向黑名单的 Intent
        val intentHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val intent = param.args.getOrNull(0) as? Intent ?: return
                val cn = intent.component ?: return
                if (cn.className in blacklist) {
                    param.result = null   // Activity 版返回 void，拦截不执行
                    Module.log("DisableActivityHook: blocked startActivity -> ${cn.className}")
                }
            }
        }
        XposedBridge.hookAllMethods(Activity::class.java, "startActivity", intentHook)
        XposedBridge.hookAllMethods(Activity::class.java, "startActivityForResult", intentHook)

        Module.log("DisableActivityHook: ${blacklist.size} activity(ies) blacklisted")
    }
}
