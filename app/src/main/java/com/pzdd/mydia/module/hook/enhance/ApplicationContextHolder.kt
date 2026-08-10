package com.pzdd.mydia.module.hook.enhance

import android.content.Context

/**
 * 注入侧 Application Context 持有者。
 *
 * 增强模式多个 hook（AppEntryHook 查 PackageManager、各 hook 弹 Toast 等）需要 Context，
 * 但它们不一定订阅 ApplicationHook.onReady。本对象在 [EnhanceModule] init 时缓存
 * 目标 App 的 Application，供全局取用。
 *
 * 【修复】用强引用 + 直接存 ctx：
 *  - 之前用 WeakReference(ctx.applicationContext) —— Application.attach 阶段的 base
 *    context 的 applicationContext 可能为 null/未初始化，导致 set 后 get 立即返回 null，
 *    增强子 hook 全部被跳过（registerEntry skipped (ctx null)）。
 *  - 强引用存 Application 进程级 context，生命周期与进程一致，无泄漏风险。
 */
object ApplicationContextHolder {
    @Volatile private var ctx: Context? = null
    fun set(ctx: Context) { this.ctx = ctx }
    fun get(): Context? = ctx
}
