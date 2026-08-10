package com.pzdd.mydia.module.hook.enhance

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 禁用对话框。对应 Dia 的 alert + ViewGroupHook + Module.ActivityKey 联动。
 *
 * 原理：hook `ViewGroup.addView`，当有 view 被加到【窗口根（DecorView / android.R.id.content）】时，
 * 用 [ViewScanner] 检查它是否含目标关键字 / id：
 *  - 命中 → setResult(null) 阻止添加（对话框根本显示不出来）
 *  - 也可在下一帧 PreDraw 时 removeView（兜底）
 *
 * 开关与参数（对齐 Dia mod_ex_dialog）：
 *  - alert : 总开关
 *  - alert_keyword / alert_id : 匹配条件（两者都空 = 禁用全部对话框）
 *  - disable_alert_mode : false=单检(只查 DecorView/content，稳定) / true=全检(所有 ViewGroup，激进)
 *  - toast : 命中时弹 Toast 提示
 *  - alert_enabled : 运行时按键触发（见 [KeyTriggerHook]，本类暴露 [enabled] 运行时开关）
 *
 * 注意：alert_auto=true 时 App 一启动就启用；否则要靠按键触发才生效。
 */
class AlertDisableHook : DiaHookEntry() {

    /** 运行时开关（由 KeyTriggerHook 按键 toggle） */
    @Volatile var active: Boolean = false
        private set

    /** 兼容外部只读（KeyTriggerHook 读它显示状态） */
    val enabled: Boolean get() = active

    private val pendingRemoval = java.util.concurrent.CopyOnWriteArrayList<ViewGroup>()

    override fun installImpl() {
        if (!prefs.getBoolean("alert", false)) {
            Module.log("AlertDisableHook: disabled (alert=false)"); return
        }
        // alert_auto=true 时启动即启用；否则等按键触发
        active = prefs.getBoolean("alert_auto", true)
        fullScanMode = prefs.getBoolean("disable_alert_mode", false)
        toast = prefs.getBoolean("toast", false)
        keyword = prefs.getString("alert_keyword", "") ?: ""
        ids = prefs.getString("alert_id", "") ?: ""

        // hook 所有 ViewGroup 子类的 addView 太广，这里 hook 基类 ViewGroup 即可（子类都会走）
        XposedBridge.hookAllMethods(ViewGroup::class.java, "addView", addViewHook)
        // 记录真实 Dialog 窗口根（show 时），拦截只针对它们，避免误伤 Activity 主界面
        runCatching {
            XposedBridge.hookAllMethods(android.app.Dialog::class.java, "show", object : XC_MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val d = param.thisObject as? android.app.Dialog ?: return
                    runCatching { d.window?.decorView?.let { dialogDecorViews.add(it) } }
                }
            })
        }
        Module.log("AlertDisableHook: hook ViewGroup.addView OK (enabled=$enabled, fullScan=$fullScanMode)")
    }

    private var fullScanMode = false
    private var toast = false
    private var keyword = ""
    private var ids = ""

    /** 由 KeyTriggerHook 调用：切换运行时启用状态 */
    fun toggle() {
        active = !active
        Module.log("AlertDisableHook toggled -> $active")
    }

    /** 已记录的真实 Dialog 窗口根（decorView）。只拦这些窗口，绝不误伤 Activity 主界面。 */
    private val dialogDecorViews = java.util.concurrent.CopyOnWriteArraySet<View>()

    private val addViewHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            if (!enabled) return
            val child = param.args.getOrNull(0) as? View ?: return
            val host = param.thisObject as? ViewGroup ?: return

            val keywords = ViewScanner.splitKeywords(keyword)
            val idArr = ViewScanner.splitKeywords(ids)
            if (keywords.isEmpty() && idArr.isEmpty()) {
                // 没设条件：禁用所有对话框——只拦窗口根的添加，避免误伤普通布局
                if (!isWindowRoot(host)) return
                intercept(param, host, child); return
            }
            // 全检模式：任何 ViewGroup 都查；单检模式：只查窗口根
            if (!fullScanMode && !isWindowRoot(host)) return
            if (ViewScanner.containsKeyword(child, keywords) || ViewScanner.containsId(child, idArr)) {
                intercept(param, host, child)
            } else if (isWindowRoot(host)) {
                // 窗口根添加的 view，即使当前不匹配，也注册一帧延迟复查（内容可能异步填充）
                scheduleLateCheck(host, child, keywords, idArr)
            }
        }
    }

    /**
     * 判断是否【弹窗】窗口根：host 是已记录的 Dialog decorView，或其父链上有一个。
     *
     * 用「Dialog.show 时记录的 decorView」判定，比 context 解包可靠——
     * Activity 与 Dialog 的 DecorView context 都是 WindowContextImpl，解包无法区分；
     * 而 Activity 主界面的 decorView 永远不会被记录，绝不误拦。
     */
    private fun isWindowRoot(vg: ViewGroup): Boolean {
        var v: View? = vg
        while (v != null) {
            if (dialogDecorViews.contains(v)) return true
            v = v.parent as? View
        }
        return false
    }

    private fun intercept(param: XC_MethodHook.MethodHookParam, host: ViewGroup, child: View) {
        param.result = null   // 阻止 addView 执行
        if (toast) showTextTip(child, "一个对话框被禁用")
        Module.log("AlertDisableHook: blocked addView to ${host.javaClass.simpleName}")
    }

    private fun scheduleLateCheck(host: ViewGroup, child: View, keywords: Array<String>, idArr: Array<String>) {
        if (pendingRemoval.contains(host)) return
        pendingRemoval.add(host)
        child.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                child.viewTreeObserver.removeOnPreDrawListener(this)
                if (enabled && pendingRemoval.remove(host) &&
                    (ViewScanner.containsKeyword(child, keywords) || ViewScanner.containsId(child, idArr))
                ) {
                    ViewScanner.mainHandler.post {
                        runCatching { host.removeView(child) }
                        Module.log("AlertDisableHook: late-removed a dialog view")
                        if (toast) showTextTip(child, "一个对话框被禁用")
                    }
                }
                return true
            }
        })
    }

    private fun showTextTip(v: View, msg: String) {
        runCatching {
            android.widget.Toast.makeText(v.context.applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
