package com.pzdd.mydia.module.mcp

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 轻量 MCP server（Streamable HTTP + JSON-only，零第三方依赖）。
 *
 * 用 [ServerSocket] 手写极简 HTTP/1.1 服务器（Android 无 com.sun.net.httpserver），
 * 用 Android 内置 org.json 做 JSON-RPC。参考 android-remote-control-mcp 的 JSON-only
 * 模式：每个 POST 一个 JSON-RPC 消息，响应 Content-Type: application/json。
 *
 * 协议流程（2025-06-18 Streamable HTTP）：
 *   initialize（响应头带 Mcp-Session-Id）→ notifications/initialized（202）→ tools/list → tools/call
 *
 * @param bindHost 127.0.0.1（adb forward）或 0.0.0.0（局域网）
 * @param port 监听端口
 */
class McpServer(
    private val context: Context,
    private val bindHost: String,
    private val port: Int,
) {
    private val tag = "MyDiaMcp"
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private var sessionId: String? = null

    /** 当前绑定地址（供 UI 显示）。 */
    var boundAddress: String = ""
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            runCatching {
                // 用无参构造 + 单独 bind：ServerSocket(port) 构造时已自动绑定，再 bind 会 Already bound
                serverSocket = ServerSocket().also { ss ->
                    ss.reuseAddress = true
                    ss.bind(java.net.InetSocketAddress(bindHost, port))
                }
                boundAddress = "$bindHost:$port"
                Log.i(tag, "MCP server listening on $boundAddress")
                while (running.get()) {
                    val client = serverSocket?.accept() ?: break
                    executor.execute { handle(client) }
                }
            }.onFailure { e ->
                Log.e(tag, "server failed: ${e.message}")
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        Log.i(tag, "MCP server stopped")
    }

    val isRunning: Boolean get() = running.get()

    // ==================== HTTP 处理 ====================

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            // 读请求行 + headers
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            var contentLength = 0
            var mcpSession: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx < 0) continue
                val name = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                when (name.lowercase()) {
                    "content-length" -> contentLength = value.toIntOrNull() ?: 0
                    "mcp-session-id" -> mcpSession = value
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r < 0) break
                    read += r
                }
                String(buf, 0, read)
            } else ""

            when (method) {
                "GET" -> respond(client, "405 Method Not Allowed", "{}", mapOf("Allow" to "POST"))
                "DELETE" -> { sessionId = null; respond(client, "200 OK", "{}") }
                "POST" -> handlePost(client, body, mcpSession)
                else -> respond(client, "405 Method Not Allowed", "{}")
            }
        } catch (e: Exception) {
            Log.w(tag, "handle error: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun handlePost(client: Socket, body: String, mcpSession: String?) {
        val msg = try { JSONObject(body) } catch (e: JSONException) {
            respond(client, "400 Bad Request", errorJson(null, -32700, "Parse error").toString()); return
        }
        val method = msg.optString("method")
        val id = if (msg.has("id")) msg.opt("id") else null

        when (method) {
            "initialize" -> {
                sessionId = UUID.randomUUID().toString()
                val result = JSONObject()
                    .put("protocolVersion", "2025-06-18")
                    .put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", false)))
                    .put("serverInfo", JSONObject().put("name", "mydia-mcp").put("version", "1.0.0"))
                respond(client, "200 OK", resultJson(id, result).toString(), mapOf("Mcp-Session-Id" to sessionId!!))
            }
            "notifications/initialized" -> respond(client, "202 Accepted", "")
            "tools/list" -> {
                val arr = JSONArray()
                McpTools.all.forEach { t ->
                    arr.put(JSONObject()
                        .put("name", t.name)
                        .put("description", t.description)
                        .put("inputSchema", t.inputSchema))
                }
                respond(client, "200 OK", resultJson(id, JSONObject().put("tools", arr)).toString())
            }
            "tools/call" -> {
                val params = msg.optJSONObject("params")
                val name = params?.optString("name", "") ?: ""
                val args = params?.optJSONObject("arguments") ?: JSONObject()
                val tool = McpTools.byName(name)
                if (tool == null) {
                    respond(client, "200 OK", errorJson(id, -32602, "Unknown tool: $name").toString())
                } else {
                    val out = try {
                        tool.execute(context, args)
                    } catch (e: Exception) {
                        Log.w(tag, "tool $name error: ${e.message}")
                        JSONObject()
                            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "ERROR: ${e.message}")))
                            .put("isError", true)
                    }
                    respond(client, "200 OK", resultJson(id, out).toString())
                }
            }
            "ping" -> respond(client, "200 OK", resultJson(id, JSONObject()).toString())
            else -> respond(client, "200 OK", errorJson(id, -32601, "Method not found: $method").toString())
        }
    }

    // ==================== JSON-RPC 辅助 ====================

    private fun resultJson(id: Any?, result: JSONObject): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)

    private fun errorJson(id: Any?, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("error", JSONObject().put("code", code).put("message", message))

    // ==================== HTTP 响应 ====================

    private fun respond(client: Socket, status: String, body: String, extraHeaders: Map<String, String> = emptyMap()) {
        try {
            val out: OutputStream = client.getOutputStream()
            val headers = StringBuilder()
            headers.append("HTTP/1.1 ").append(status).append("\r\n")
            headers.append("Content-Type: application/json\r\n")
            headers.append("Accept: application/json, text/event-stream\r\n")
            headers.append("Content-Length: ").append(body.toByteArray(Charsets.UTF_8).size).append("\r\n")
            extraHeaders.forEach { (k, v) -> headers.append(k).append(": ").append(v).append("\r\n") }
            headers.append("Connection: close\r\n\r\n")
            out.write(headers.toString().toByteArray(Charsets.UTF_8))
            if (body.isNotEmpty()) out.write(body.toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (e: Exception) {
            Log.w(tag, "respond error: ${e.message}")
        }
    }
}
