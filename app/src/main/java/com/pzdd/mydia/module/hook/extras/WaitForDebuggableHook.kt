package com.pzdd.mydia.module.hook.extras

import android.app.Application
import android.os.Debug
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 等待调试器附加。对应 Dia 的 WaitForDebuggableHook + wait_for_debuggable。
 *
 * 用途：App 启动时阻塞，等 Android Studio/ddms 的调试器 attach 上来再继续，
 * 方便调试 Application 的早期逻辑（ onCreate / attachBaseContext 等容易错过断点的地方）。
 *
 * 实现：hook Application.onCreate，调 Debug.waitForDebugger() 阻塞。
 * select_process：多选进程，只有目标进程才阻塞（防所有子进程都卡住）。
 * app_debuggable_mode：若 App 非 debuggable，需配合它让进程可调试（本骨架仅标记，需 LSPosed 作用域勾选）。
 *
 * SP key：wait_for_debuggable(总开关) / select_process(多选包名) / app_debuggable_mode
 */
class WaitForDebuggableHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("wait_for_debuggable", false)) return
        val pkg = Module.packageName ?: ""
        val selected = prefs.getStringSet("select_process", emptySet()) ?: emptySet()
        // 选中了特定进程才生效；没选则对所有进程生效
        if (selected.isNotEmpty() && pkg !in selected) return

        XposedBridge.hookAllMethods(Application::class.java, "onCreate", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                Module.log("WaitForDebuggable: waiting for debugger on $pkg ...")
                Debug.waitForDebugger()
                Module.log("WaitForDebuggable: debugger attached, continue")
            }
        })
        if (prefs.getBoolean("app_debuggable_mode", false)) {
            Module.log("WaitForDebuggable: app_debuggable_mode on (需 App 可调试，否则用 LSPosed 勾选作用域)")
        }
        Module.log("WaitForDebuggable: installed pkg=$pkg")
    }
}
