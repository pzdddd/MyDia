package com.pzdd.mydia.module.tool

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root 文件读写工具。对应 Dia 的 dialog.box.root.RootFile + RootFileService。
 *
 * 通过 su 以 root 权限读写系统文件（App 无法直接访问的 /data、/system 等）。
 * 供高级功能（隐藏检测项、改系统属性、注入文件）使用。
 *
 * 用法：
 *  - [isRootAvailable]：是否有 root 权限
 *  - [readFile]：root 读文件内容（空 = 失败/无权限）
 *  - [writeFile]：root 写文件
 *  - [exec]：root 执行命令
 */
object RootFile {

    /** su 是否可用（尝试执行 `su -c id`）。 */
    fun isRootAvailable(): Boolean = exec("id")?.contains("uid=0") == true

    /** root 读取文件全文。失败返回空串。 */
    fun readFile(path: String): String = exec("cat \"$path\" 2>/dev/null") ?: ""

    /** root 写文件（覆盖）。返回是否成功。 */
    fun writeFile(path: String, content: String): Boolean {
        // 用 base64 避免特殊字符转义问题
        val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        return exec("echo $b64 | base64 -d > \"$path\" 2>/dev/null") != null
    }

    /** root 追加写文件。 */
    fun appendFile(path: String, content: String): Boolean {
        val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        return exec("echo $b64 | base64 -d >> \"$path\" 2>/dev/null") != null
    }

    /** root 删除文件。 */
    fun deleteFile(path: String): Boolean = exec("rm -f \"$path\"") != null

    /** root 执行命令，返回 stdout。失败/超时返回 null。 */
    fun exec(command: String): String? {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            // 读输出（防阻塞用短超时轮询）
            val t = Thread {
                reader.forEachLine { output.append(it).append('\n') }
            }.apply { isDaemon = true }.also { it.start() }
            Thread {
                while (errReader.ready()) errReader.read()
            }.apply { isDaemon = true }.also { it.start() }
            val finished = process.waitFor()
            t.join(1000)
            if (finished != 0 && output.isEmpty()) null else output.toString().trim()
        }.getOrNull()
    }
}
