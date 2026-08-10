package com.pzdd.mydia.module.hook.extras

import android.app.Activity
import android.content.Intent
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Intent 监控。对应 Dia 的 IntentHook + mod_ex_dev 的 monitor_intent。
 *
 * hook Activity.startActivity / startActivityForResult，把 Intent 拆成 JSON
 * （action/data/type/component/categories/extras）打到 logcat（TAG=MyDia/Intent）。
 *
 * Dia 完整版还通过 IPCHelper 把 JSON 发回 Dia App 显示；本骨架简化为 logcat 输出，
 * 广播回传 UI 可后续基于 MonitorReceiver 模式扩展。
 *
 * SP key：monitor_intent_switch(总开关)
 */
class IntentMonitorHook : DiaHook() {

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS")

    override fun install() {
        if (!prefs.getBoolean("monitor_intent_switch", false)) return

        val cb = object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                runCatching { logIntent(activity, intent, param.method.name) }
            }
        }
        XposedBridge.hookAllMethods(Activity::class.java, "startActivity", cb)
        XposedBridge.hookAllMethods(Activity::class.java, "startActivityForResult", cb)
        XposedBridge.hookAllMethods(Activity::class.java, "startActivityIfNeeded", cb)
        Module.log("IntentMonitor: installed")
    }

    private fun logIntent(activity: Activity, intent: Intent, method: String) {
        val json = JSONObject()
        json.put("time", timeFmt.format(Date()))
        json.put("from", activity.javaClass.name)
        json.put("method", method)
        json.put("action", intent.action)
        json.put("data", intent.dataString)
        json.put("type", intent.type)
        json.put("component", intent.component?.className)
        json.put("package", intent.`package`)
        json.put("flags", "0x${Integer.toHexString(intent.flags)}")
        val categories = intent.categories
        if (categories != null) json.put("categories", categories.toString())
        val extras = intent.extras
        if (extras != null) {
            val ej = JSONObject()
            for (k in extras.keySet()) ej.put(k, "" + extras.get(k))
            json.put("extras", ej)
        }
        Module.log("Intent >>> ${json.toString(2)}")
    }
}
