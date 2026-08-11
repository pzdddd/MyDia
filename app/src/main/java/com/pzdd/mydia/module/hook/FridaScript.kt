package com.pzdd.mydia.module.hook

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 一条 Frida 注入脚本。对应 Dia 的 fridaJsExample / 脚本注入功能。
 *
 * 由 UI（文件选择器选 .js）创建，脚本内容 [source] 直接存 SP（JSON），
 * 注入侧 FridaHook 读出来用 frida-gadget config 的 `scripts[].source` 内联执行——
 * 目标进程无需读外部脚本文件，避免跨进程/SELinux 问题。
 */
@Keep
data class FridaScript(
    var id: String = "",
    var name: String = "",
    var source: String = "",
    /** 开关：false 时跳过该脚本 */
    var enabled: Boolean = true,
)

/** Frida 脚本列表的持久化层（SP key = frida_scripts，JSON 数组）。 */
object FridaScriptStore {
    const val KEY = "frida_scripts"

    private val gson = Gson()
    private val type = object : TypeToken<List<FridaScript>>() {}.type

    fun load(json: String?): List<FridaScript> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<FridaScript>>(json, type) ?: emptyList() }
            .getOrElse { emptyList() }
    }

    fun toJson(list: List<FridaScript>): String = gson.toJson(list)
}
