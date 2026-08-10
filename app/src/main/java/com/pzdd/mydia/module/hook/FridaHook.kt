package com.pzdd.mydia.module.hook

import android.content.Context
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import java.io.File

/**
 * Frida 注入。对应 Dia 的 dialog.box.hook.FridaHook。
 *
 * 原理：在目标 App 的 Application.attach 之后，System.loadLibrary("frida-gadget")，
 * gadget.so 会在 App 数据目录读 frida-gadget.config（配置脚本来源 / 监听端口），
 * 然后注入 JS——可做比 Xposed 更灵活的运行时 hook、绕过 ssl pinning 等。
 *
 * Dia 把 frida-gadget.so 重命名成 libalertclose.so 放进目标 App 的 nativeLibraryDir。
 * 我们简化：直接从 MyDia 的 assets 释放到目标 App 的 files 目录再 load，无需改目标 App。
 *
 * 使用前置：
 *  1. 把 frida-gadget（arm64-v8a / armeabi-v7a）放到 app/src/main/jniLibs/<abi>/libfrida-gadget.so
 *  2. 把配置文件放到 assets/frida-gadget.config（或运行时由 UI 生成）
 *  3. App 端 prefs 打开 code_inject 开关
 *
 * 开关语义（对齐 Dia）：
 *  - code_inject : 总开关
 *  - frida_listen / select_active_process_listen_mode : 是否监听模式（不按进程过滤）
 *  - select_active_process : set，只在这些进程里注入（非监听模式下生效）
 */
class FridaHook : DiaHook(), ApplicationHook.OnAppReady {

    override fun install() {
        if (!prefs.getBoolean("code_inject", false)) {
            Module.log("FridaHook: disabled (code_inject=false)")
            return
        }
        // 进程过滤：监听模式 vs 指定进程集合
        val listenMode = prefs.getBoolean("frida_listen", false) == true
        if (listenMode) {
            val targetProc = prefs.getString("select_active_process_listen_mode", Module.packageName)
            if (targetProc != Module.processName) {
                Module.log("FridaHook: listen mode but proc mismatch ($targetProc != ${Module.processName}), skip")
                return
            }
        } else {
            val selected = prefs.getStringSet("select_active_process", emptySet()) ?: emptySet()
            if (selected.isNotEmpty() && Module.processName !in selected) {
                Module.log("FridaHook: process ${Module.processName} not in selected set, skip")
                return
            }
        }
        // 等 Application 就绪后才能安全 loadLibrary（需要 Context 拿 filesDir）
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady(this)
    }

    override fun onReady(ctx: Context) {
        inject(ctx)
    }

    /** 释放并加载 frida-gadget */
    private fun inject(ctx: Context) {
        try {
            // 1) 尝试直接 load（若目标 App 已自带 gadget，或之前已释放过）
            System.loadLibrary("frida-gadget")
            Module.log("FridaHook: loadLibrary(frida-gadget) OK")
            return
        } catch (ignored: UnsatisfiedLinkError) {
            // 首次注入：需要从 assets 释放
        } catch (t: Throwable) {
            Module.err("FridaHook: loadLibrary failed", t)
        }

        // 2) 检查 assets 是否真的打包了 gadget（体积大，按需自行放入）
        val hasSo = runCatching {
            ctx.assets.list("frida-gadget")?.any { it == "libfrida-gadget.so" } == true
        }.getOrDefault(false)
        if (!hasSo) {
            Module.err(
                "FridaHook: libfrida-gadget.so 未打包。请把对应架构的 gadget 放到 " +
                    "app/src/main/assets/frida-gadget/libfrida-gadget.so 后重新编译。",
                IllegalStateException("asset missing")
            )
            return
        }

        try {
            // 3) 从 MyDia assets 释放 gadget + config 到目标 App 的 filesDir
            val soFile = extractAsset(ctx, "frida-gadget/libfrida-gadget.so")
            val cfgFile = extractAsset(ctx, "frida-gadget/frida-gadget.config")
            // gadget 默认在自身同目录读 <soname>.config，文件名必须匹配
            val cfgTarget = File(soFile.parentFile, "libfrida-gadget.config")
            cfgFile.copyTo(cfgTarget, overwrite = true)
            System.load(soFile.absolutePath)
            Module.log("FridaHook: injected from ${soFile.absolutePath}")
        } catch (t: Throwable) {
            Module.err("FridaHook: inject failed", t)
        }
    }

    /** 把 assets 下的资源释放到 filesDir，返回释放后的 File */
    private fun extractAsset(ctx: Context, path: String): File {
        val out = File(ctx.filesDir, path)
        out.parentFile?.mkdirs()
        ctx.assets.open(path).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        // SO 必须可执行
        out.setExecutable(true, false)
        return out
    }
}
