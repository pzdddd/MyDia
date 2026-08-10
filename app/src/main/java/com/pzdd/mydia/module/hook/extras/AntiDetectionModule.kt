package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import com.pzdd.mydia.module.hook.extras.check.CheckManager

/**
 * 「反检测」分类协调器。对应 Dia 的 mod_ex_anti_detection.xml 整页。
 *
 * 下辖 11 个子 hook：
 *  - HideXposedHook    隐藏 Xposed/LSPosed 痕迹
 *  - HideRootHook      隐藏 Root（su/busybox/magisk/test-keys）
 *  - HideEmulatorHook  隐藏模拟器特征（Build.generic/sdk）
 *  - HideMultiAppHook  隐藏多开/分身（过滤容器包/进程）
 *  - VPNHook           隐藏 VPN 连接
 *  - LocationHook      GPS 位置伪造
 *  - DebugHook         反调试（isDebuggerConnected → false）
 *  - PackageNameHook   包名伪装（ActivityThread.currentPackageName）
 *  - ProtectHook       模块自身防护（从 PackageManager 抹掉模块包）
 *  - ThreadHook        线程名伪装
 *  - StackHook         调用栈过滤（隐藏模块帧）
 *  - FakeXposedHook    Xposed 类存在性伪装
 *  - CheckManager      环境检测记录（App 探测 root/hook/模拟器时打点）
 *
 * 注意：Dia 原版的 check 相关类 (MagiskCheckHook/FridaCheckHook 等) 都是空壳，
 * 真正的反检测矩阵在加密的 otherModEx 里。本实现基于通用反检测知识重写。
 *
 * SP key：hide / hide_xposed / hide_emulator / hide_multi_app / vpn / gps(+子项) /
 *          debug / package_name_fake / protect / thread_name_fake / stack_filter
 */
class AntiDetectionModule : DiaHook() {
    override fun install() {
        Module.log("AntiDetectionModule: registering anti-detection hooks")
        DiaHook.register(
            HideXposedHook::class.java,
            HideRootHook::class.java,
            HideEmulatorHook::class.java,
            HideMultiAppHook::class.java,
            VPNHook::class.java,
            LocationHook::class.java,
            DebugHook::class.java,
            PackageNameHook::class.java,
            ProtectHook::class.java,
            ThreadHook::class.java,
            StackHook::class.java,
            FakeXposedHook::class.java,
            CheckManager::class.java,
        )
    }
}
