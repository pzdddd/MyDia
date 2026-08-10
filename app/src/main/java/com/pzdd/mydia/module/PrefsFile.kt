package com.pzdd.mydia.module

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * 跨进程读取模块配置的自实现（替代 de.robv.android.xposed.XSharedPreferences）。
 *
 * **读取优先级**：
 *  1. libxposed Remote Preferences（[LibXposedEntry.instance.getRemotePreferences]）——
 *     由 LSPosed daemon 通过 binder 提供，天然绕过 SELinux 文件隔离，是 API 102 官方机制；
 *  2. 直接读模块 SP 文件（legacy/无 remote 时回退，需文件 chmod 0644）。
 *
 * 注入侧只读（getBoolean/getString/getInt/getLong/getStringSet），
 * 与旧 API XSharedPreferences 的读取风格一致，hook 代码无需改动。
 */
class PrefsFile private constructor(
    private val remote: android.content.SharedPreferences?,
    private val hostPackage: String?,
    private val name: String,
) {

    private val cache = HashMap<String, Any?>()

    /** 上次 reload 时间戳（节流：读前自动刷新，最多 1 秒一次）。 */
    @Volatile
    private var lastReload = 0L

    /** 取配置：优先 remote（daemon binder），回退文件。 */
    fun reload() {
        cache.clear()
        lastReload = System.currentTimeMillis()
        remote?.let {
            // Remote Preferences 已经是最新（daemon 数据库），直接缓存
            for ((k, v) in it.all) cache[k] = v
            return
        }
        // 回退：解析本地 SP 文件
        val file = File("/data/data/$hostPackage/shared_prefs/$name.xml")
        if (!file.isFile) return
        try {
            val parser = Xml.newPullParser()
            parser.setInput(file.inputStream(), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "boolean") {
                    parser.getAttributeValue(null, "name")?.let { key ->
                        cache[key] = parser.getAttributeValue(null, "value") == "true"
                    }
                } else if (eventType == XmlPullParser.START_TAG && parser.name == "int") {
                    parser.getAttributeValue(null, "name")?.let { key ->
                        cache[key] = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                    }
                } else if (eventType == XmlPullParser.START_TAG && parser.name == "long") {
                    parser.getAttributeValue(null, "name")?.let { key ->
                        cache[key] = parser.getAttributeValue(null, "value")?.toLongOrNull() ?: 0L
                    }
                } else if (eventType == XmlPullParser.START_TAG && parser.name == "float") {
                    parser.getAttributeValue(null, "name")?.let { key ->
                        cache[key] = parser.getAttributeValue(null, "value")?.toFloatOrNull() ?: 0f
                    }
                } else if (eventType == XmlPullParser.START_TAG && parser.name == "string") {
                    parser.getAttributeValue(null, "name")?.let { key ->
                        cache[key] = if (parser.next() == XmlPullParser.TEXT) parser.text else null
                    }
                } else if (eventType == XmlPullParser.START_TAG && parser.name == "set") {
                    // getStringSet 写入的 <set><string>a</string><string>b</string></set>
                    parser.getAttributeValue(null, "name")?.let { key ->
                        val list = ArrayList<String>()
                        var inner = parser.next()
                        while (inner != XmlPullParser.END_TAG || parser.name != "set") {
                            if (inner == XmlPullParser.START_TAG && parser.name == "string") {
                                if (parser.next() == XmlPullParser.TEXT) list.add(parser.text)
                            }
                            inner = parser.next()
                        }
                        cache[key] = list.toSet()
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Throwable) {
            // 解析失败保持空缓存（等下次 reload）
        }
    }

    /**
     * 读取前自动刷新（节流）：目标进程常驻，用户可能在 UI 改了配置——
     * 距上次 reload 超过 1 秒就重新读，保证 hook 事件触发时拿到最新配置。
     */
    fun ensureFresh() {
        if (System.currentTimeMillis() - lastReload > 1000) reload()
    }

    fun getBoolean(key: String, def: Boolean): Boolean { ensureFresh(); return cache[key] as? Boolean ?: def }
    fun getString(key: String, def: String?): String? { ensureFresh(); return cache[key] as? String ?: def }
    fun getInt(key: String, def: Int): Int { ensureFresh(); return (cache[key] as? Number)?.toInt() ?: def }
    fun getLong(key: String, def: Long): Long { ensureFresh(); return (cache[key] as? Number)?.toLong() ?: def }

    /**
     * 读取字符串集合。兼容两种存储：
     *  - UI 用 putStringSet → XML `<set>` 标签
     *  - UI 用 putString 存「逗号/空格/换行分隔」（当前各 hook 的 EditText 配置）→ 按分隔符切分
     */
    fun getStringSet(key: String, def: Set<String>?): Set<String>? {
        ensureFresh()
        when (val v = cache[key]) {
            is Set<*> -> return v.mapNotNull { it as? String }.toSet()
            is String -> {
                val parts = v.split(",", "，", "\n", " ").map { it.trim() }.filter { it.isNotEmpty() }
                return parts.toSet()
            }
        }
        return def
    }

    companion object {
        /**
         * 创建配置读取器（优先 remote，回退文件）。
         * [name] = SP 文件名（"digXposed" 或目标包名），与 UI 侧写入的组名一致。
         */
        @JvmStatic
        fun of(name: String): PrefsFile {
            val remote = try {
                LibXposedEntry.instance?.getRemotePreferences(name)
            } catch (_: Throwable) { null }
            return if (remote != null) {
                PrefsFile(remote, null, name)
            } else {
                PrefsFile(null, LibXposedEntry.HOST_PACKAGE, name)
            }
        }
    }
}
