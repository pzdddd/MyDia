package com.pzdd.mydia.ui.prefs

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 单个配置项的声明。所有 hook 的开关/参数都映射成一个 [Pref]。
 *
 * 为什么不用 XML Preference？
 *   Compose 时代，把配置声明直接写在 Kotlin 里更直观、可搜索、可重构。
 *   这套模型替代了原来的 res/xml/prefs*.xml + strings.xml。
 *
 * **SP key 必须和注入侧 `XSharedPreferences` 读取的 key 完全一致**，
 * 否则目标进程读不到。详见 [com.pzdd.mydia.module.Module]。
 */
sealed class Pref {
    abstract val key: String

    /** 分类小标题（分隔线）。 */
    data class Header(
        val title: String,
    ) : Pref() {
        override val key: String = "header::$title"
    }

    /**
     * 开关。[dependency] 指向另一个 [Switch] 的 key，仅在其为 true 时显示本项。
     * 对应原 XML 的 `app:dependency`。
     */
    data class Switch(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val summaryOn: String? = null,
        val summaryOff: String? = null,
        val default: Boolean = false,
        val dependency: String? = null,
    ) : Pref()

    /** 文本输入。点击弹对话框编辑。 */
    data class EditText(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val default: String = "",
        val hint: String? = null,
        val numeric: Boolean = false,
        val multiLine: Boolean = false,
        val dependency: String? = null,
    ) : Pref()

    /** 单选列表。[entries] = (显示文本, 存储值)。 */
    data class ListChoice(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val entries: List<Pair<String, String>>,
        val default: String = "",
        val dependency: String? = null,
    ) : Pref()

    /** 可点击条目（跳转、动作）。 */
    data class Action(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val icon: ImageVector? = null,
        val dependency: String? = null,
        val onClick: () -> Unit,
    ) : Pref()
}

/** 取出该 Pref 的依赖目标 key（用于条件显示），无依赖返回 null。 */
val Pref.dependencyKey: String?
    get() = when (this) {
        is Pref.Switch -> dependency
        is Pref.EditText -> dependency
        is Pref.ListChoice -> dependency
        is Pref.Header -> null
        is Pref.Action -> dependency
    }

/**
 * 一个配置页。
 *
 * @param key    唯一标识，用于注册表查找
 * @param title  顶部标题
 * @param icon   列表入口处的图标（可选）
 * @param summary 入口处的副标题（可选）
 * @param items  本页所有配置项
 */
data class PrefScreen(
    val key: String,
    val title: String,
    val icon: ImageVector? = null,
    val summary: String? = null,
    val items: List<Pref>,
)
