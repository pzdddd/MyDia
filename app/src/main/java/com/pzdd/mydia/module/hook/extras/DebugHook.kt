package com.pzdd.mydia.module.hook.extras

import android.os.Debug
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge

/**
 * 反调试。对应 Dia 的 DebugHook（反调试部分，不含 AppPicker 系统 UI 联动）。
 *
 * 拦截对调试器状态的探测，让 App 以为没有调试器 attach：
 *  - `Debug.isDebuggerConnected` → false
 *  - `Debug.waitingForDebugger` → false
 *  - `android.os.Process.myTid()` 相关的 ptrace 检查在 native 层，本 hook 处理 Java 层。
 *
 * 注意：反调试功能本身属于双用途（可用于检测环境也可用于逃避检测）。
 * 这里仅实现标准 Xposed 模块常见的反调试伪装，目标为教育/调试场景。
 *
 * SP key：debug(总开关) / debug_force(可选：isDebuggable 也返回 false)
 */
class DebugHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("debug", false)) return

        runCatching {
            XposedBridge.hookAllMethods(
                Debug::class.java,
                "isDebuggerConnected",
                XC_MethodReplacement.returnConstant(false)
            )
        }
        runCatching {
            XposedBridge.hookAllMethods(
                Debug::class.java,
                "waitingForDebugger",
                XC_MethodReplacement.returnConstant(false)
            )
        }
        if (prefs.getBoolean("debug_force", false)) {
            runCatching {
                XposedBridge.hookAllMethods(
                    Debug::class.java,
                    "isDebuggable",
                    XC_MethodReplacement.returnConstant(false)
                )
            }
        }
        Module.log("DebugHook ACTIVE (isDebuggerConnected -> false).")
    }
}
