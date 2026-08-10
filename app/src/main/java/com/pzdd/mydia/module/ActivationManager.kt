package com.pzdd.mydia.module

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 模块激活状态管理器（官方标准机制）。
 *
 * 对应 `libxposed/example` 的 `App.kt` —— 通过 `XposedServiceHelper` 绑定 LSPosed 的
 * binder 服务判断模块是否激活。
 *
 * **机制**：
 *  1. App 自身进程（com.pzdd.mydia）启动时，LSPosed Manager 进程通过本 App 内由
 *     libxposed-service 声明的 `XposedProvider`（ContentProvider，authorities=包名.XposedService）
 *     的 `call("SendBinder")` 推送 binder 过来。
 *  2. `XposedServiceHelper.onBinderReceived` 缓存 binder。
 *  3. 调用 [registerListener] 后，回调 [onServiceBind] = **框架已安装 + 模块已启用** = 激活。
 *
 * **关键优势**：**不需要把模块自身加入 Xposed 作用域**。激活检测完全在 App 自身进程完成，
 * 通过 LSPosed Manager 进程下发的 binder 判断。这也符合官方 example 的设计。
 *
 * 这取代了之前「hook 自身写 SP 标志」的错误做法——那个方案需要把 com.pzdd.mydia
 * 加入作用域，而 LSPosed 通常不允许模块作用域包含自身。
 */
object ActivationManager {

    private const val TAG = "MyDia/Activation"

    /** 当前绑定的 LSPosed 服务（null = 未激活 / LSPosed 未安装 / 模块未启用）。 */
    @Volatile
    var service: XposedService? = null
        private set

    /**
     * 给 Compose 用的响应式状态。直接 `ActivationManager.activeState.value` 即可订阅。
     * - `Activated` = 已绑定 service（激活）
     * - `NotActivated` = 未绑定（未激活）
     */
    val activeState = mutableStateOf<ActivationState>(ActivationState.Unknown)

    private val listeners = CopyOnWriteArraySet<OnServiceStateListener>()

    /** 注册服务状态监听。重复注册幂等。 */
    fun registerListener(listener: OnServiceStateListener) {
        listeners.add(listener)
        // 立即用当前状态回调一次
        listener.onStateChanged(service)
    }

    fun unregisterListener(listener: OnServiceStateListener) {
        listeners.remove(listener)
    }

    /**
     * 在 [android.app.Application.onCreate] 里调用一次，启动 binder 监听。
     * 幂等（多次调用安全）。
     */
    fun init() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(svc: XposedService) {
                service = svc
                activeState.value = ActivationState.Activated(svc)
                Log.i(TAG, "Activated: framework=${svc.frameworkName}(${svc.frameworkVersionCode}) api=${svc.apiVersion}")
                listeners.forEach { runCatching { it.onStateChanged(svc) } }
                // 把本地 SP 配置同步到 LSPosed Remote Preferences（注入侧靠 binder 读配置，
                // 绕过 SELinux 文件隔离）。在后台线程做，避免卡 UI。
                Thread {
                    runCatching {
                        // ActivityThread 是 @hide 类，编译期不可引用——反射获取当前 Application
                        val ctx = currentApplication()
                        RemotePrefsSync.syncAll(ctx, svc)
                        Log.i(TAG, "Local prefs synced to remote (LSPosed database)")
                    }
                }.apply { isDaemon = true; name = "prefs-sync" }.start()
            }

            override fun onServiceDied(svc: XposedService) {
                service = null
                activeState.value = ActivationState.NotActivated
                Log.w(TAG, "Service died (framework disappeared)")
                listeners.forEach { runCatching { it.onStateChanged(null) } }
            }
        })
        // 短暂延迟后若仍未 bind，标记为未激活（避免一直 Unknown）
        Thread {
            Thread.sleep(1500)
            if (service == null && activeState.value == ActivationState.Unknown) {
                activeState.value = ActivationState.NotActivated
                Log.w(TAG, "Not activated: no binder received within 1.5s (LSPosed not installed or module not enabled)")
            }
        }.apply { isDaemon = true; name = "activation-timeout" }.start()
    }

    /** 激活状态。 */
    sealed interface ActivationState {
        /** 初始未知（正在等待 binder）。 */
        data object Unknown : ActivationState
        /** 未激活（超时未收到 binder，或 service died）。 */
        data object NotActivated : ActivationState
        /** 已激活，[service] 为绑定的 LSPosed 服务实例。 */
        data class Activated(val service: XposedService) : ActivationState
    }

    fun interface OnServiceStateListener {
        fun onStateChanged(service: XposedService?)
    }

    /** 反射拿当前 Application（ActivityThread 是 @hide 类，编译期不可引用）。 */
    private fun currentApplication(): android.app.Application {
        val atClass = Class.forName("android.app.ActivityThread")
        val method = atClass.getDeclaredMethod("currentApplication")
        return method.invoke(null) as android.app.Application
    }
}
