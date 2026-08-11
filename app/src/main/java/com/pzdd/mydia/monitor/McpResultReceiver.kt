package com.pzdd.mydia.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap

/**
 * 接收注入侧 McpCommandHook 通过广播回传的 MCP 工具执行结果。
 *
 * 注入侧无法写 remote prefs（libxposed 对注入侧是只读投影），改用广播回传
 * （与 ConsoleLogReceiver 同模式）。MCP server（本进程）轮询 [McpResultStore]。
 */
class McpResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val result = intent.getStringExtra(EXTRA_RESULT) ?: return
        McpResultStore.put(id, result)
    }

    companion object {
        const val ACTION = "com.pzdd.mydia.ACTION_MCP_RESULT"
        const val EXTRA_ID = "id"
        const val EXTRA_RESULT = "result"
    }
}

/** MCP 工具结果的内存缓存（MyDia 进程内，requestId → JSON）。 */
object McpResultStore {
    private val results = ConcurrentHashMap<String, String>()

    fun put(id: String, json: String) {
        // 只保留最近 50 个，防泄漏
        if (results.size > 50) results.clear()
        results[id] = json
    }

    fun get(id: String): String? = results[id]

    fun remove(id: String) { results.remove(id) }
}
