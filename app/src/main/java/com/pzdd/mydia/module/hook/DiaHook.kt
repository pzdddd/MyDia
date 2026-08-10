package com.pzdd.mydia.module.hook

import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import java.util.concurrent.ConcurrentHashMap

/**
 * 所有功能 Hook 的抽象基类。对应 Dia 的 dialog.box.hook.DiaHook。
 *
 * 设计精髓（照搬 Dia）：
 *  - 子类在 [install] 里装自己的钩子；
 *  - 通过 [register] 统一注册、缓存、幂等；
 *  - 通过 [get] 取已注册实例（常用：拿 ApplicationHook）。
 *
 * 加一个新功能 = 新建一个 `XxxHook extends DiaHook` + 在 Module.onLoadPackage 里
 * `DiaHook.register(XxxHook)` 一行。
 */
abstract class DiaHook : XC_MethodHook() {

    protected val appPrefs get() = Module.appPrefs
    protected val globalPrefs get() = Module.globalPrefs
    /** 功能配置统一入口（per-app）。所有功能开关从该 App 自己的 SP 文件读。 */
    protected val prefs get() = Module.appPrefs
    protected val classLoader get() = Module.classLoader!!

    /** 子类实现：在这里 findAndHookMethod / hookAllMethods */
    protected abstract fun install()

    companion object {
        private val cache = ConcurrentHashMap<Class<out DiaHook>, DiaHook>()

        /** 实例化每个 hook 并调用 install()（幂等：同一类只装一次） */
        fun register(vararg classes: Class<out DiaHook>) {
            for (c in classes) {
                if (cache.containsKey(c)) continue
                try {
                    val h = c.getDeclaredConstructor().newInstance()
                    Module.log("loading hook module: ${c.simpleName}")
                    h.install()
                    cache[c] = h
                } catch (t: Throwable) {
                    Module.err("register ${c.simpleName} failed", t)
                }
            }
        }

        /** 取已注册实例。典型用法：DiaHook.get(ApplicationHook::class.java) */
        @Suppress("UNCHECKED_CAST")
        fun <T : DiaHook> get(cls: Class<T>): T? = cache[cls] as? T
    }
}
