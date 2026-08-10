package com.pzdd.mydia.module.hook.extras

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.regex.Pattern

/**
 * 禁用通知。对应 Dia 的 NotificationHook + mod_ex_notify_and_tips 的 ecei_disable_notify。
 *
 * hook 点：
 *  - NotificationManager.notify / notifyAsUser：args 里找 Notification，设 result=null 拦截
 *  - Service.startForeground：args[1] 是 Notification，设 result=null 拦截（防前台服务通知）
 *
 * 关键字过滤：notify_keyword 空格分隔（正则），命中才拦截；空 = 全拦。
 *
 * SP key：notify(总开关) / notify_keyword(空格分隔正则) / notify_tip(拦截时 Toast)
 */
class NotificationHook : DiaHook() {

    private val tip get() = prefs.getBoolean("notify_tip", false)
    private val keywords get() = prefs.getString("notify_keyword", "") ?: ""

    /** 同一个 callback 实例复用给 notify/notifyAsUser 两个方法 */
    private val blockCb = object : MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            val n = param.args.firstOrNull { it is Notification } ?: return
            if (shouldBlock(n)) {
                param.result = null
                Module.log("Notification: blocked ${param.method.name}")
            }
        }
    }

    override fun install() {
        if (!prefs.getBoolean("notify", false)) return

        XposedBridge.hookAllMethods(NotificationManager::class.java, "notify", blockCb)
        XposedBridge.hookAllMethods(NotificationManager::class.java, "notifyAsUser", blockCb)
        XposedBridge.hookAllMethods(Service::class.java, "startForeground", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val n = param.args.getOrNull(1) as? Notification ?: return
                if (shouldBlock(n)) {
                    param.result = null
                    Module.log("Notification: startForeground blocked")
                }
            }
        })
        Module.log("NotificationHook: installed (keywords='$keywords')")
    }

    private fun shouldBlock(n: Any): Boolean {
        if (keywords.isEmpty()) return true  // 无关键字 = 全拦
        val extras = (n as Notification).extras ?: return false
        val sb = StringBuilder()
        for (key in extras.keySet()) extras.get(key)?.let { sb.append(it) }
        val text = sb.toString()
        for (kw in keywords.split(" ")) {
            if (kw.isNotEmpty() && Pattern.compile(kw).matcher(text).find()) return true
        }
        return false
    }
}
