package com.pzdd.mydia.module.rewrite

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 一个待解析的 dex/apk 源。对应 Dia 的 dialog.box.expand.rewrite.DexPath。
 *
 * 类树浏览器把这些源里的类聚合展示，用户从中选择目标方法。
 */
@Keep
data class DexPath(
    var id: String = "",
    var alias: String = "",
    /** dex/apk/jar 文件绝对路径 */
    var path: String = "",
    var enabled: Boolean = true,
    /** 是目标 App 自身 APK（由系统自动注入，用户一般不改） */
    var isSelf: Boolean = false,
    /** 是捆绑的 Android framework dex（自动注入） */
    var isAndroidFramework: Boolean = false,
)

/**
 * dex 源列表的持久化层。对应 Dia 的 DexPathDataStore。
 *
 * SP key = [KEY]，value = JSON 数组。
 * 与 [RuleGroupDataStore] 存在同一份 per-app SP。
 */
object DexPathStore {
    const val KEY = "method_rewrite_dex_list"

    private val gson = Gson()
    private val type = object : TypeToken<List<DexPath>>() {}.type

    fun load(json: String?): List<DexPath> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<DexPath>>(json, type) ?: emptyList() }
            .getOrElse { emptyList() }
    }

    fun toJson(list: List<DexPath>): String = gson.toJson(list)
}
