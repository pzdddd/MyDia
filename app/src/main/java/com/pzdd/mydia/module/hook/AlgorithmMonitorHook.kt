package com.pzdd.mydia.module.hook

import com.pzdd.mydia.algorithm.AlgorithmHookManager
import com.pzdd.mydia.module.Module

/**
 * 算法监控触发器。对应 Dia 里在 Module 流程里调用 AlgorithmHookManager.m11828 的那部分。
 *
 * 监控点（MessageDigest/Mac/Cipher/Base64）都是系统类，不依赖目标 App 的类加载，
 * 所以可以直接在 handleLoadPackage 时就装钩子——但为了拿到 sendBroadcast 所需的进程上下文，
 * 我们仍走 ApplicationHook.onReady，确保 ActivityThread.currentApplication() 已就绪。
 *
 * 开关：prefs 里 `algorithm_monitor` = true 时启用。
 */
class AlgorithmMonitorHook : DiaHook(), ApplicationHook.OnAppReady {

    override fun install() {
        if (!prefs.getBoolean("algorithm_monitor", false)) {
            Module.log("AlgorithmMonitorHook: disabled")
            return
        }
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady(this)
    }

    override fun onReady(ctx: android.content.Context) {
        runCatching { AlgorithmHookManager.start() }
            .onFailure { Module.err("AlgorithmMonitorHook start failed", it) }
    }
}
