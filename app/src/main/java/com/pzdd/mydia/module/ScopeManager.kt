package com.pzdd.mydia.module

import android.util.Log
import io.github.libxposed.service.XposedService

/**
 * LSPosed 作用域管理。
 *
 * 封装 [XposedService] 的 scope API，让 UI 能列出/添加/移除模块作用域里的 App，
 * 无需用户手动去 LSPosed Manager 勾选。
 *
 * - [getScope]：返回当前作用域里的包名列表
 * - [requestScope]：请求添加（触发 LSPosed 交互式确认流，结果走回调）
 * - [removeScope]：移除
 * - [requestScopeIfNeeded]：幂等地把单个包加入作用域（「应用」页开关联动）
 *
 * 依赖 [ActivationManager.service]（LSPosed binder）。未激活时所有操作返回空/失败。
 */
object ScopeManager {

    private const val TAG = "MyDia/Scope"

    /** 当前绑定的 LSPosed 服务（未激活时为 null）。 */
    private val service: XposedService? get() = ActivationManager.service

    /** 是否可用（已激活）。 */
    val isAvailable: Boolean get() = service != null

    /** 获取当前作用域里的全部包名。未激活返回空列表。 */
    fun getScope(): List<String> = runCatching {
        service?.getScope() ?: emptyList()
    }.getOrElse { e ->
        Log.w(TAG, "getScope failed: ${e.message}")
        emptyList()
    }

    /**
     * 请求把 [packages] 加入作用域。
     * LSPosed 会弹交互式确认；结果通过 [onApproved]/[onFailed] 回调。
     */
    fun requestScope(
        packages: List<String>,
        onApproved: (List<String>) -> Unit = {},
        onFailed: (String) -> Unit = {},
    ) {
        val svc = service
        if (svc == null) {
            Log.w(TAG, "requestScope skipped: module not activated")
            onFailed("模块未激活")
            return
        }
        Log.i(TAG, "requestScope: $packages")
        try {
            svc.requestScope(packages, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(pkgs: List<String>) {
                    Log.i(TAG, "scope request approved: $pkgs")
                    onApproved(pkgs)
                }

                override fun onScopeRequestFailed(reason: String) {
                    Log.w(TAG, "scope request failed: $reason")
                    onFailed(reason)
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "requestScope threw", t)
            onFailed(t.message ?: "unknown")
        }
    }

    /** 从作用域移除 [packages]。 */
    fun removeScope(packages: List<String>) {
        runCatching { service?.removeScope(packages) }
            .onFailure { Log.w(TAG, "removeScope failed: ${it.message}") }
    }

    /**
     * 幂等地把单个包请求加入作用域（供「应用」页开关联动）。
     *
     * 已激活且不在作用域时才发起 [requestScope]——LSPosed 会弹出确认通知，
     * 用户批准后该 App 进入作用域，无需手动去设置页添加。
     * 未激活 / 已在作用域则静默返回（不打扰、不重复弹窗）。
     */
    fun requestScopeIfNeeded(packageName: String) {
        if (!isAvailable) {
            Log.w(TAG, "requestScopeIfNeeded skipped: not activated")
            return
        }
        val scope = getScope()
        if (packageName in scope) {
            Log.i(TAG, "requestScopeIfNeeded: $packageName already in scope, skip")
            return
        }
        Log.i(TAG, "requestScopeIfNeeded: requesting $packageName (scope=${scope.size})")
        requestScope(listOf(packageName))
    }
}
