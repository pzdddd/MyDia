package com.pzdd.mydia.algorithm

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.util.Base64
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import java.util.concurrent.ConcurrentHashMap

/**
 * 算法监控核心：hook MessageDigest / Mac / Cipher / Base64，
 * 把每次摘要/签名/加解密的输入、输出、密钥、IV 通过广播回传给 MyDia App 端展示。
 *
 * 对应 Dia 的：
 *  - com.mhook.dialog.task.hook.algorithm.AlgorithmHookManager（装钩子 + 广播）
 *  - com.mhook.dialog.task.hook.algorithm.AlgorithmHook（XC_MethodHook 实现，@MTProtector 加密）
 *
 * 这里把两者合并到一个文件，并用 Kotlin 重写 Dia 那段被 MTProtector 加密、
 * 反编译不完整的逻辑——行为对齐 Dia：
 *  - getInstance → 建对象与算法名的映射
 *  - update → 累加输入字节
 *  - digest / doFinal → 取输出，触发广播
 *  - Cipher/Mac init → 记录 key
 *  - Base64.encode/decode → 直接记录输入输出
 */
object AlgorithmHookManager {

    /** 广播 action（App 端 receiver 监听这个） */
    const val ACTION = "com.pzdd.mydia.ACTION_ALGORITHM_MONITOR"

    /** MyDia App 端接收算法数据的 receiver 组件 */
    private val RECEIVER = ComponentName(
        "com.pzdd.mydia",
        "com.pzdd.mydia.monitor.AlgorithmMonitorReceiver"
    )

    /** Cipher/Mac/MessageDigest 实例 → 其 [ObjectInfo] */
    private val objInfos = ConcurrentHashMap<Any, ObjectInfo>()

    /** 启动监控：装上所有相关钩子。在 ApplicationHook.onReady 之后调用 */
    fun start() {
        val hook = AlgorithmHook

        // MessageDigest
        XposedBridge.hookAllMethods(MessageDigest::class.java, "getInstance", hook)
        XposedBridge.hookAllMethods(MessageDigest::class.java, "update", hook)
        XposedBridge.hookAllMethods(MessageDigest::class.java, "digest", hook)

        // Mac（HMAC）
        XposedBridge.hookAllMethods(Mac::class.java, "getInstance", hook)
        XposedBridge.hookAllMethods(Mac::class.java, "update", hook)
        XposedBridge.hookAllMethods(Mac::class.java, "doFinal", hook)
        XposedBridge.hookAllMethods(Mac::class.java, "init", hook)

        // Cipher（对称/非对称加解密）
        XposedBridge.hookAllMethods(Cipher::class.java, "getInstance", hook)
        XposedBridge.hookAllMethods(Cipher::class.java, "update", hook)
        XposedBridge.hookAllMethods(Cipher::class.java, "doFinal", hook)
        XposedBridge.hookAllMethods(Cipher::class.java, "init", hook)

        // Base64（用精确签名，避免 hook 到内部重载）
        val intCls = Int::class.javaPrimitiveType
        try {
            XposedHelpers.findAndHookMethod(
                Base64::class.java, "encode",
                ByteArray::class.java, intCls, intCls, intCls, hook
            )
        } catch (_: Throwable) {}
        try {
            XposedHelpers.findAndHookMethod(
                Base64::class.java, "decode",
                ByteArray::class.java, intCls, intCls, intCls, hook
            )
        } catch (_: Throwable) {}

        Module.log("AlgorithmHookManager: hooks installed")
    }

    fun infoOf(obj: Any): ObjectInfo? = objInfos[obj]

    fun registerInstance(obj: Any, name: String) {
        objInfos.putIfAbsent(obj, ObjectInfo(name))
    }

    /** 数据齐全 → 广播 + 移除条目（对齐 Dia 的 m11830） */
    fun reportAndConsume(obj: Any) {
        val info = objInfos[obj] ?: return
        if (!info.hasPayload()) return
        sendBroadcast(info)
        objInfos.remove(obj)
    }

    /** 直接广播一条 [info]（无状态对象如 Base64 静态方法用） */
    fun report(info: ObjectInfo) {
        if (info.hasPayload()) sendBroadcast(info)
    }

    private fun sendBroadcast(info: ObjectInfo) {
        try {
            val app = currentApplication() ?: return
            val intent = Intent(ACTION).apply {
                component = RECEIVER
                putExtra("process", Module.processName)
                putExtra("package_name", Module.packageName)
                putExtra("thread", Thread.currentThread().name)
                putExtra("stack", info.stack)
                putExtra("al_name", info.name)
                info.data?.let { putExtra("al_data", Base64.encodeToString(it, Base64.NO_WRAP)) }
                info.ret?.let { putExtra("return", Base64.encodeToString(it, Base64.NO_WRAP)) }
                info.key?.let { putExtra("al_key", Base64.encodeToString(it, Base64.NO_WRAP)) }
                info.iv?.let { putExtra("al_iv", Base64.encodeToString(it, Base64.NO_WRAP)) }
            }
            app.sendBroadcast(intent)
        } catch (t: Throwable) {
            Module.err("AlgorithmHookManager sendBroadcast failed", t)
        }
    }

    /** 反射拿 ActivityThread.currentApplication()（注入侧没有直接的 Application 引用） */
    private fun currentApplication(): Application? = runCatching {
        val atCls = Class.forName("android.app.ActivityThread")
        val m = atCls.getDeclaredMethod("currentApplication")
        m.invoke(null) as? Application
    }.getOrNull()
}

/**
 * 单例 XC_MethodHook：分发 getInstance/update/digest/doFinal/init/encode/decode。
 * 对应 Dia 的 AlgorithmHook（原版被 @MTProtector 加密）。
 */
object AlgorithmHook : XC_MethodHook() {

    override fun beforeHookedMethod(param: MethodHookParam) {
        val name = param.method.name
        when (name) {
            "getInstance" -> {
                // 建立对象 ↔ 算法名映射（实际关联在 after 里拿 result 做）
            }
            "init" -> handleInit(param)
            "update" -> handleUpdate(param)
        }
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        val name = param.method.name
        when (name) {
            "getInstance" -> {
                val algo = param.args.firstOrNull()?.toString() ?: "?"
                param.result?.let { AlgorithmHookManager.registerInstance(it, algo) }
            }
            "digest" -> handleDigest(param)
            "doFinal" -> handleDoFinal(param)
            "encode", "decode" -> handleBase64(param, name)
        }
    }

    private fun handleInit(param: MethodHookParam) {
        val obj = param.thisObject ?: return
        val info = AlgorithmHookManager.infoOf(obj) ?: return
        when (obj) {
            is Cipher -> {
                // Cipher.init(opmode, key, ...) —— key 在 args[1]
                (param.args.getOrNull(1) as? java.security.Key)?.encoded?.let { info.setKey(it) }
            }
            is Mac -> {
                // Mac.init(key) —— key 在 args[0]
                (param.args.getOrNull(0) as? java.security.Key)?.encoded?.let { info.setKey(it) }
            }
        }
    }

    private fun handleUpdate(param: MethodHookParam) {
        val obj = param.thisObject ?: return
        val info = AlgorithmHookManager.infoOf(obj) ?: return
        val args = param.args
        when (obj) {
            is Cipher -> {
                when (args.size) {
                    1 -> (args[0] as? ByteArray)?.let { info.appendData(it) }
                    2 -> (args[0] as? ByteBuffer)?.array()?.let { info.appendData(it) }
                    else -> byteArraySlice(args[0], args.getOrNull(1), args.getOrNull(2))?.let { info.appendData(it.first, it.second, it.third) }
                }
            }
            is MessageDigest, is Mac -> {
                when (args.size) {
                    1 -> {
                        when (val a = args[0]) {
                            is ByteArray -> info.appendData(a)
                            is ByteBuffer -> info.appendData(a.array())
                            is Byte -> info.appendData(byteArrayOf(a))
                        }
                    }
                    3 -> byteArraySlice(args[0], args[1], args[2])?.let { info.appendData(it.first, it.second, it.third) }
                }
            }
        }
    }

    private fun handleDigest(param: MethodHookParam) {
        val obj = param.thisObject as? MessageDigest ?: return
        val info = AlgorithmHookManager.infoOf(obj) ?: return
        info.stack = android.util.Log.getStackTraceString(Throwable("algorithm monitor"))
        val args = param.args
        when (args.size) {
            0 -> info.setReturn(param.result as? ByteArray)
            3 -> byteArraySlice(args[0], args[1], args[2])?.let { (b, off, len) ->
                // digest(byte[], off, len) 把结果写入传入数组
                info.setReturn(b, off, len)
            }
        }
        AlgorithmHookManager.reportAndConsume(obj)
    }

    private fun handleDoFinal(param: MethodHookParam) {
        val obj = param.thisObject ?: return
        val info = AlgorithmHookManager.infoOf(obj) ?: return
        info.stack = android.util.Log.getStackTraceString(Throwable("algorithm monitor"))
        when (obj) {
            is Mac -> {
                if (param.args.isEmpty()) info.setReturn(param.result as? ByteArray)
            }
            is Cipher -> {
                if (param.args.size == 1) info.appendData(param.args[0] as? ByteArray)
                if (param.args.isEmpty() || param.args.size == 1) info.setReturn(param.result as? ByteArray)
            }
        }
        AlgorithmHookManager.reportAndConsume(obj)
    }

    private fun handleBase64(param: MethodHookParam, name: String) {
        val mode = (param.args.getOrNull(3) as? Int) ?: 0
        val info = ObjectInfo("Base64${if (name == "encode") "Encode" else "Decode"}-mode:$mode")
        info.setData(param.args[0] as? ByteArray)
        info.setReturn(param.result as? ByteArray)
        info.stack = android.util.Log.getStackTraceString(Throwable("algorithm monitor"))
        // Base64 是静态方法，没有 thisObject 可关联，直接广播
        AlgorithmHookManager.report(info)
    }

    /** (ByteArray, offsetInt, lenInt) → Triple，非法返回 null */
    private fun byteArraySlice(
        b: Any?, off: Any?, len: Any?
    ): Triple<ByteArray, Int, Int>? {
        val arr = b as? ByteArray ?: return null
        val o = (off as? Int) ?: return null
        val l = (len as? Int) ?: return null
        return Triple(arr, o, l)
    }
}
