package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 「大杂烩」分类协调器。对应 Dia 的 mod_ex_misc.xml 整页。
 *
 * 下辖 14 个子 hook：
 *  - ClipboardDisableHook      剪贴板读写禁用（带关键字）
 *  - SensorDisableHook         禁用加速度计/陀螺仪
 *  - HideOnBackgroundHook      后台隐藏（最近任务不可见）
 *  - SoLibraryHook             禁用指定 so 加载
 *  - RandomFileHook            随机设备 id 文件内容
 *  - HttpProxyHook             设置 HTTP 代理
 *  - DisableNetworkOnStartHook 启动时短暂禁网
 *  - UiModeHook                强制深色/浅色模式
 *  - ShakeHook                 摇一摇检测/触发
 *  - WindowManagerHook         窗口操作监控/拦截
 *  - FileHook                  隐藏敏感文件
 *  - RuntimeHook               exec 监控 + 输出伪装
 *  - AndroidJsonHook           Gson/fastjson 序列化监控
 *  - ForceProxyHook            强制 HTTP 代理
 *  - HTTPRewriteHook           HTTP 请求/响应改写引擎
 *  - NativeHook                原生 hook 层（时间伪造/exit 拦截）
 *  - (DisableExitHook 已在主注册列表，对应 exit 开关)
 *  - (FakeXposedHook 在反检测模块)
 *
 * SP key：见各 hook 注释；总页是 mod_ex_misc.xml
 */
class MiscModule : DiaHook() {
    override fun install() {
        Module.log("MiscModule: registering misc hooks")
        DiaHook.register(
            ClipboardDisableHook::class.java,
            SensorDisableHook::class.java,
            HideOnBackgroundHook::class.java,
            SoLibraryHook::class.java,
            RandomFileHook::class.java,
            HttpProxyHook::class.java,
            DisableNetworkOnStartHook::class.java,
            UiModeHook::class.java,
            ShakeHook::class.java,
            WindowManagerHook::class.java,
            FileHook::class.java,
            RuntimeHook::class.java,
            AndroidJsonHook::class.java,
            ForceProxyHook::class.java,
            HTTPRewriteHook::class.java,
            NativeHook::class.java,
        )
    }
}
