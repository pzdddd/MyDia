package com.pzdd.mydia.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * JS 语法高亮（轻量自写，无第三方依赖）。
 *
 * 通过 [VisualTransformation] 挂在 TextField 上：输入层仍是纯文本，
 * 显示层用 AnnotatedString + SpanStyle 着色。选中/光标仍按原始 offset 工作。
 *
 * 配色参考 VS Code Dark+（深浅两套）：
 *  - 关键字：蓝系
 *  - 字符串：绿系
 *  - 注释：灰
 *  - 数字：橙
 *  - 内置全局：紫 / 函数调用：紫加粗
 *
 * @param dark 是否深色模式（由调用方在 Composable 里用 isSystemInDarkTheme /
 *             MaterialTheme.colorScheme 判断后传入）
 */
class JsSyntaxHighlighter(private val dark: Boolean) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text), OffsetMapping.Identity)

    private fun highlight(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        if (code.isEmpty()) return builder.toAnnotatedString()

        val kw = if (dark) D_KEYWORD else L_KEYWORD
        val str = if (dark) D_STRING else L_STRING
        val cmt = if (dark) D_COMMENT else L_COMMENT
        val num = if (dark) D_NUMBER else L_NUMBER
        val glb = if (dark) D_GLOBAL else L_GLOBAL

        // 受保护区域：字符串（单/双/模板）与注释，整体着色、内部不再细分
        val protected = mutableListOf<IntRange>()
        val tokenRe = Regex(
            """//[^\n]*|/\*[\s\S]*?\*/|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\]|\\.)*`"""
        )
        tokenRe.findAll(code).forEach { m ->
            protected.add(m.range)
            val color = if (m.value.startsWith("//") || m.value.startsWith("/*")) cmt else str
            builder.addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
        }

        fun intersects(r: IntRange): Boolean = protected.any { it.first <= r.last && it.last >= r.first }

        // 标识符 / 数字
        val identRe = Regex("""[a-zA-Z_$][\w$]*|\d+(?:\.\d+)?""")
        identRe.findAll(code).forEach { m ->
            if (intersects(m.range)) return@forEach
            val word = m.value
            val color = when {
                word in KEYWORDS -> kw
                word.first().isDigit() -> num
                word in GLOBALS -> glb
                else -> null
            }
            if (color != null) {
                builder.addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
            }
        }

        // 函数调用：标识符紧跟 ( 且未被保护 → 高亮函数名（紫加粗）
        val callRe = Regex("""[a-zA-Z_$][\w$]*(?=\()""")
        callRe.findAll(code).forEach { m ->
            if (intersects(m.range)) return@forEach
            builder.addStyle(
                SpanStyle(color = glb, fontWeight = FontWeight.Medium),
                m.range.first, m.range.last + 1,
            )
        }

        return builder.toAnnotatedString()
    }

    companion object {
        private val KEYWORDS = setOf(
            "function", "var", "let", "const", "if", "else", "for", "while", "do",
            "return", "class", "new", "this", "import", "export", "from", "try",
            "catch", "finally", "throw", "typeof", "instanceof", "in", "of", "switch",
            "case", "break", "continue", "default", "async", "await", "delete", "void",
            "yield", "static", "get", "set", "null", "undefined", "true", "false",
        )

        private val GLOBALS = setOf(
            "console", "Math", "JSON", "Object", "Array", "String", "Number", "Boolean",
            "Date", "Promise", "Map", "Set", "RegExp", "Error", "globalThis", "window",
            "document", "process", "require", "setTimeout", "setInterval", "clearTimeout",
            "clearInterval", "Symbol", "BigInt", "Proxy", "Reflect", "WeakMap", "WeakSet",
        )

        // 浅色
        private val L_KEYWORD = Color(0xFF0054A6)
        private val L_STRING = Color(0xFF2E7D32)
        private val L_COMMENT = Color(0xFF9E9E9E)
        private val L_NUMBER = Color(0xFFE65100)
        private val L_GLOBAL = Color(0xFF6A1B9A)

        // 深色（VS Code Dark+）
        private val D_KEYWORD = Color(0xFF82AAFF)
        private val D_STRING = Color(0xFFC3E88D)
        private val D_COMMENT = Color(0xFF546E7A)
        private val D_NUMBER = Color(0xFFF78C6C)
        private val D_GLOBAL = Color(0xFFC792EA)
    }
}
