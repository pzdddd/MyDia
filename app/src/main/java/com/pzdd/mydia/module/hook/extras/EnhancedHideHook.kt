package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 增强隐藏：补 Java 层反检测缺口（现有 hook 未覆盖的部分）。
 *
 * 现有 HideXposedHook/FakeXposedHook/FileHook 已覆盖 Class.forName/loadClass/
 * getStackTrace/File.exists 等，但以下检测仍绕不过：
 *
 *  1. **新 libxposed API 类检测**：App 检查 `io.github.libxposed.api.*` 类是否存在
 *  2. **`/proc/self/maps` 内容读取**：App 读 maps 找 xposed/lsposed/magisk 的 so 名
 *  3. **`/proc/self/status` 的 TracerPid**：App 检查是否被调试
 *
 * SP key：enhanced_hide(总开关)
 */
class EnhancedHideHook : DiaHook() {

    companion object {
        private val MAPS_FILTER = listOf(
            "xposed", "lsposed", "edxposed", "riru", "zygisk",
            "lsplant", "magisk", "libxposed", "libsunloader",
        )
        private val LIBXPOSED_CLASSES = listOf(
            "io.github.libxposed.api.XposedModule",
            "io.github.libxposed.api.XposedInterface",
            "io.github.libxposed.api",
            "io.github.libxposed.service.XposedService",
        )
        private val cleanStreams: MutableMap<FileInputStream, ByteArrayInputStream> = ConcurrentHashMap()
        private val checkedInstances: MutableSet<Any> = ConcurrentHashMap.newKeySet()
    }

    override fun install() {
        if (!prefs.getBoolean("enhanced_hide", false)) {
            Module.log("EnhancedHideHook: disabled")
            return
        }
        hookLibxposedDetection()
        hookProcFiles()
        Module.log("EnhancedHideHook ACTIVE (libxposed + maps + status)")
    }

    /** 拦截 Class.forName / ClassLoader.loadClass 对 libxposed 新 API 类的探测。 */
    private fun hookLibxposedDetection() {
        runCatching {
            XposedBridge.hookAllMethods(Class::class.java, "forName", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.args.getOrNull(0) as? String ?: return
                    if (LIBXPOSED_CLASSES.any { name.startsWith(it) }) {
                        param.throwable = ClassNotFoundException(name)
                    }
                }
            })
        }
        runCatching {
            XposedBridge.hookAllMethods(ClassLoader::class.java, "loadClass", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.args.getOrNull(0) as? String ?: return
                    if (LIBXPOSED_CLASSES.any { name.startsWith(it) }) {
                        param.throwable = ClassNotFoundException(name)
                    }
                }
            })
        }
    }

    /**
     * hook FileInputStream.read()：对 /proc/self/maps 和 /proc/self/status 做内容过滤。
     * 首次访问某实例时检查路径，若命中则预读真实内容 → 过滤 → 缓存 ByteArrayInputStream。
     * 后续 read 调用从替代流读。
     */
    private fun hookProcFiles() {
        runCatching {
            XposedBridge.hookAllMethods(FileInputStream::class.java, "read", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val fis = param.thisObject as? FileInputStream ?: return
                    // 首次：检查路径并预读
                    if (!checkedInstances.contains(fis)) {
                        checkedInstances.add(fis)
                        val path = getFilePath(fis)
                        if (path != null) {
                            val filtered = filterProcContent(path) ?: return
                            cleanStreams[fis] = ByteArrayInputStream(filtered.toByteArray())
                        }
                    }
                    // 如果有替代流 → 从替代流读
                    val clean = cleanStreams[fis] ?: return
                    when (param.args.size) {
                        0 -> param.result = clean.read()
                        1 -> { param.result = clean.read(param.args[0] as ByteArray) }
                        3 -> {
                            param.result = clean.read(
                                param.args[0] as ByteArray,
                                param.args[1] as Int,
                                param.args[2] as Int,
                            )
                        }
                    }
                }
            })
        }
    }

    /** 反射拿 FileInputStream 的 path 字段。 */
    private fun getFilePath(fis: FileInputStream): String? = runCatching {
        var cls: Class<*>? = fis.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField("path").apply { isAccessible = true }
                return f.get(fis) as? String
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        null
    }.getOrNull()

    /**
     * 根据 /proc 文件路径过滤内容：
     *  - /proc/self/maps → 删除含 xposed/magisk/lsposed 等关键字的行
     *  - /proc/self/status → TracerPid 行改成 0
     * 返回 null 表示不需要过滤。
     */
    private fun filterProcContent(path: String): String? {
        val real = runCatching { java.io.File(path).readText() }.getOrNull() ?: return null
        return when {
            path.contains("/proc/self/maps") || path.contains("/proc/\$\$/maps") -> {
                real.lines().filter { line ->
                    MAPS_FILTER.none { kw -> line.contains(kw, ignoreCase = true) }
                }.joinToString("\n") + "\n"
            }
            path.contains("/proc/self/status") -> {
                real.lines().map { line ->
                    if (line.startsWith("TracerPid:")) "TracerPid:\t0" else line
                }.joinToString("\n") + "\n"
            }
            else -> null
        }
    }
}
