package com.pzdd.mydia.module.rewrite

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 规则组。对应 Dia 的 dialog.box.expand.rewrite.RuleGroup。
 * 一个 [RuleGroup] = 一组业务相关的 [Rule]，可整体启停、排序。
 */
@Keep
data class RuleGroup(
    var id: String = "",
    var name: String = "",
    var desc: String = "",
    var priority: Int = 0,
    var enabled: Boolean = true,
    var rules: MutableList<Rule> = mutableListOf(),
)

/**
 * 规则库的持久化层。对应 Dia 的 dialog.box.expand.rewrite.RuleGroupDataStore。
 *
 * Dia 用一个独立的 SharedPreferences（key="method_rewrite_mod_list"，value=JSON 数组）。
 * 我们复用【每个目标 App 一份】的 SP 文件，key 同名，方便与 Dia 的规则导入导出兼容。
 *
 * SP 由 App 端 UI 写入（[com.pzdd.mydia.ui.RuleListActivity]），注入侧只读。
 */
object RuleGroupDataStore {
    /** SP 里存规则列表的 key */
    const val KEY = "method_rewrite_mod_list"

    private val gson = Gson()
    private val type = object : TypeToken<List<RuleGroup>>() {}.type

    /** 从 world-readable SP 读取全部规则组（注入侧用） */
    fun load(rulesJson: String?): List<RuleGroup> {
        if (rulesJson.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<RuleGroup>>(rulesJson, type) ?: emptyList() }
            .getOrElse { emptyList() }
    }

    /** 展平所有启用的规则组里的启用的规则，按 priority 排序 */
    fun activeRules(groups: List<RuleGroup>): List<Rule> =
        groups.asSequence()
            .filter { it.enabled }
            .sortedByDescending { it.priority }
            .flatMap { it.rules.asSequence() }
            .filter { it.enabled }
            .toList()

    /** 序列化（App 端 UI 用） */
    fun toJson(groups: List<RuleGroup>): String = gson.toJson(groups)
}
