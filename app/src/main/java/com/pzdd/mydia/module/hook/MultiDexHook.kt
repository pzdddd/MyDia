package com.pzdd.mydia.module.hook

import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 监听目标 App 加载新的 dex/ClassLoader。
 * 对应 Dia 的 dialog.box.hook.MultiDexHook。
 *
 * 很多 App（multidex / 插件化 / 热修复）的业务类不在主 dex 里，
 * handleLoadPackage 时还加载不到。本类 hook 了会触发 dex 加载的关键入口，
 * 每次有新 ClassLoader 出现就回调观察者——方法改写引擎等可在此时补装钩子。
 *
 * 设计为纯静态单例 + 观察者列表（不继承 DiaHook），由 [Module] 在
 * ApplicationHook 装好后调用 [init]。观察者通过 [addObserver] 注册即可。
 */
object MultiDexHook {

    fun interface Observer {
        fun onClassLoader(cl: ClassLoader)
    }

    private val observers = CopyOnWriteArrayList<Observer>()
    @Volatile private var inited = false

    /** 装钩子。幂等。建议在 ApplicationHook.onReady 之后调用。 */
    fun init(hostClassLoader: ClassLoader) {
        if (inited) return
        inited = true

        // hook DexClassLoader 构造：插件 dex 加载立即通知
        runCatching {
            val clazz = XposedHelpers.findClass("dalvik.system.DexClassLoader", hostClassLoader)
            XposedHelpers.findAndHookConstructor(
                clazz,
                String::class.java, String::class.java,
                String::class.java, ClassLoader::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        // 新建出来的 DexClassLoader 本身就是新 cl
                        notify(param.thisObject as ClassLoader)
                    }
                }
            )
        }

        // hook LoadedApk.getClassLoader：捕获系统为每个 apk 创建 cl 的时机
        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.app.LoadedApk", hostClassLoader,
                "getClassLoader",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        (param.result as? ClassLoader)?.let { notify(it) }
                    }
                }
            )
        }
    }

    /** 立即对当前主 ClassLoader 通知一轮（兜底，确保至少跑一次） */
    fun notifyCurrent(cl: ClassLoader) = notify(cl)

    /** 注册观察者（幂等） */
    fun addObserver(o: Observer) {
        if (o !in observers) observers += o
    }

    private fun notify(cl: ClassLoader) {
        Module.log("MultiDexHook: ClassLoader -> ${cl.javaClass.simpleName}")
        observers.forEach { runCatching { it.onClassLoader(cl) } }
    }
}
