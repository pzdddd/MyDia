package com.pzdd.mydia.module.hook.enhance

import android.app.Activity
import android.view.KeyEvent
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference

/**
 * 按键触发器。对应 Dia 的 dialog.box.hook.KeyEventHook + com.mhook.dialog.task.hook.ShakeHook。
 *
 * 增强/退出类功能（禁用对话框、禁止退出、强制结束 Activity）支持【运行时按键 toggle】——
 * 这样用户不用每次回 MyDia 改配置，直接在目标 App 里按组合键就能开关。
 *
 * 7 种手势（对齐 Dia condition_value）：
 *   0 双击音量减 | 1 双击返回 | 2 三击音量减 | 3 三击返回
 *   4 双击音量加 | 5 长按返回 | 6 同时按音量加减
 *
 * SP 配置：
 *   alert_enabled / exit_enabled / activity_enabled(int, -1=不绑定) = 手势 id
 *   alert_auto / exit_auto : App 启动时默认开/关（开关的【初值】，toggle 改的是运行时值）
 *
 * 联动：toggle alert 调 [AlertDisableHook.toggle]；toggle exit 改 [exitEnabled]（ExitHook 读它）；
 * activity 直接 finish 当前 Activity。
 */
class KeyTriggerHook : DiaHookEntry() {

    /** 运行时「禁止退出」开关（本类维护，供 ExitHook 读取） */
    @Volatile var exitEnabled: Boolean = false

    @Volatile private var currentActivity: WeakReference<Activity>? = null

    // 触发配置（手势 id，-1=不绑定）
    private var triggerActivity = -1
    private var triggerExit = -1
    private var triggerAlert = -1

    // 手势识别状态
    private var lastVolDown = 0L
    private var lastVolUp = 0L
    private var lastBack = 0L
    private var volDownCount = 0
    private var backCount = 0
    private var lastVolDownForPair = 0L

    override fun installImpl() {
        triggerActivity = parseInt("activity_enabled")
        triggerExit = parseInt("exit_enabled")
        triggerAlert = parseInt("alert_enabled")

        val needKey = triggerActivity >= 0 || triggerExit >= 0 || triggerAlert >= 0
        if (!needKey) { Module.log("KeyTriggerHook: no trigger bound, skip"); return }

        // 初始值
        exitEnabled = prefs.getBoolean("exit_auto", false)
        // hook Activity 的按键 + 生命周期
        XposedHelpers.findAndHookMethod(Activity::class.java, "onKeyDown",
            Int::class.javaPrimitiveType, KeyEvent::class.java, onKeyDownHook)
        XposedHelpers.findAndHookMethod(Activity::class.java, "onKeyLongPress",
            Int::class.javaPrimitiveType, KeyEvent::class.java, onKeyLongPressHook)
        XposedBridge.hookAllMethods(Activity::class.java, "onResume", lifecycleHook(true))
        XposedBridge.hookAllMethods(Activity::class.java, "onPause", lifecycleHook(false))
        Module.log("KeyTriggerHook: triggers activity=$triggerActivity exit=$triggerExit alert=$triggerAlert")
    }

    private fun parseInt(key: String): Int =
        try { prefs.getString(key, "-1")?.toInt() ?: -1 } catch (_: Throwable) { -1 }

    private fun lifecycleHook(resume: Boolean) = object : XC_MethodHook() {
        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
            val act = param.thisObject as? Activity ?: return
            if (resume) currentActivity = WeakReference(act)
            else currentActivity = null
        }
    }

    private val onKeyDownHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            val keyCode = param.args[0] as Int
            val now = System.currentTimeMillis()
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    // 双击（200-2000ms）
                    if (now - lastVolDown in 200..2000) { fireTrigger(0); lastVolDown = 0 }
                    else lastVolDown = now
                    // 三击（2000ms 内 3 次）
                    volDownCount = if (now - lastVolDownForPair > 2000) 1 else volDownCount + 1
                    lastVolDownForPair = now
                    if (volDownCount == 3) { fireTrigger(2); volDownCount = 0 }
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (now - lastBack in 200..2000) { fireTrigger(1); lastBack = 0 }
                    else lastBack = now
                    backCount = if (now - lastBack > 2000) 1 else backCount + 1
                    if (backCount == 3) { fireTrigger(3); backCount = 0 }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (now - lastVolUp in 200..2000) fireTrigger(4)
                    lastVolUp = now
                    // 同时按音量加减：volUp 后短时间内有 volDown
                    if (now - lastVolDown in -400..400) fireTrigger(6)
                }
            }
        }
    }

    private val onKeyLongPressHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            val keyCode = param.args[0] as Int
            if (keyCode == KeyEvent.KEYCODE_BACK) fireTrigger(5)
        }
    }

    /** 命中手势 id → 执行绑定的动作 */
    private fun fireTrigger(triggerId: Int) {
        Module.log("KeyTrigger: gesture=$triggerId")
        if (triggerId == triggerActivity) {
            currentActivity?.get()?.let {
                runCatching { it.finish() }
                Module.log("KeyTrigger: finished current Activity")
            }
        }
        if (triggerId == triggerExit) {
            exitEnabled = !exitEnabled
            Module.log("KeyTrigger: exit toggled -> $exitEnabled")
            toast("禁止退出已${if (exitEnabled) "开启" else "关闭"}")
        }
        if (triggerId == triggerAlert) {
            alertDisableHook?.let {
                it.toggle()
                toast("禁用对话框已${if (it.active) "开启" else "关闭"}")
            }
        }
    }

    private fun toast(msg: String) {
        val act = currentActivity?.get() ?: return
        runCatching {
            android.widget.Toast.makeText(act.applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** 由 EnhanceModule 注入，用于 toggle alert */
    @Volatile var alertDisableHook: AlertDisableHook? = null
}
