package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 「开发者」分类协调器。对应 Dia 的 mod_ex_dev.xml 整页（Dia 最大的一个分类）。
 *
 * 下辖 12 个子 hook（shell/algorithm/frida 监控已在主注册列表，作为独立大功能）：
 *  - ActivityShowNameHook   Activity 名提示
 *  - FragmentShowNameHook   Fragment 名提示
 *  - ClearDataOnStartHook   启动清数据
 *  - WaitForDebuggableHook  等待调试器
 *  - IntentMonitorHook      Intent 监控
 *  - HttpMonitorHook        HTTP/Socket 监控
 *  - SqlMonitorHook         SQL 监控
 *  - PrintMethodStackHook   方法调用栈打印
 *  - InjectSuccessTipsHook  注入成功提示
 *  - ClassLoaderHook        类加载监控/隐藏
 *  - HideDexHook            隐藏 dex
 *  - DexInjectHook          dex 注入
 *  - SqlcipherHook          加密 SQL 监控（SQLCipher/WCDB）
 *  - TraceHook              方法级调用追踪（入参/出参/耗时）
 *
 * 已在 Module 主注册列表（不在本 Module 重复注册，避免双注册）：
 *  - ShellMonitorHook       (monitor_shell_switch)
 *  - AlgorithmMonitorHook   (monitor_algorithm_switch)
 *  - FridaHook              (code_inject)
 *
 * SP key：见各 hook 注释；总页是 mod_ex_dev.xml
 */
class DevModule : DiaHook() {
    override fun install() {
        Module.log("DevModule: registering developer hooks")
        DiaHook.register(
            ActivityShowNameHook::class.java,
            FragmentShowNameHook::class.java,
            ClearDataOnStartHook::class.java,
            WaitForDebuggableHook::class.java,
            IntentMonitorHook::class.java,
            HttpMonitorHook::class.java,
            SqlMonitorHook::class.java,
            SqlcipherHook::class.java,
            PrintMethodStackHook::class.java,
            InjectSuccessTipsHook::class.java,
            ClassLoaderHook::class.java,
            HideDexHook::class.java,
            DexInjectHook::class.java,
            TraceHook::class.java,
        )
    }
}
