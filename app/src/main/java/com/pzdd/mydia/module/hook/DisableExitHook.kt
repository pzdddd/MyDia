package com.pzdd.mydia.module.hook

import android.app.Activity
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 禁止目标 App 退出。
 *
 * 用途：对付「检测到环境异常（root / 版本旧 / 不适配）就自行 finish / System.exit
 * 退掉」的 App，把它强留在前台。
 *
 * 两个开关入口（任一开启即生效）：
 *  - `disable_exit`（基础功能页顶层项）
 *  - `exit`（大杂烩分类）
 *
 * 拦截点（激进策略）：
 *  - **进程级退出**（[System.exit] / [Runtime.exit] / [Runtime.halt] / [Process.killProcess]）
 *    → 抛 RuntimeException 打断调用栈（仅 `param.result = null` 拦不住 native exit 系统调用，
 *    必须抛异常才能真正阻止 JVM/进程终止；代价是 App 调用方会收到异常，可能 ANR——这正是
 *    「不准退」的预期效果）。
 *  - **finish 家族**（Activity.finish / finishAndRemoveTask / finishAffinity /
 *    moveTaskToBack / moveTaskToEnd）→ return null 阻断（这些是 Java 层调度，return null 即可挡住）。
 *  - 返回键 Activity.onBackPressed → no-op（Android 13+ 期望式手势部分失效，无害保留）。
 *
 * 与 KeyTriggerHook.exitEnabled 互不干扰（那是按键触发的运行时行为）。
 */
class DisableExitHook : DiaHook() {

    override fun install() {
        val basic = prefs.getBoolean("disable_exit", false)
        val misc = prefs.getBoolean("exit", false)
        if (!basic && !misc) {
            Module.log("DisableExitHook disabled.")
            return
        }

        // 1) 进程级退出：必须抛异常才能真正打断（native exit() 已在路径上时 return null 拦不住）
        runCatching { XposedBridge.hookAllMethods(System::class.java, "exit", throwHook("System.exit")) }
        runCatching { XposedBridge.hookAllMethods(Runtime::class.java, "exit", throwHook("Runtime.exit")) }
        runCatching { XposedBridge.hookAllMethods(Runtime::class.java, "halt", throwHook("Runtime.halt")) }
        runCatching { XposedBridge.hookAllMethods(Process::class.java, "killProcess", throwHook("Process.killProcess")) }

        // 2) finish 家族：Java 层调度，return null 阻断即可（Dialog 无 finish，不会误伤弹窗）
        runCatching { XposedBridge.hookAllMethods(Activity::class.java, "finish", blockHook("Activity.finish")) }
        runCatching { XposedBridge.hookAllMethods(Activity::class.java, "finishAndRemoveTask", blockHook("Activity.finishAndRemoveTask")) }
        runCatching { XposedBridge.hookAllMethods(Activity::class.java, "finishAffinity", blockHook("Activity.finishAffinity")) }
        // moveTaskToBack / moveTaskToBottom：App 检测异常后把自己退到后台（等效离开前台）
        runCatching { XposedBridge.hookAllMethods(Activity::class.java, "moveTaskToBack", blockHook("Activity.moveTaskToBack")) }
        runCatching { XposedBridge.hookAllMethods(Activity::class.java, "moveTaskToEnd", blockHook("Activity.moveTaskToEnd")) }

        // 3) 返回键：no-op（Android 13+ 的 OnBackInvokedCallback 不走 onBackPressed，此 hook 部分失效但无害）
        runCatching {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onBackPressed",
                de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING
            )
        }

        Module.log("DisableExitHook ACTIVE (exit/finish/back all blocked, aggressive mode).")
    }

    /** 阻断 Java 层调度方法：假装已正常返回（result 置 null/默认）。 */
    private fun blockHook(tag: String) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            param.result = null
            Module.log("DisableExitHook: blocked $tag")
        }
    }

    /**
     * 阻断进程级退出：抛异常打断调用栈。必须抛异常而非 return null——
     * System.exit/halt 的 native 路径一旦进入，return null 无法回滚。
     * 抛出的异常会被 App 的退出调用方捕获（或导致其线程崩溃），从而保住进程。
     */
    private fun throwHook(tag: String) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            Module.log("DisableExitHook: prevented $tag (threw to abort native exit)")
            throw RuntimeException("MyDia: $tag blocked by disable-exit")
        }
    }
}
