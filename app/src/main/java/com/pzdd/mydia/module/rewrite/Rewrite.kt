package com.pzdd.mydia.module.rewrite

import androidx.annotation.Keep
import java.util.regex.Pattern

/**
 * 一条改写动作。对应 Dia 的 dialog.box.expand.rewrite.Rewrite。
 *
 * 语义：把方法的【第 [index] 个参数】（或返回值，index=-1）按 [match] 条件替换成 [replace]。
 *
 * - [type]：参数的数据类型，决定 [replace] 字符串如何解析成真实值
 *   - 0 STRING / 1 NUMBER / 2 BOOLEAN / 3 OBJECT(JSON) / 4 VOID / 5 BYTES(十六进制)
 * - [mode]：字符串匹配模式（仅 type=0/5 且 isCheckMatch 时生效）
 *   - 0 MATCH(相等) / 1 CONTAINS / 2 START_WITH / 3 END_WITH / 4 BIG(>) / 5 LITTLE(<)
 * - [match] == "NAN" 哨兵表示“无条件替换”（不检查原值，直接覆盖）
 *
 * 用 Kotlin 重写了 Dia 原版那段被 jadx 反编译失败、且夹杂 MTProtector 的 m14363()——
 * 行为对齐 Dia（字符串支持正则/大小写/整体匹配；bytes 支持十六进制 find/replace）。
 */
@Keep
data class Rewrite(
    /** 改第几个入参；-1 = 改返回值 */
    var index: Int = 0,
    /** 见 [Type] 常量 */
    var type: Int = TYPE_STRING,
    /** 见 [Mode] 常量 */
    var mode: Int = MODE_MATCH,
    /** smali 类型串（如 "Ljava/lang/String;" "I" "[B"），用于校验/显示 */
    var classType: String = "",

    var match: String = NAN,
    var replace: String = NAN,

    var matchWholeValue: Boolean = false,
    var matchCaseSensitive: Boolean = false,
    var matchRegex: Boolean = false,
    var replaceAll: Boolean = true,
    /** bytes 模式下，match/replace 按十六进制串解析 */
    var matchHex: Boolean = false,
) {
    /** 是否需要检查原值（match 非 NAN 哨兵时才检查） */
    val isCheckMatch: Boolean get() = match != NAN
    /** 是否启用（replace 非 NAN 哨兵） */
    val isEnabled: Boolean get() = replace != NAN

    /**
     * 把 [replace] 字符串按 [type] 解析成方法参数/返回值的真实对象。
     * 原 JVM 对象 [current] 用于 OBJECT 类型做局部修改。
     */
    fun toValue(current: Any?): Any? {
        // OBJECT 类型：replace 是一段 JSON，整体替换（不支持 merge）
        return when (type) {
            TYPE_VOID -> null
            TYPE_BOOLEAN -> parseBoolean(replace)
            TYPE_NUMBER -> parseNumber(replace, current)
            TYPE_BYTES -> hexToBytes(replace)
            TYPE_OBJECT -> replace // 简化：直接返回 JSON 字符串；如需对象可接 Gson
            else /* STRING */ -> replace
        }
    }

    /**
     * 对原值 [current] 应用本条改写：先匹配 [match]，命中则替换成 [replace] 解析后的值。
     * 不命中返回原值。
     */
    fun apply(current: Any?): Any? {
        if (!isCheckMatch) return toValue(current)
        return when (type) {
            TYPE_BYTES -> applyBytes(current as? ByteArray ?: return current)
            TYPE_STRING -> applyString(current?.toString() ?: return toValue(null))
            TYPE_NUMBER -> applyNumber(current)
            TYPE_BOOLEAN -> if (parseBoolean(current?.toString()) == parseBoolean(match)) parseBoolean(replace) else current
            else -> toValue(current)
        }
    }

    // ---- 字符串改写（支持正则 / 大小写 / 整体匹配 / 全部替换）----
    private fun applyString(s: String): Any {
        if (match.isEmpty() && s.isEmpty()) return replace
        val flags = if (matchCaseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return when {
            matchRegex -> {
                val p = Pattern.compile(match, flags)
                val m = p.matcher(s)
                if (matchWholeValue) {
                    if (m.find() && m.replaceFirst("").isEmpty()) replace else s
                } else {
                    if (!m.find()) s else if (replaceAll) m.replaceAll(replace) else m.replaceFirst(replace)
                }
            }
            matchWholeValue -> if (s.equals(match, ignoreCase = !matchCaseSensitive)) replace else s
            else -> {
                val target = if (matchCaseSensitive) match else match.lowercase()
                val src = if (matchCaseSensitive) s else s.lowercase()
                val idx = src.indexOf(target)
                if (idx < 0) s else {
                    if (replaceAll) {
                        val sb = StringBuilder()
                        var i = 0
                        while (i < s.length) {
                            val j = src.indexOf(target, i)
                            if (j < 0) { sb.append(s, i, s.length); break }
                            sb.append(s, i, j).append(replace)
                            i = j + target.length
                        }
                        sb.toString()
                    } else s.substring(0, idx) + replace + s.substring(idx + target.length)
                }
            }
        }
    }

    // ---- 数字改写：MODE_BIG/MODE_LITTLE 做大小比较 ----
    private fun applyNumber(current: Any?): Any? {
        val cur = (current as? Number)?.toDouble() ?: return toValue(current)
        val m = match.toDoubleOrNull() ?: return toValue(current)
        val hit = when (mode) {
            MODE_BIG -> cur > m
            MODE_LITTLE -> cur < m
            MODE_CONTAINS -> cur.toString().contains(m.toString())
            else -> cur == m
        }
        return if (hit) toValue(current) else current
    }

    // ---- 字节改写：在 byte[] 中按 match 十六进制串 find，命中段替换成 replace ----
    private fun applyBytes(data: ByteArray): Any {
        val needle = hexToBytes(match)
        if (needle.isEmpty()) return toValue(data) as ByteArray
        val repl = hexToBytes(replace)
        val out = ArrayList<Byte>(data.size)
        var i = 0
        while (i < data.size) {
            if (i + needle.size <= data.size && data.copyOfRange(i, i + needle.size).contentEquals(needle)) {
                repl.forEach { out.add(it) }
                i += needle.size
            } else {
                out.add(data[i]); i++
            }
        }
        return out.toByteArray()
    }

    private fun parseBoolean(s: String?): Boolean =
        s?.equals("true", ignoreCase = true) == true

    private fun parseNumber(s: String, current: Any?): Number {
        val v = s.trim()
        return when (current) {
            is Float -> v.toFloatOrNull() ?: 0f
            is Double -> v.toDoubleOrNull() ?: 0.0
            is Long -> v.toLongOrNull() ?: 0L
            is Int -> v.toIntOrNull() ?: 0
            is Short -> (v.toShortOrNull() ?: 0).toShort()
            is Byte -> (v.toByteOrNull() ?: 0).toByte()
            else -> v.toIntOrNull() ?: v.toLongOrNull() ?: 0
        }
    }

    companion object {
        const val TYPE_STRING = 0
        const val TYPE_NUMBER = 1
        const val TYPE_BOOLEAN = 2
        const val TYPE_OBJECT = 3
        const val TYPE_VOID = 4
        const val TYPE_BYTES = 5

        const val MODE_MATCH = 0
        const val MODE_CONTAINS = 1
        const val MODE_START_WITH = 2
        const val MODE_END_WITH = 3
        const val MODE_BIG = 4
        const val MODE_LITTLE = 5

        /** “不限制”的哨兵值（对齐 Dia 用 \u0001NAN\u0002） */
        const val NAN = "\u0001NAN\u0002"
    }
}

/** 十六进制串 → ByteArray，容错（空/非法返回空数组） */
fun hexToBytes(hex: String?): ByteArray {
    if (hex.isNullOrBlank()) return ByteArray(0)
    val cleaned = hex.replace(Regex("[\\s,]"), "")
    if (cleaned.length % 2 != 0 || cleaned.any { it.digitToIntOrNull(16) == null && it !in "0123456789abcdefABCDEF" }) {
        // 非法十六进制：直接当 UTF-8 字节（比原版更宽容）
        return hex.toByteArray(Charsets.UTF_8)
    }
    return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/** ByteArray → 十六进制串（监控日志用） */
fun bytesToHex(b: ByteArray?, limit: Int = Int.MAX_VALUE): String {
    if (b == null) return ""
    val n = minOf(b.size, limit)
    val sb = StringBuilder(n * 2)
    for (i in 0 until n) sb.append("%02x".format(b[i]))
    if (n < b.size) sb.append("...(+${b.size - n})")
    return sb.toString()
}
