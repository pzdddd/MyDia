package com.pzdd.mydia.module.mcp

import android.content.Context
import org.json.JSONObject

/**
 * 一个 MCP 工具定义：name / description / inputSchema（JSON Schema）/ 执行函数。
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val execute: (Context, JSONObject) -> JSONObject,
)
