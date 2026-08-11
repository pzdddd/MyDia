package com.pzdd.mydia.ui.rewrite

import android.content.Context
import android.content.SharedPreferences
import com.pzdd.mydia.module.RemotePrefsSync
import com.pzdd.mydia.module.rewrite.RuleGroup
import com.pzdd.mydia.module.rewrite.RuleGroupDataStore
import com.pzdd.mydia.ui.prefs.chmodPref

/**
 * 规则编辑器的持久化仓库。
 *
 * 每个 [RuleGroupListScreen] / [RuleListScreen] / [RuleEditScreen] 通过它读写规则，
 * 写入后自动执行「三连」：commit 落盘 → chmod 0644 → 同步 LSPosed remote。
 * 这样注入侧（常驻目标进程）能立刻读到最新规则，与现有 SP 契约一致。
 *
 * @param pkg 目标 App 包名（决定读写哪份 per-app SP）
 */
class RewriteRuleRepository(context: Context, private val pkg: String) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(pkg, Context.MODE_PRIVATE)

    /** 读取全部规则组。 */
    fun loadGroups(): MutableList<RuleGroup> =
        RuleGroupDataStore.load(sp.getString(RuleGroupDataStore.KEY, null)).toMutableList()

    /** 查找指定规则组。 */
    fun findGroup(groupId: String): RuleGroup? =
        loadGroups().firstOrNull { it.id == groupId }

    /**
     * 保存全部规则组（全量覆盖）。
     * 写后三连：commit → chmod 0644 → 同步 remote。
     */
    fun saveGroups(groups: List<RuleGroup>) {
        sp.edit().putString(RuleGroupDataStore.KEY, RuleGroupDataStore.toJson(groups)).commit()
        chmodPref(sp)
        RemotePrefsSync.syncLocal(sp)
    }

    /** 新增或更新单个规则组（按 id 匹配；不存在则追加）。 */
    fun upsertGroup(group: RuleGroup) {
        val groups = loadGroups()
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) groups[idx] = group else groups.add(group)
        saveGroups(groups)
    }

    /** 删除规则组。 */
    fun deleteGroup(groupId: String) {
        saveGroups(loadGroups().filterNot { it.id == groupId })
    }
}
