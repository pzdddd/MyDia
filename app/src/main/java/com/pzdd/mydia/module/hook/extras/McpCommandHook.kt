package com.pzdd.mydia.module.hook.extras

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pzdd.mydia.module.LibXposedEntry
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.ApplicationHook
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MCP 命令通道（注入侧）。对应 MCP server 的 hook_observe 工具。
 *
 * 目标 App 注入后运行，轮询 remote prefs 的 `mcp_command` key：
 *  - UI/MCP 侧写命令 JSON {id, action:"hook_observe", className, methodName, paramTypes?, timeoutMs}
 *  - 本 hook 执行 hookAllMethods 挂观察回调，捕获 window 内参数/返回值/异常
 *  - 写结果回 remote prefs 的 `mcp_result`（{id, calls:[...]}），MCP 侧轮询读回
 *
 * 注意：compat 层 Unhook 是 no-op（无法真正移除 hook），所以用「当前激活 id +
 * 激活标志」控制：新命令覆盖旧的，超时后回调里不再记录。
 *
 * 无条件注册（无开关）：只要目标 App 被注入即启动轮询，MCP 工具才有通道。
 */
class McpCommandHook : DiaHook(), ApplicationHook.OnAppReady {

    override fun install() {
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady(this)
    }

    override fun onReady(ctx: Context) {
        runCatching {
            val thread = Thread({ loop(ctx) }, "mcp-command")
            thread.isDaemon = true
            thread.start()
            Module.log("McpCommandHook: poller started")
        }.onFailure { Module.err("McpCommandHook start failed", it) }
    }

    // ==================== 轮询循环 ====================

    private fun loop(ctx: Context) {
        while (true) {
            try {
                // 读 UI 侧写的命令（remote prefs，组名 = 目标包名）
                val remote = LibXposedEntry.instance?.getRemotePreferences(ctx.packageName)
                val cmdJson = remote?.getString(KEY_COMMAND, null)
                if (cmdJson != null && cmdJson != lastCmdJson) {
                    lastCmdJson = cmdJson
                    handleCommand(ctx, cmdJson)
                }
                // 清理已消费的命令（避免重复执行）
                remote?.edit()?.putString(KEY_COMMAND, null)?.commit()
            } catch (e: Throwable) {
                // 轮询错误忽略（可能 remote 未就绪）
            }
            try { Thread.sleep(800) } catch (_: InterruptedException) { break }
        }
    }

    @Volatile private var lastCmdJson: String? = null

    // ==================== 命令处理 ====================

    private fun handleCommand(ctx: Context, cmdJson: String) {
        val cmd = runCatching { Gson().fromJson(cmdJson, JsonObject::class.java) }.getOrNull() ?: return
        val id = cmd.get("id")?.asString ?: return
        val action = cmd.get("action")?.asString ?: return
        Module.log("McpCommandHook: cmd id=$id action=$action")

        when (action) {
            "hook_observe" -> observe(ctx, id, cmd)
            "print_stack" -> printStack(ctx, id, cmd)
            "list_loaded_so" -> writeResult(id, listLoadedSo())
            "list_dex_files" -> writeResult(id, listDexFiles())
            else -> writeResult(id, JsonObject().apply { addProperty("error", "unknown action: $action") })
        }
    }

    /** print_stack：hook 指定方法，每次调用时 dump 调用栈到结果。 */
    private fun printStack(ctx: Context, id: String, cmd: JsonObject) {
        val className = cmd.get("className")?.asString ?: return writeResult(id, err("className 必填"))
        val methodName = cmd.get("methodName")?.asString ?: return writeResult(id, err("methodName 必填"))
        val timeoutMs = cmd.get("timeoutMs")?.asLong ?: 5000L

        val calls = JsonArray()
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val call = JsonObject()
                call.addProperty("method", param.method?.name ?: methodName)
                val stack = JsonArray()
                Thread.currentThread().stackTrace.take(30).forEach { se ->
                    stack.add("${se.className}.${se.methodName}(${se.fileName}:${se.lineNumber})")
                }
                call.add("stack", stack)
                synchronized(calls) { calls.add(call) }
            }
        }
        val hookResult = runCatching {
            val clazz = Class.forName(className, false, Module.classLoader)
            XposedBridge.hookAllMethods(clazz, methodName, hook)
        }
        if (hookResult.isFailure) {
            writeResult(id, err("hook 失败: ${hookResult.exceptionOrNull()?.message}"))
            return
        }
        Thread {
            try { Thread.sleep(timeoutMs) } catch (_: InterruptedException) {}
            val out = JsonObject().apply {
                addProperty("id", id)
                addProperty("status", "done")
                add("calls", calls)
            }
            writeResult(id, out)
            Module.log("McpCommandHook: printStack $className.$methodName done, ${calls.size()} calls")
        }.apply { isDaemon = true }.start()
    }

    /** list_loaded_so：读 /proc/self/maps 里的 so 库路径（去重、去系统库）。 */
    private fun listLoadedSo(): JsonObject {
        val sos = LinkedHashSet<String>()
        runCatching {
            java.io.File("/proc/self/maps").readLines().forEach { line ->
                val idx = line.lastIndexOf(".so")
                if (idx > 0) {
                    val path = line.substring(line.lastIndexOf(' '), idx + 3).trim()
                    if (path.isNotEmpty() && !path.startsWith("/system/") && !path.startsWith("/apex/")) {
                        sos.add(path)
                    }
                }
            }
        }
        val arr = JsonArray()
        sos.forEach { arr.add(it) }
        return JsonObject().apply {
            addProperty("id", "list_loaded_so")
            addProperty("status", "done")
            addProperty("count", sos.size)
            add("so", arr)
        }
    }

    /** list_dex_files：枚举 PathClassLoader 的 DexPathList 里的 dex 路径。 */
    private fun listDexFiles(): JsonObject {
        val dexes = LinkedHashSet<String>()
        runCatching {
            val cl = Module.classLoader
            val pathList = cl?.javaClass?.getDeclaredField("pathList")?.apply { isAccessible = true }?.get(cl)
            val dexElements = pathList?.javaClass?.getDeclaredField("dexElements")?.apply { isAccessible = true }?.get(pathList)
            if (dexElements is Array<*>) {
                dexElements.forEach { el ->
                    runCatching {
                        val file = el?.javaClass?.getDeclaredField("file")?.apply { isAccessible = true }?.get(el)
                        if (file is java.io.File) dexes.add(file.absolutePath)
                    }
                }
            }
        }
        val arr = JsonArray()
        dexes.forEach { arr.add(it) }
        return JsonObject().apply {
            addProperty("id", "list_dex_files")
            addProperty("status", "done")
            addProperty("count", dexes.size)
            add("dex", arr)
        }
    }

    /** hook_observe：hook 指定方法，观察 window 内调用。 */
    private fun observe(ctx: Context, id: String, cmd: JsonObject) {
        val className = cmd.get("className")?.asString ?: return writeResult(id, err("className 必填"))
        val methodName = cmd.get("methodName")?.asString ?: return writeResult(id, err("methodName 必填"))
        val timeoutMs = cmd.get("timeoutMs")?.asLong ?: 5000L

        // 激活新观察（旧观察自动停用）
        synchronized(observeLock) {
            activeObserveId = id
        }

        // 结果收集
        val calls = JsonArray()
        val finished = AtomicBoolean(false)

        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (synchronized(observeLock) { activeObserveId != id }) return
                val call = JsonObject()
                call.addProperty("method", param.method?.name ?: methodName)
                call.addProperty("thisClass", param.thisObject?.javaClass?.name ?: "static")
                val args = JsonArray()
                param.args.forEach { a ->
                    args.add(JsonObject().apply {
                        addProperty("type", a?.javaClass?.name ?: "null")
                        addProperty("value", safeString(a))
                    })
                }
                call.add("args", args)
                synchronized(calls) { calls.add(call) }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (synchronized(observeLock) { activeObserveId != id }) return
                // 补充返回值（给最后一次调用）
                val result = JsonObject().apply {
                    addProperty("type", param.result?.javaClass?.name ?: "void")
                    addProperty("value", safeString(param.result))
                    if (param.throwable != null) addProperty("throwable", param.throwable.toString())
                }
                synchronized(calls) {
                    if (calls.size() > 0) {
                        calls.get(calls.size() - 1).asJsonObject.add("result", result)
                    }
                }
            }
        }

        val hookResult = runCatching {
            val clazz = Class.forName(className, false, Module.classLoader)
            XposedBridge.hookAllMethods(clazz, methodName, hook)
        }

        // 超时后停用
        Thread {
            try { Thread.sleep(timeoutMs) } catch (_: InterruptedException) {}
            synchronized(observeLock) {
                if (activeObserveId == id) activeObserveId = null
            }
            finished.set(true)
            val out = JsonObject().apply {
                addProperty("id", id)
                addProperty("status", "done")
                add("calls", calls)
            }
            writeResult(id, out)
            Module.log("McpCommandHook: observe $className.$methodName done, ${calls.size()} calls")
        }.apply { isDaemon = true }.start()

        if (hookResult.isFailure) {
            writeResult(id, err("hook 失败: ${hookResult.exceptionOrNull()?.message}"))
        }
    }

    private fun err(msg: String): JsonObject = JsonObject().apply {
        addProperty("status", "error")
        addProperty("error", msg)
    }

    private val observeLock = Any()
    @Volatile private var activeObserveId: String? = null

    /** 把值安全转字符串（对象用 toString 截断，避免太大）。 */
    private fun safeString(o: Any?): String = when (o) {
        null -> "null"
        is String -> o
        is Number, is Boolean -> o.toString()
        else -> o.toString().take(500)
    }

    // ==================== 结果回传 ====================

    /** 写结果到 remote prefs 的 `mcp_result`（组名 = 目标包名），MCP 侧轮询读回。 */
    private fun writeResult(id: String, payload: JsonObject) {
        runCatching {
            val remote = LibXposedEntry.instance?.getRemotePreferences(Module.packageName) ?: return
            payload.addProperty("id", id)
            remote.edit().putString(KEY_RESULT, GsonBuilder().create().toJson(payload)).commit()
        }
    }

    companion object {
        /** MCP 侧写命令的 key（remote prefs，组名 = 目标包名）。 */
        const val KEY_COMMAND = "mcp_command"
        /** 注入侧写结果的 key（remote prefs，组名 = 目标包名）。 */
        const val KEY_RESULT = "mcp_result"
    }
}
