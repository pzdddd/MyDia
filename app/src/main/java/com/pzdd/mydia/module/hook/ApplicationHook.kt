package com.pzdd.mydia.module.hook

import android.app.Application
import android.content.Context
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 等待目标 App 的 Application 就绪。
 * 对应 Dia 的 dialog.box.hook.ApplicationHook。
 *
 * 绝大多数 Hook 需要 Context / 目标类才能干活，但 handleLoadPackage 时 App 还没 attach。
 * 本类 hook `Application.attach(Context)`，在 attach 之后回调所有订阅者——
 * 此时拿到的 Context 可安全用于 getClassLoader、sendBroadcast、System.loadLibrary 等。
 */
class ApplicationHook : DiaHook() {

    fun interface OnAppReady {
        /** Application.attach 调用完成后触发，ctx 已就绪 */
        fun onReady(ctx: Context)
    }

    private val callbacks = CopyOnWriteArrayList<OnAppReady>()

    fun addOnAppReady(cb: OnAppReady) {
        if (cb !in callbacks) callbacks += cb
    }

    override fun install() {
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.args[0] as? Context ?: return
                        Module.log("Application ready: ${ctx.packageName}")
                        // 初始化多 dex 监听：此刻主 ClassLoader 已就绪，装 dex 相关钩子
                        runCatching { MultiDexHook.init(ctx.classLoader) }
                        callbacks.forEach { runCatching { it.onReady(ctx) } }
                    }
                }
            )
        } catch (t: Throwable) {
            Module.err("ApplicationHook install failed", t)
        }
    }
}
