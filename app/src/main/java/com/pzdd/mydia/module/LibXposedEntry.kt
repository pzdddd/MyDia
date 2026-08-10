package com.pzdd.mydia.module

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * **libxposed API 102 入口** + 自写旧 API compat 层。
 *
 * 注册方式：
 *  - `META-INF/xposed/java_init.list` = 本类全限定名
 *  - `META-INF/xposed/module.prop` = minApiVersion=93 + targetApiVersion=102
 *
 * **自写 compat 层（核心）**：libxposed API 102 模式不注入旧 API 兼容层（实测
 * NoClassDefFoundError），因此我们把 `de.robv.android.xposed.*` 的实现（XposedBridge /
 * XposedHelpers / XC_MethodHook / XC_MethodReplacement）【自己打包进 APK】——
 * 底层桥接到 libxposed 新 API 的 hook().intercept()。36 个 hook 的旧 API 代码
 * 零改动即可运行，模块身份保持 API 102。
 *
 * 桥接入口：[onModuleLoaded] 里调用 [de.robv.android.xposed.XposedBridge.attach] 注入
 * 本模块实例；[onPackageLoaded] 里委托 [Module.onLoadPackage]（内部用旧 API，经 compat
 * 层桥接）。
 */
class LibXposedEntry : XposedModule() {

    companion object {
        /** 宿主（模块自身）包名。 */
        const val HOST_PACKAGE = "com.pzdd.mydia"

        /** 当前模块实例（供 hook 代码调用 [XposedModule.log] 等）。 */
        @Volatile
        var instance: LibXposedEntry? = null
            private set
    }

    /**
     * 模块首次加载。缓存实例 + 注入 compat 层（XposedBridge 需要模块实例来调 hook()）。
     */
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        instance = this
        de.robv.android.xposed.XposedBridge.attach(this)
        log(
            Log.INFO,
            "MyDia",
            "[libxposed 102 compat] module loaded: process=${param.processName} " +
                "framework=${frameworkName}@${frameworkVersion}(${frameworkVersionCode}) " +
                "api=${apiVersion}"
        )
    }

    /**
     * 每个目标 App 包加载时回调。委托给统一注入逻辑
     * （旧 API 实现，经自写 compat 层桥接到 libxposed）。
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        val pkg = param.packageName
        val appInfo = param.applicationInfo
        val proc = appInfo.processName?.takeIf { it.isNotBlank() } ?: pkg
        @Suppress("NewApi", "KotlinRedundantDiagnosticSuppress")
        val cl = try {
            param.defaultClassLoader
        } catch (e: Throwable) {
            log(Log.WARN, "MyDia", "[libxposed 102] getDefaultClassLoader failed for $pkg: $e")
            return
        }
        runCatching {
            Module.onLoadPackage(pkg, proc, cl)
        }.onFailure { t ->
            log(Log.ERROR, "MyDia", "[libxposed 102] onLoadPackage failed: $pkg", t)
        }
    }
}
