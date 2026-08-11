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
 * 【多脚本注入】config 由本类动态生成（不再用静态 assets config）：
 *  - 用户配置的脚本路径（`frida_script_list`，每行一个）写进顶层 `scripts` 数组，
 *    gadget 启动时逐个加载，实现多个脚本同时注入
 *  - 监听模式（`frida_listen`）→ interaction.type = "listen"（frida -H 连接交互）
 *  - 纯脚本模式 → interaction.type = "script"（gadget 自跑脚本，无需外部连接）
 *
 * 使用前置：
 *  1. 把 frida-gadget（arm64-v8a / armeabi-v7a）放到 app/src/main/jniLibs/<abi>/libfrida-gadget.so
 *     （或 assets/frida-gadget/libfrida-gadget.so，见 scripts/download-frida-gadget.sh）
 *  2. App 端 prefs 打开 code_inject 开关，填写 frida_script_list 脚本路径
 *
 * 开关语义（对齐 Dia）：
 *  - code_inject : 总开关
 *  - frida_listen / select_active_process_listen_mode : 是否监听模式（不按进程过滤）
 *  - select_active_process : set，只在这些进程里注入（非监听模式下生效）
 *  - frida_script_list : 注入的 JS 脚本绝对路径（每行一个，多脚本）
 *
 * 日志：注入每一步输出 [MyDia] 日志（开启设置页「远程日志」后，可在日志控制台查看）。
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
                "FridaHook: libfrida-gadget.so 未打包。请用 scripts/download-frida-gadget.sh 下载后重新编译。",
                IllegalStateException("asset missing")
            )
            return
        }

        try {
            // 3) 释放 gadget so
            val soFile = extractAsset(ctx, "frida-gadget/libfrida-gadget.so")

            // 4) 动态生成 config（多脚本 + 监听/脚本模式）
            val cfg = buildConfig()
            val cfgTarget = File(soFile.parentFile, "libfrida-gadget.config")
            cfgTarget.writeText(cfg)

            System.load(soFile.absolutePath)
            Module.log("FridaHook: injected from ${soFile.absolutePath}")
        } catch (t: Throwable) {
            Module.err("FridaHook: inject failed", t)
        }
    }

    /**
     * 生成 frida-gadget config（JSON）。
     *
     * 多脚本：读 SP `frida_scripts`（JSON 数组 [{id,name,source,enabled}]，
     * 由 UI 的文件选择器选 .js 后存内容），过滤 enabled，用 `scripts[].source`
     * 内联 JS 文本——frida-gadget 原生支持，目标进程直接执行，
     * 不依赖跨进程读脚本文件（避免 SELinux/路径问题）。
     * 无脚本时保留 shared socket 交互（可 frida -H 连接）。
     */
    private fun buildConfig(): String {
        val listenMode = prefs.getBoolean("frida_listen", false) == true
        val scripts = FridaScriptStore.load(prefs.getString(FridaScriptStore.KEY, null))
            .filter { it.enabled && it.source.isNotBlank() }

        Module.log("FridaHook: buildConfig listen=$listenMode scripts=${scripts.size}")
        scripts.forEach { Module.log("FridaHook:   script: ${it.name}") }

        val interactionType = when {
            listenMode -> "listen"
            scripts.isNotEmpty() -> "script"
            else -> "shared"
        }
        val interaction = buildString {
            append("\"type\": \"$interactionType\"")
            if (interactionType == "listen") {
                append(", \"address\": \"127.0.0.1\", \"port\": 27042, \"on_port_conflict\": \"fail\"")
            }
            if (interactionType == "shared") {
                append(", \"path\": \"/data/local/tmp/mydia-frida.sock\", \"on_port_conflict\": \"fail\", \"on_load\": \"wait\"")
            }
        }

        return buildString {
            append("{")
            append("\n  \"interaction\": { $interaction },")
            if (scripts.isNotEmpty()) {
                append("\n  \"scripts\": [")
                scripts.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("\n    { \"source\": \"${escapeJson(s.source)}\", \"on_change\": \"ignore\" }")
                }
                append("\n  ],")
            }
            append("\n  \"teardown\": \"full\"")
            append("\n}")
        }
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

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
