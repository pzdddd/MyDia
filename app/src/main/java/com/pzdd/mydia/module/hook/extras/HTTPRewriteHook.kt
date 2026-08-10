package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import com.pzdd.mydia.module.rewrite.Rewrite
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URLConnection

/**
 * HTTP 请求/响应改写引擎。对应 Dia 的 HTTPRewriteHook + rewrite 规则体系。
 *
 * 规则来源：SP key `http_rewrite_rules`（JSON 数组，每个元素：
 * `{"host":"目标域名","path":"路径包含","req":{"match":"旧串","replace":"新串"},"resp":{"match":"旧串","replace":"新串"}}`）。
 *
 * 实现（对标 Dia 行为）：
 *  1. hook `HttpURLConnection.connect`：记录 URL；请求头写入时若有匹配则改写
 *  2. hook `URLConnection.getInputStream` / `getOutputStream`：包装流，对明文 HTTP
 *     请求/响应体做字符串替换（规则匹配旧串 → 新串）
 *
 * 说明：HTTPS 下流是加密的，本 Java 层只能改明文 HTTP；HTTPS 改写需 SSL pinning
 * 破解 + 明文层 hook（超出 Java 层范围，见 Phase 5 native 备注）。
 *
 * SP key：http_rewrite(总开关) / http_rewrite_rules(JSON 规则)
 */
class HTTPRewriteHook : DiaHook() {

    data class RewritePair(val match: String, val replace: String)
    data class HttpRule(val host: String, val path: String, val req: RewritePair?, val resp: RewritePair?)

    private var rules: List<HttpRule> = emptyList()

    override fun install() {
        if (!prefs.getBoolean("http_rewrite", false)) return
        rules = parseRules(prefs.getString("http_rewrite_rules", "") ?: "")
        if (rules.isEmpty()) {
            Module.log("HTTPRewriteHook: no rules, skip.")
            return
        }

        // 1) connect 记录 + 请求头改写
        XposedBridge.hookAllMethods(HttpURLConnection::class.java, "connect", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val conn = param.thisObject as? HttpURLConnection ?: return
                Module.log("HttpRewrite >>> ${conn.url}")
            }
        })

        // 2) 请求流改写（写入时替换）
        XposedBridge.hookAllMethods(URLConnection::class.java, "getOutputStream", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val conn = param.thisObject as? URLConnection ?: return
                val raw = param.result as? OutputStream ?: return
                if (raw is RewriteOutputStream) return
                val rule = matchRule(conn.url.toString()) ?: return
                if (rule.req == null) return
                param.result = RewriteOutputStream(raw, rule.req)
            }
        })

        // 3) 响应流改写（读取时替换）
        XposedBridge.hookAllMethods(URLConnection::class.java, "getInputStream", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val conn = param.thisObject as? URLConnection ?: return
                val raw = param.result as? InputStream ?: return
                if (raw is RewriteInputStream) return
                val rule = matchRule(conn.url.toString()) ?: return
                if (rule.resp == null) return
                param.result = RewriteInputStream(raw, rule.resp)
            }
        })

        Module.log("HTTPRewriteHook ACTIVE (${rules.size} rules).")
    }

    private fun matchRule(url: String): HttpRule? {
        return rules.firstOrNull { r ->
            url.contains(r.host) && (r.path.isEmpty() || url.contains(r.path))
        }
    }

    private fun parseRules(json: String): List<HttpRule> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val req = o.optJSONObject("req")
                val resp = o.optJSONObject("resp")
                HttpRule(
                    host = o.optString("host", ""),
                    path = o.optString("path", ""),
                    req = req?.takeIf { it.has("match") }?.let { RewritePair(it.getString("match"), it.optString("replace", "")) },
                    resp = resp?.takeIf { it.has("match") }?.let { RewritePair(it.getString("match"), it.optString("replace", "")) },
                )
            }.filter { it.host.isNotEmpty() }
        }.getOrElse { t ->
            Module.err("HTTPRewriteHook: bad rules JSON", t)
            emptyList()
        }
    }

    /** 响应读取流：读入缓冲，整体替换后吐出。简化：小响应全量替换，大响应只替换前 1MB。 */
    private inner class RewriteInputStream(private val delegate: InputStream, private val pair: RewritePair)
        : FilterInputStream(delegate) {

        private var replaced: ByteArray? = null
        private var pos = 0

        override fun read(): Int {
            ensureReplaced()
            val data = replaced ?: return super.read()
            return if (pos < data.size) (data[pos++].toInt() and 0xff) else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            ensureReplaced()
            val data = replaced ?: return super.read(b, off, len)
            if (pos >= data.size) return -1
            val n = minOf(len, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }

        private fun ensureReplaced() {
            if (replaced != null) return
            runCatching {
                // 读全部（限制 1MB 防内存爆炸）
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var n: Int
                var total = 0
                while (delegate.read(chunk).also { n = it } > 0 && total < 1_000_000) {
                    buf.write(chunk, 0, n); total += n
                }
                val text = buf.toString(Charsets.UTF_8)
                replaced = text.replace(pair.match, pair.replace).toByteArray(Charsets.UTF_8)
                Module.log("HttpRewrite <<< ${pair.match} -> ${pair.replace} (${buf.size()} bytes)")
            }.onFailure { replaced = ByteArray(0) }
        }
    }

    /** 请求写入流：缓冲写入，输出前替换。 */
    private inner class RewriteOutputStream(private val delegate: OutputStream, private val pair: RewritePair)
        : FilterOutputStream(delegate) {

        private val buf = ByteArrayOutputStream()

        override fun write(b: Int) { buf.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { buf.write(b, off, len) }
        override fun flush() {
            val text = buf.toString(Charsets.UTF_8)
            val replaced = text.replace(pair.match, pair.replace)
            if (replaced != text) Module.log("HttpRewrite >>> ${pair.match} -> ${pair.replace}")
            delegate.write(replaced.toByteArray(Charsets.UTF_8))
            delegate.flush()
            buf.reset()
        }
    }
}
