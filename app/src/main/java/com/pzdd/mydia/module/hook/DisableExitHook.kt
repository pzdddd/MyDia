package com.pzdd.mydia.module.hook

import android.app.Activity
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 禁止目标 App 退出。
 *
 * 对应 Dia 的退出拦截（disable_exit / exit 两个开关入口）：
 *  - `disable_exit`（基础功能页顶层项）
 *  - `exit`（大杂烩分类）
 *
 * 拦截点：
 *  - `Activity.onBackPressed` → no-op（返回键无效）
 *  - `Activity.finish` → 阻断（所有 finish 无效，含 finishAndRemoveTask 等间接调用）
 *  - `System.exit` / `Runtime.halt` / `Process.killProcess` → 抛 SecurityException 阻断
 *
 * 注意：拦截 System.exit 属于「硬禁止」，个别 App 退出逻辑异常时会卡住，属预期行为
 * （对齐 Dia 原版）。与 KeyTriggerHook 的 exitEnabled 互不干扰（那是按键触发的运行时行为）。
 */
class DisableExitHook : DiaHook() {

    override fun install() {
        val basic = prefs.getBoolean("disable_exit", false)
        val misc = prefs.getBoolean("exit", false)
        if (!basic && !misc) {
            Module.log("DisableExitHook disabled.")
            return
        }

        // 1) 返回键：no-op
        runCatching {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onBackPressed",
                de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING
            )
        }

        // 2) finish：阻断（保留 Dialog.finish？Dialog 没有 finish，安全）
        runCatching {
            XposedBridge.hookAllMethods(Activity::class.java, "finish", blockHook("Activity.finish"))
        }
        runCatching {
            XposedBridge.hookAllMethods(Activity::class.java, "finishAndRemoveTask", blockHook("Activity.finishAndRemoveTask"))
        }

        // 3) 进程级退出：抛异常阻断（比 returnConstant 更稳，防止静默吞掉后状态错乱）
        runCatching { XposedBridge.hookAllMethods(System::class.java, "exit", blockHook("System.exit")) }
        runCatching { XposedBridge.hookAllMethods(Runtime::class.java, "halt", blockHook("Runtime.halt")) }
        runCatching { XposedBridge.hookAllMethods(Process::class.java, "killProcess", blockHook("Process.killProcess")) }

        Module.log("DisableExitHook ACTIVE (finish/back/exit blocked).")
    }

    private fun blockHook(tag: String) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            param.result = null
            Module.log("DisableExitHook: blocked $tag")
        }
    }
}
