package com.pzdd.mydia

import android.app.Application
import android.content.Context
import timber.log.Timber

/**
 * Dia App 自身的 Application（运行在 com.pzdd.mydia 进程）。
 *
 * - 在 [attachBaseContext] 最早期安装全局崩溃捕获器，确保任何线程崩溃都能落盘；
 * - onCreate 里初始化 Timber 日志；
 * - SP 统一用 MODE_PRIVATE 写（Android 13+ 禁止 WORLD_READABLE 模式，会崩），
 *   每次写入后手动 chmod 0644（见 [com.pzdd.mydia.ui.prefs.chmodPref]），
 *   注入侧 XSharedPreferences 直接 open 文件读，要求 0644。
 *   这里每次启动还做一次全量 chmod 0644 迁移，兜住历史遗留的 0600 文件。
 */
class App : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 最早期装崩溃捕获，避免错过 Application 阶段的异常
        CrashCatcher.install(this)
        // 把 shared_prefs 下所有模块 SP 文件 chmod 成 0644（幂等，几十个文件开销可忽略）
        com.pzdd.mydia.ui.prefs.migrateWorldReadable(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("MyDia App started.")
        // 官方标准激活检测：通过 libxposed-service 绑定 LSPosed binder 服务。
        // 无需把自身加入 Xposed 作用域，LSPosed Manager 会通过 XposedProvider 推送 binder。
        com.pzdd.mydia.module.ActivationManager.init()
    }
}
