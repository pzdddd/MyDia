package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * HTTP/Socket 监控（明文抓包）。对应 Dia 的 socket/monitor/SocketHook +
 * InputStreamWrapper/OutputStreamWrapper。
 *
 * 功能：
 *  1. `Socket.connect` 记录目标地址（IP+端口）
 *  2. `Socket.getInputStream` / `getOutputStream` 返回包装流，抓取明文读写内容
 *     （HTTP 明文、WebSocket、自定义 TCP 协议可见；TLS 握手后加密不可见）
 *  3. `URL.openConnection` 记录目标 URL
 *
 * 抓到的内容输出到 logcat（TAG=MyDia/Http），并限制单条长度防刷屏。
 *
 * SP key：monitor_http_switch(总开关) / monitor_http_capture(是否抓明文，默认 true)
 */
class HttpMonitorHook : DiaHook() {

    private var captureBody = true

    override fun install() {
        if (!prefs.getBoolean("monitor_http_switch", false)) return
        captureBody = prefs.getBoolean("monitor_http_capture", true)

        // 1) connect：记录目标
        XposedBridge.hookAllMethods(Socket::class.java, "connect", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val addr = param.args.getOrNull(0) as? InetSocketAddress ?: return
                Module.log("Http >>> connect ${addr.hostString}:${addr.port}")
            }
        })

        // 2) getInputStream / getOutputStream：包装流抓明文
        XposedBridge.hookAllMethods(Socket::class.java, "getInputStream", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val raw = param.result as? InputStream ?: return
                if (raw is CaptureInputStream) return   // 防重复包装
                param.result = CaptureInputStream(raw)
            }
        })
        XposedBridge.hookAllMethods(Socket::class.java, "getOutputStream", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val raw = param.result as? OutputStream ?: return
                if (raw is CaptureOutputStream) return
                param.result = CaptureOutputStream(raw)
            }
        })

        // 3) URL.openConnection 兜底
        runCatching {
            XposedBridge.hookAllMethods(Class.forName("java.net.URL"), "openConnection", object : MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    Module.log("Http >>> openConnection $param.thisObject")
                }
            })
        }
        Module.log("HttpMonitor: installed (connect + capture=$captureBody)")
    }

    /** 输入捕获流：累计 read 的字节，凑成行后输出（简单按换行/长度切分）。 */
    private inner class CaptureInputStream(private val delegate: InputStream) : FilterInputStream(delegate) {
        private val buffer = ByteArrayOutputStream()

        override fun read(): Int {
            val b = super.read()
            if (b >= 0 && captureBody) captureByte(b.toByte())
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0 && captureBody) for (i in off until off + n) captureByte(b[i])
            return n
        }

        private fun captureByte(b: Byte) {
            buffer.write(b.toInt())
            if (b.toInt() == '\n'.code || buffer.size() >= 4096) flushCapture()
        }

        private fun flushCapture() {
            if (buffer.size() == 0) return
            val text = String(buffer.toByteArray(), Charsets.ISO_8859_1)
            buffer.reset()
            Module.log("Http <<< ${text.take(2000)}")
        }
    }

    /** 输出捕获流：累计 write 的字节，凑成行后输出。 */
    private inner class CaptureOutputStream(private val delegate: OutputStream) : FilterOutputStream(delegate) {
        private val buffer = ByteArrayOutputStream()

        override fun write(b: Int) {
            delegate.write(b)
            if (captureBody) captureByte(b.toByte())
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            if (captureBody) for (i in off until off + len) captureByte(b[i])
        }

        private fun captureByte(b: Byte) {
            buffer.write(b.toInt())
            if (b.toInt() == '\n'.code || buffer.size() >= 4096) flushCapture()
        }

        private fun flushCapture() {
            if (buffer.size() == 0) return
            val text = String(buffer.toByteArray(), Charsets.ISO_8859_1)
            buffer.reset()
            Module.log("Http >>> ${text.take(2000)}")
        }
    }
}
