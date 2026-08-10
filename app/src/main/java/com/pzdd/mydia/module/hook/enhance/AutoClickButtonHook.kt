package com.pzdd.mydia.module.hook.enhance

import android.view.View
import android.view.WindowManager
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 自动点击按钮。对应 Dia 的 click_btn + ClickBtnHook。
 *
 * 描述：「When a button containing a specified keyword or id appears on a screen, click it automatically」
 * 原理：hook `WindowManagerImpl.addView`（每个新窗口/弹层都会走这里），
 * 拿到新加进来的 root view，用 [ViewScanner] 按关键字/id 找按钮并 performClick。
 * 同时挂一个 PreDraw 监听（view 真正布局完才点，命中率更高）。
 *
 * 开关与参数（对齐 Dia mod_ex_btn）：
 *  - click_btn : 总开关
 *  - click_btn_keyword / click_btn_id : 匹配条件（空格分隔，关键字支持正则）
 *  - click_delay_ms : 点击延迟（毫秒，给目标 App 反应时间）
 *  - click_time : 点击次数（0 = 一直点，>0 = 点 N 次）
 *  - click_btn_tip : 点击时弹 Toast 提示
 */
class AutoClickButtonHook : DiaHookEntry() {

    private var keywords: Array<String> = emptyArray()
    private var ids: Array<String> = emptyArray()
    private var delayMs: Long = 0
    private var times: Int = 0  // 0=无限
    private var tip: Boolean = false
    private var clickedWindows = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<View, Boolean>())

    override fun installImpl() {
        if (!prefs.getBoolean("click_btn", false)) {
            Module.log("AutoClickButtonHook: disabled"); return
        }
        keywords = ViewScanner.splitKeywords(prefs.getString("click_btn_keyword", ""))
        ids = ViewScanner.splitKeywords(prefs.getString("click_btn_id", ""))
        delayMs = try { prefs.getString("click_delay_ms", "0")?.toIntOrNull()?.toLong() ?: 0 } catch (_: Throwable) { 0 }
        times = try { prefs.getString("click_time", "0")?.toIntOrNull() ?: 0 } catch (_: Throwable) { 0 }
        tip = prefs.getBoolean("click_btn_tip", false)

        if (keywords.isEmpty() && ids.isEmpty()) {
            Module.log("AutoClickButtonHook: no keyword/id set, skip"); return
        }

        // hook 所有 addView（WindowManagerImpl 继承自 WindowManager，参数签名 (View, LayoutParams)）
        XposedBridge.hookAllMethods(WindowManager::class.java, "addView", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val view = param.args.getOrNull(0) as? View ?: return
                scanAndClick(view)
                // 再挂 PreDraw：等子 view 布局好后扫一次（很多按钮是异步 inflate 的）
                view.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        view.viewTreeObserver.removeOnPreDrawListener(this)
                        scanAndClick(view); return true
                    }
                })
            }
        })
        Module.log("AutoClickButtonHook: hook addView OK (kw=${keywords.size}, id=${ids.size}, delay=${delayMs}ms)")
    }

    private fun scanAndClick(root: View) {
        if (clickedWindows.contains(root) && times > 0) return
        val n1 = if (keywords.isNotEmpty()) ViewScanner.clickByKeyword(root, keywords, delayMs) else 0
        val n2 = if (ids.isNotEmpty()) ViewScanner.clickById(root, ids, delayMs) else 0
        val total = n1 + n2
        if (total > 0) {
            clickedWindows.add(root)
            Module.log("AutoClickButtonHook: clicked $total button(s) in ${root.javaClass.simpleName}")
            if (tip) showTip(root, "自动点击了 $total 个按钮")
        }
    }

    private fun showTip(v: View, msg: String) {
        runCatching {
            android.widget.Toast.makeText(v.context.applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
