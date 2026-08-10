package com.pzdd.mydia.module.hook.enhance

import android.content.Context
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 增强模式 Hook 的公共基类。
 *
 * 增强模式（mod_ex）针对 UI 元素（对话框/按钮/Activity）做改写，
 * 与基础 [DiaHook] 的区别：这些 Hook 大多需要 Application Context 才能完整工作
 * （查 PackageManager、弹 Toast、拿 ClassLoader 等），
 * 所以统一在 ApplicationHook.onReady 之后才 install，避免空指针。
 *
 * 配置来源说明：增强模式配置从【该 App 自己的 SP（appPrefs / prefs）】读，
 * 实现 per-app 隔离——在「应用」页点某个 App 配的增强功能只对该 App 生效。
 *
 * 子类实现 [installImpl]，基类负责 gating + 日志。
 */
abstract class DiaHookEntry : DiaHook(), com.pzdd.mydia.module.hook.ApplicationHook.OnAppReady {

    /** 子类的实际装钩子逻辑 */
    protected abstract fun installImpl()

    final override fun install() {
        // 等 Application 就绪后再装（需要 Context / 已加载的类）
        com.pzdd.mydia.module.hook.DiaHook.get(
            com.pzdd.mydia.module.hook.ApplicationHook::class.java
        )?.addOnAppReady(this)
    }

    final override fun onReady(ctx: Context) {
        runCatching { installImpl() }.onFailure { Module.err("${javaClass.simpleName} installImpl failed", it) }
    }
}
