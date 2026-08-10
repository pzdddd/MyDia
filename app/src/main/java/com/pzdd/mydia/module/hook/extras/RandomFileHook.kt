package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlin.random.Random

/**
 * 随机设备 id 文件内容。对应 Dia 的 RandomFileHook。
 *
 * 用途：有些 App 把设备唯一标识（IMEI/android_id/uuid）写到本地文件缓存，下次启动直接读
 * 文件绕过系统 API hook。本 hook 拦截对指定文件的读取，返回随机内容，每次启动都「新设备」。
 *
 * 实现（完整的字节级替换）：
 *  1. hook `FileInputStream(File)` / `FileInputStream(String)` 构造：命中路径 → 记录该流实例
 *  2. hook `FileInputStream.read()` / `read(byte[],int,int)`：命中实例返回随机字节
 *  3. hook `File.length()` / `File.exists()`：命中路径返回随机长度 / true
 *
 * SP key：random_file_content(总开关) / select_random_range(0数字 1UUID 2混合) / input_random_file(空格分隔路径)
 */
class RandomFileHook : DiaHook() {

    /** 已命中目标的流实例 → 该流循环吐出的随机字节（IdentityHashMap 防混淆）。 */
    private val hitStreams = java.util.Collections.synchronizedMap(
        java.util.IdentityHashMap<FileInputStream, ByteArray>()
    )

    private var files: Set<String> = emptySet()
    private var gen: () -> ByteArray = { ByteArray(0) }
    private var randomLen: () -> Long = { 16 }

    override fun install() {
        if (!prefs.getBoolean("random_file_content", false)) return
        val range = prefs.getString("select_random_range", "0")?.toIntOrNull() ?: 0
        files = (prefs.getString("input_random_file", "") ?: "")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (files.isEmpty()) {
            Module.log("RandomFileHook: no target files configured, skip.")
            return
        }

        when (range) {
            1 -> { // UUID（36 字节）
                randomLen = { 36 }
                gen = { UUID.randomUUID().toString().toByteArray() }
            }
            2 -> { // 字母数字混合（16 字节）
                randomLen = { 16 }
                gen = { (1..16).map { (('a'..'z') + ('0'..'9')).random() }.joinToString("").toByteArray() }
            }
            else -> { // 数字串（15 位，IMEI 风格）
                randomLen = { 15 }
                gen = { (100000000000000L..999999999999999L).random().toString().toByteArray() }
            }
        }

        // 1) 构造时：命中路径 → 生成一份随机内容绑定到该流实例
        val constructorHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val stream = param.thisObject as? FileInputStream ?: return
                val path = param.args.getOrNull(0)?.let {
                    when (it) {
                        is File -> it.absolutePath
                        is String -> it
                        else -> null
                    }
                } ?: return
                if (path in files) hitStreams[stream] = gen()
            }
        }
        runCatching { XposedHelpers.findAndHookConstructor(FileInputStream::class.java, File::class.java, constructorHook) }
        runCatching { XposedHelpers.findAndHookConstructor(FileInputStream::class.java, String::class.java, constructorHook) }

        // 2) 无参 read()：返回随机内容的下一字节
        XposedBridge.hookAllMethods(FileInputStream::class.java, "read", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args.isNotEmpty()) return   // 只处理无参 read()
                val data = hitStreams[param.thisObject] ?: return
                param.result = readNext(data)
            }
        })

        // 3) read(byte[], off, len)：填充随机内容
        XposedBridge.hookAllMethods(FileInputStream::class.java, "read", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args.isEmpty()) return      // 只处理带参 read
                val data = hitStreams[param.thisObject] ?: return
                val buf = param.args.getOrNull(0) as? ByteArray ?: return
                val off = (param.args.getOrNull(1) as? Int) ?: 0
                val len = (param.args.getOrNull(2) as? Int) ?: 0
                val n = minOf(len, data.size)
                System.arraycopy(data, 0, buf, off, n)
                param.result = n
            }
        })

        // 4) File.length / exists 伪装
        XposedBridge.hookAllMethods(File::class.java, "length", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val f = param.thisObject as? File ?: return
                if (f.absolutePath in files) param.result = randomLen()
            }
        })
        XposedBridge.hookAllMethods(File::class.java, "exists", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val f = param.thisObject as? File ?: return
                if (f.absolutePath in files) param.result = true
            }
        })

        Module.log("RandomFileHook ACTIVE (targets=${files.size}, range=$range).")
    }

    /** 随机内容循环读取（超出末尾回到开头，模拟文件循环）。 */
    private fun readNext(data: ByteArray): Int {
        if (data.isEmpty()) return -1
        return data[Random.nextInt(data.size)].toInt() and 0xff
    }
}
