package com.pzdd.mydia.module.mcp

import android.content.Context
import com.pzdd.mydia.module.ActivationManager
import com.pzdd.mydia.module.PrefsFile
import com.pzdd.mydia.module.rewrite.DexParser
import com.pzdd.mydia.module.rewrite.RuleGroupDataStore
import com.pzdd.mydia.monitor.ConsoleLogStore
import com.pzdd.mydia.monitor.LogCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * MyDia MCP 工具实现（UI 侧可直接完成的部分）。
 *
 * 每个工具 [McpTool.execute] 返回 JSONObject，正常结果形如 {"content":[{...}]}，
 * 失败形如 {"content":[{"type":"text","text":"...",}],"isError":true}。
 * 协议层统一包成 MCP result。
 */
object McpTools {

    /** 全部工具。name → 工具。（lazy：工具定义在后面，避免前向引用） */
    val all: List<McpTool> by lazy {
        listOf(
            listApps, listActivities, listClasses, listMethods, hookObserve, printStack,
            listLoadedSo, listDexFiles, fridaStatus,
            getLogs, getConfig, setConfig, listRewriteRules, getStatus,
        )
    }

    fun byName(name: String): McpTool? = all.firstOrNull { it.name == name }

    // ==================== list_apps ====================
    private val listApps = McpTool(
        name = "list_apps",
        description = "列出已安装 App 及其 MyDia 注入状态（enabled）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"includeSystem":{"type":"boolean","description":"是否包含系统应用"},"query":{"type":"string","description":"按包名/名称过滤"}},"required":[]}"""),
    ) { ctx, args ->
        val includeSystem = args.optBoolean("includeSystem", false)
        val query = args.optString("query", "").lowercase()
        val apps = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            com.pzdd.mydia.ui.loadInstalledApps(ctx, includeSystem)
        }
        val arr = JSONArray()
        apps.filter { query.isEmpty() || it.pkg.lowercase().contains(query) || it.label.lowercase().contains(query) }
            .forEach { arr.put(JSONObject().put("pkg", it.pkg).put("label", it.label).put("enabled", it.enabled)) }
        text("${arr.length()} apps\n$arr")
    }

    // ==================== list_activities ====================
    private val listActivities = McpTool(
        name = "list_activities",
        description = "列出目标 App 的 Activity（注入侧 ActivityListHook 枚举结果，需目标 App 已被注入过）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"}},"required":["pkg"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        if (pkg.isBlank()) return@McpTool err("pkg 必填")
        val json = runCatching {
            ActivationManager.service?.getRemotePreferences(pkg)?.getString("activity_list", null)
        }.getOrNull()
        if (json.isNullOrBlank()) return@McpTool err("$pkg 无 Activity 列表（可能未注入或 remote 未同步）")
        text("Activities of $pkg:\n$json")
    }

    // ==================== list_classes ====================
    private val listClasses = McpTool(
        name = "list_classes",
        description = "列出目标 App 的类（用 dexlib2 解析其 apk）。返回类名列表。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"},"query":{"type":"string","description":"类名过滤关键字"},"limit":{"type":"number","description":"最多返回条数"}},"required":["pkg"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        if (pkg.isBlank()) return@McpTool err("pkg 必填")
        val apkPath = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).sourceDir }.getOrNull()
        if (apkPath == null) return@McpTool err("找不到 $pkg 的 apk")
        val classes = runCatching { DexParser.parse(File(apkPath)) }.getOrElse { emptyList() }
        val query = args.optString("query", "")
        val limit = args.optInt("limit", 500)
        val filtered = classes.filter { query.isEmpty() || it.className.contains(query, ignoreCase = true) }
            .take(limit)
        val arr = JSONArray()
        filtered.forEach { arr.put(it.className) }
        text("${classes.size} classes total, showing ${arr.length()}:\n$arr")
    }

    // ==================== list_methods ====================
    private val listMethods = McpTool(
        name = "list_methods",
        description = "列出目标 App 指定类的成员方法及签名。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"},"className":{"type":"string","description":"类全限定名（点分）"},"query":{"type":"string","description":"方法名过滤关键字"}},"required":["pkg","className"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        val className = args.optString("className", "")
        if (pkg.isBlank() || className.isBlank()) return@McpTool err("pkg 和 className 必填")
        val apkPath = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).sourceDir }.getOrNull()
        if (apkPath == null) return@McpTool err("找不到 $pkg 的 apk")
        val classes = runCatching { DexParser.parse(File(apkPath)) }.getOrElse { emptyList() }
        val cls = classes.firstOrNull { it.className == className }
            ?: return@McpTool err("类 $className 不存在")
        val query = args.optString("query", "")
        val methods = cls.methods.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
        val arr = JSONArray()
        methods.forEach { arr.put(JSONObject().put("name", it.name).put("signature", it.signature)) }
        text("${cls.className} methods (${arr.length()}):\n$arr")
    }

    // ==================== get_logs ====================
    private val getLogs = McpTool(
        name = "get_logs",
        description = "拉取 MyDia 远程日志（注入侧模块日志）。可按分类过滤、限量。",
        inputSchema = JSONObject("""{"type":"object","properties":{"category":{"type":"string","description":"分类：all/inject/dialog/button/anti/fake/monitor/frida/other"},"limit":{"type":"number","description":"最多返回条数"}},"required":[]}"""),
    ) { ctx, args ->
        val category = args.optString("category", "all")
        val limit = args.optInt("limit", 100)
        val cat = LogCategory.entries.firstOrNull { it.name.equals(category, ignoreCase = true) || it.label == category }
        val logs = ConsoleLogStore.snapshot()
            .let { if (cat != null) it.filter { e -> e.category == cat } else it }
            .takeLast(limit)
        val arr = JSONArray()
        logs.forEach { e ->
            arr.put(JSONObject()
                .put("time", e.time)
                .put("pkg", e.pkg)
                .put("category", e.category.name)
                .put("msg", e.msg))
        }
        text("${arr.length()} log entries:\n$arr")
    }

    // ==================== get_config ====================
    private val getConfig = McpTool(
        name = "get_config",
        description = "读某 App 的模块配置（per-app SP，含 enabled 和各功能开关）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名；填 mydia 读全局配置"}},"required":["pkg"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        if (pkg.isBlank()) return@McpTool err("pkg 必填")
        val prefs = PrefsFile.of(if (pkg == "mydia") "digXposed" else pkg)
        prefs.reload()
        // PrefsFile 是私有 cache，这里通过反射取 all 不方便，改用 remote/本地 SP 直接读
        val sp = ctx.getSharedPreferences(if (pkg == "mydia") "digXposed" else pkg, Context.MODE_PRIVATE)
        val obj = JSONObject()
        sp.all.forEach { (k, v) -> obj.put(k, v.toString()) }
        text("Config of $pkg:\n$obj")
    }

    // ==================== set_config ====================
    private val setConfig = McpTool(
        name = "set_config",
        description = "写某 App 的模块配置（开关 hook 用）。写入后同步 remote，注入侧立即生效。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"},"key":{"type":"string","description":"配置 key"},"value":{"type":"string","description":"配置值（true/false/字符串）"}},"required":["pkg","key","value"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        val key = args.optString("key", "")
        val value = args.optString("value", "")
        if (pkg.isBlank() || key.isBlank()) return@McpTool err("pkg 和 key 必填")
        val sp = ctx.getSharedPreferences(pkg, Context.MODE_PRIVATE)
        val editor = sp.edit()
        when (value.lowercase()) {
            "true" -> editor.putBoolean(key, true)
            "false" -> editor.putBoolean(key, false)
            else -> editor.putString(key, value)
        }
        editor.commit()
        com.pzdd.mydia.ui.prefs.chmodPref(sp)
        com.pzdd.mydia.module.RemotePrefsSync.syncLocal(sp)
        text("已写入 $pkg/$key=$value")
    }

    // ==================== list_rewrite_rules ====================
    private val listRewriteRules = McpTool(
        name = "list_rewrite_rules",
        description = "读某 App 的方法重写规则组（method_rewrite_mod_list）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"}},"required":["pkg"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        if (pkg.isBlank()) return@McpTool err("pkg 必填")
        val sp = ctx.getSharedPreferences(pkg, Context.MODE_PRIVATE)
        val json = sp.getString(RuleGroupDataStore.KEY, null)
        if (json.isNullOrBlank()) return@McpTool err("$pkg 无重写规则")
        text("Rewrite rules of $pkg:\n$json")
    }

    // ==================== get_status ====================
    private val getStatus = McpTool(
        name = "get_status",
        description = "查询 MyDia 模块激活状态与 MCP server 信息。",
        inputSchema = JSONObject("""{"type":"object","properties":{},"required":[]}"""),
    ) { ctx, args ->
        val svc = ActivationManager.service
        val obj = JSONObject()
            .put("moduleActive", svc != null)
            .put("framework", svc?.frameworkName ?: "null")
            .put("apiVersion", svc?.apiVersion ?: -1)
            .put("mcpServer", "MyDia MCP server v1")
        text(obj.toString())
    }

    // ==================== hook_observe ====================
    /**
     * 实时 hook 观察：通过注入侧 McpCommandHook 的 remote 命令通道，
     * 让目标 App 内的 hook 观察指定方法的参数/返回值，结果写回 remote。
     *
     * 流程：写 `mcp_command`（{id, action:hook_observe, className, methodName, timeoutMs}）
     * → 注入侧轮询执行 → 超时后写 `mcp_result` → 本工具轮询读回。
     */
    private val hookObserve = McpTool(
        name = "hook_observe",
        description = "实时 hook 目标 App 的指定方法，观察 timeoutMs 毫秒内的参数/返回值/异常（需目标 App 已被注入）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"},"className":{"type":"string","description":"类全限定名（点分）"},"methodName":{"type":"string","description":"方法名"},"timeoutMs":{"type":"number","description":"观察窗口毫秒数，默认 5000"}},"required":["pkg","className","methodName"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        val className = args.optString("className", "")
        val methodName = args.optString("methodName", "")
        val timeoutMs = args.optLong("timeoutMs", 5000L)
        if (pkg.isBlank() || className.isBlank() || methodName.isBlank()) return@McpTool err("pkg/className/methodName 必填")
        val svc = ActivationManager.service
        if (svc == null) return@McpTool err("模块未激活")
        val remote = svc.getRemotePreferences(pkg)
        val id = "hook_${System.currentTimeMillis()}"

        // 1) 写命令到注入侧通道
        val cmd = JSONObject()
            .put("id", id)
            .put("action", "hook_observe")
            .put("className", className)
            .put("methodName", methodName)
            .put("timeoutMs", timeoutMs)
        runCatching {
            remote.edit().putString("mcp_command", cmd.toString()).commit()
        }.onFailure { return@McpTool err("写命令失败: ${it.message}") }

        // 2) 轮询结果（最长等待 timeoutMs + 2s）
        val deadline = System.currentTimeMillis() + timeoutMs + 2000
        var result: String? = null
        while (System.currentTimeMillis() < deadline) {
            val r = runCatching { remote.getString("mcp_result", null) }.getOrNull()
            if (r != null && r.contains("\"id\":\"$id\"")) { result = r; break }
            try { Thread.sleep(300) } catch (_: InterruptedException) { break }
        }
        if (result == null) return@McpTool err("观察超时（目标 App 可能未注入，或 ${className}.$methodName 未被调用）")
        text(result!!)
    }

    // ==================== print_stack ====================
    /**
     * 打印调用栈：通过注入侧通道 hook 指定方法，每次调用时记录调用栈。
     * 流程同 hook_observe：写命令 → 注入侧执行 → 读结果。
     */
    private val printStack = McpTool(
        name = "print_stack",
        description = "hook 目标 App 的指定方法，记录每次调用时的调用栈（用于定位谁调用了它）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"},"className":{"type":"string","description":"类全限定名（点分）"},"methodName":{"type":"string","description":"方法名"},"timeoutMs":{"type":"number","description":"观察窗口毫秒数，默认 5000"}},"required":["pkg","className","methodName"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        val className = args.optString("className", "")
        val methodName = args.optString("methodName", "")
        val timeoutMs = args.optLong("timeoutMs", 5000L)
        if (pkg.isBlank() || className.isBlank() || methodName.isBlank()) return@McpTool err("pkg/className/methodName 必填")
        sendCommandAndWait(pkg, "print_stack", className, methodName, timeoutMs, "调用栈观察超时")
    }

    // ==================== list_loaded_so ====================
    /** 列出目标 App 当前加载的 so 库（注入侧读 /proc/self/maps）。 */
    private val listLoadedSo = McpTool(
        name = "list_loaded_so",
        description = "列出目标 App 当前加载的 so 库（非系统库，读 /proc/self/maps）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"}},"required":["pkg"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        if (pkg.isBlank()) return@McpTool err("pkg 必填")
        sendCommandAndWait(pkg, "list_loaded_so", "", "", 3000L, "枚举超时（目标 App 可能未注入）")
    }

    // ==================== list_dex_files ====================
    /** 列出目标 App 加载的 dex 文件（注入侧枚举 DexPathList）。 */
    private val listDexFiles = McpTool(
        name = "list_dex_files",
        description = "列出目标 App 加载的 dex 文件（注入侧枚举 ClassLoader 的 DexPathList）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"}},"required":["pkg"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        if (pkg.isBlank()) return@McpTool err("pkg 必填")
        sendCommandAndWait(pkg, "list_dex_files", "", "", 3000L, "枚举超时（目标 App 可能未注入）")
    }

    // ==================== frida_status ====================
    /** 查询目标 App 的 Frida 注入配置状态（UI 侧读 SP，无需注入侧通道）。 */
    private val fridaStatus = McpTool(
        name = "frida_status",
        description = "查询目标 App 的 Frida 注入配置状态（code_inject 开关、监听模式、脚本列表）。",
        inputSchema = JSONObject("""{"type":"object","properties":{"pkg":{"type":"string","description":"目标 App 包名"}},"required":["pkg"]}"""),
    ) { ctx, args ->
        val pkg = args.optString("pkg", "")
        if (pkg.isBlank()) return@McpTool err("pkg 必填")
        val sp = ctx.getSharedPreferences(pkg, Context.MODE_PRIVATE)
        val scripts = com.pzdd.mydia.module.hook.FridaScriptStore.load(sp.getString(com.pzdd.mydia.module.hook.FridaScriptStore.KEY, null))
        val obj = JSONObject()
            .put("codeInject", sp.getBoolean("code_inject", false))
            .put("listenMode", sp.getBoolean("frida_listen", false))
            .put("processFilter", sp.getString("select_active_process_listen_mode", ""))
            .put("scriptCount", scripts.size)
        val arr = JSONArray()
        scripts.filter { it.enabled }.forEach { arr.put(JSONObject().put("name", it.name).put("enabled", it.enabled)) }
        obj.put("enabledScripts", arr)
        text(obj.toString())
    }

    /** 发送注入侧命令并轮询读回结果（hook_observe / print_stack / list_* 通用）。 */
    private fun sendCommandAndWait(
        pkg: String,
        action: String,
        className: String,
        methodName: String,
        timeoutMs: Long,
        timeoutMsg: String,
    ): JSONObject {
        val svc = ActivationManager.service
        if (svc == null) return err("模块未激活")
        val remote = svc.getRemotePreferences(pkg)
        val id = "${action}_${System.currentTimeMillis()}"
        val cmd = JSONObject()
            .put("id", id)
            .put("action", action)
        if (className.isNotBlank()) cmd.put("className", className)
        if (methodName.isNotBlank()) cmd.put("methodName", methodName)
        cmd.put("timeoutMs", timeoutMs)
        runCatching {
            remote.edit().putString("mcp_command", cmd.toString()).commit()
        }.onFailure { return err("写命令失败: ${it.message}") }

        val deadline = System.currentTimeMillis() + timeoutMs + 2000
        var result: String? = null
        while (System.currentTimeMillis() < deadline) {
            val r = runCatching { remote.getString("mcp_result", null) }.getOrNull()
            if (r != null && r.contains("\"id\":\"$id\"")) { result = r; break }
            try { Thread.sleep(300) } catch (_: InterruptedException) { break }
        }
        return if (result == null) err(timeoutMsg) else text(result!!)
    }

    // ==================== 辅助 ====================
    /** 成功文本结果。 */
    private fun text(t: String): JSONObject = JSONObject()
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", t)))
        .put("isError", false)

    /** 失败结果（isError=true，MCP 规范要求工具级错误走 result 而非 JSON-RPC error）。 */
    private fun err(msg: String): JSONObject = JSONObject()
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "ERROR: $msg")))
        .put("isError", true)
}
