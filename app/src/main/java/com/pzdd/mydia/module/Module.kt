package com.pzdd.mydia.module

import android.content.Context
import com.pzdd.mydia.module.hook.AlgorithmMonitorHook
import com.pzdd.mydia.module.hook.ApplicationHook
import com.pzdd.mydia.module.hook.DialogCancelHook
import com.pzdd.mydia.module.hook.DiaHook
import com.pzdd.mydia.module.hook.DisableExitHook
import com.pzdd.mydia.module.hook.RemoteConsoleLogHook
import com.pzdd.mydia.module.hook.enhance.EnhanceModule
import com.pzdd.mydia.module.hook.extras.AdvancedFeaturesModule
import com.pzdd.mydia.module.hook.extras.AntiDetectionModule
import com.pzdd.mydia.module.hook.extras.DevModule
import com.pzdd.mydia.module.hook.extras.FakeModule
import com.pzdd.mydia.module.hook.extras.MiscModule
import com.pzdd.mydia.module.hook.extras.NotifyModule
import com.pzdd.mydia.module.hook.FridaHook
import com.pzdd.mydia.module.hook.MethodRewriteHook
import com.pzdd.mydia.module.hook.ShellMonitorHook
import com.pzdd.mydia.module.hook.ToastDisableHook

/**
 * 注入侧的全局单例。缓存当前注入目标的信息、配置、日志。
 * 对应 Dia 的 com.mhook.dialog.Module。
 *
 * 运行环境：本类跑在【被注入的目标 App 进程】里，不是 com.pzdd.mydia 自身进程。
 * 因此严禁引用 com.pzdd.mydia.R 或任何 App 端的资源类——目标进程里没有这些 ID。
 *
 * 配置模型（per-app）：
 *  - 全局 SP `digXposed`：仅 [switchModule] 总开关 + 日志开关 + 自激活标志
 *  - 每 App 一份 SP（文件名 = 包名）：该 App 的 [enabled] 总开关 + 所有功能配置
 *    UI 侧在「应用」页给每个 App 开关、点进去配功能，写的就是这份 SP。
 */
object Module {

    /** Dia App 的包名（写 SP 用的 world-readable 文件就在这个包的数据目录下） */
    const val HOST_PACKAGE = "com.pzdd.mydia"

    /** 全局 SP 文件名，存模块总开关 / 日志开关 / 自激活标志 */
    const val PREFS_GLOBAL = "digXposed"

    /** per-app 总开关的 key（写在每个 App 自己的 SP 文件里） */
    const val KEY_APP_ENABLED = "enabled"

    /** 自激活标志的 key（写在全局 SP） */
    const val KEY_SELF_ACTIVE = "self_active"
    const val KEY_SELF_ACTIVE_TIME = "self_active_time"
    const val KEY_LAST_INJECT_PKG = "last_inject_pkg"
    const val KEY_LAST_INJECT_TIME = "last_inject_time"

    lateinit var packageName: String
        private set
    lateinit var processName: String
        private set
    var classLoader: ClassLoader? = null
        private set

    /** 全局配置（switchModule / log_enable / 自激活标志） */
    lateinit var globalPrefs: PrefsFile
        private set

    /** 按目标 App 存储的配置（每个被 hook 的 App 一份，文件名 = 包名） */
    lateinit var appPrefs: PrefsFile
        private set

    @Volatile var detailLog: Boolean = true
        private set

    fun onLoadPackage(pkg: String, proc: String, cl: ClassLoader) {
        packageName = pkg
        processName = proc
        classLoader = cl

        // 每次 reload，读到用户最新配置
        // 优先 libxposed Remote Preferences（daemon binder，绕过 SELinux），回退本地文件
        globalPrefs = PrefsFile.of(PREFS_GLOBAL).apply { reload() }
        appPrefs = PrefsFile.of(pkg).apply { reload() }

        detailLog = globalPrefs.getBoolean("log_enable", true)
        log("injected: process=$proc pkg=$pkg")

        // 自身进程不装功能 hook（激活检测已改由 ActivationManager 通过 LSPosed binder 完成，
        // 不再需要 hook 自身写 SP 标志，也不需要把自身加入作用域）
        if (pkg == HOST_PACKAGE) {
            log("self-inject: skip feature hooks for host package")
            return
        }

        // 全局总开关
        if (!globalPrefs.getBoolean("switchModule", false)) {
            log("module disabled by global switchModule.")
            return
        }

        // ===== per-app 总开关：「应用」页右边那个开关 =====
        if (!appPrefs.getBoolean(KEY_APP_ENABLED, false)) {
            log("app $pkg not enabled in Apps page, skip.")
            return
        }

        // ApplicationHook 必须最先注册——其它 Hook 依赖它来等 Application 就绪
        DiaHook.register(ApplicationHook::class.java)

        // 注册所有功能 Hook（加新功能 = 这里加一行）
        DiaHook.register(
            DialogCancelHook::class.java,
            DisableExitHook::class.java,
            ToastDisableHook::class.java,
            ShellMonitorHook::class.java,
            MethodRewriteHook::class.java,
            FridaHook::class.java,
            AlgorithmMonitorHook::class.java,
            RemoteConsoleLogHook::class.java,   // 远程日志控制台
            // ============ 增强模式六大分类（对应 Dia mod_ex_*）============
            EnhanceModule::class.java,           // 对话框/按钮/Activity 改写
            FakeModule::class.java,              // 模拟与伪装
            NotifyModule::class.java,            // 通知与提示
            AntiDetectionModule::class.java,     // 反检测
            MiscModule::class.java,              // 大杂烩
            AdvancedFeaturesModule::class.java,  // 高级功能
            DevModule::class.java,               // 开发者
        )
    }

    /** 统一日志，走 XposedBridge（logcat 里搜 MyDia） */
    fun log(msg: String) {
        if (detailLog) de.robv.android.xposed.XposedBridge.log("[MyDia] $msg")
    }

    fun err(msg: String, t: Throwable) {
        de.robv.android.xposed.XposedBridge.log("[MyDia] $msg\n${t.stackTraceToString()}")
    }
}
