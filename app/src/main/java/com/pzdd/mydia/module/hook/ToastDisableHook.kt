package com.pzdd.mydia.module.hook

import android.widget.TextView
import android.widget.Toast
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 屏蔽 Toast（含关键字过滤）。
 *
 * 对应 Dia 的 ToastDisableHook。两个开关入口：
 *  - `disable_toast`（基础功能页顶层项）：全拦
 *  - `toast_disable` + `toast_keyword`（通知与提示分类）：按关键字拦截，空 = 全拦
 *
 * 无关键字时直接 DO_NOTHING（性能最好）；有关键字时遍历 Toast 视图树取文本匹配。
 */
class ToastDisableHook : DiaHook() {

    override fun install() {
        val basic = prefs.getBoolean("disable_toast", false)
        val enhanced = prefs.getBoolean("toast_disable", false)
        if (!basic && !enhanced) {
            Module.log("ToastDisableHook disabled.")
            return
        }

        val keywords = (prefs.getString("toast_keyword", "") ?: "").trim()
        if (keywords.isEmpty()) {
            XposedBridge.hookAllMethods(Toast::class.java, "show", de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING)
            Module.log("ToastDisableHook ACTIVE (block all).")
        } else {
            val kw = keywords.split(" ").filter { it.isNotEmpty() }
            XposedBridge.hookAllMethods(Toast::class.java, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val toast = param.thisObject as? Toast ?: return
                    if (toastText(toast)?.let { t -> kw.any { t.contains(it, ignoreCase = true) } } == true) {
                        param.result = null
                        Module.log("ToastDisableHook: blocked toast containing keyword")
                    }
                }
            })
            Module.log("ToastDisableHook ACTIVE (keyword=$keywords).")
        }
    }

    /** 提取 Toast 视图里的文本（Toast 内部通常是个 TextView 或含 TextView 的布局）。 */
    private fun toastText(toast: Toast): String? {
        return try {
            val view = toast.view ?: return null
            val tv = findTextView(view) ?: return null
            tv.text?.toString()
        } catch (_: Throwable) { null }
    }

    private fun findTextView(v: android.view.View): TextView? {
        if (v is TextView) return v
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) {
                findTextView(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
