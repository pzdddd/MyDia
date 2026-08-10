package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 加密数据库 SQL 监控。对应 Dia 的 SqlcipherHook + WcdbHook + sql_hook_native。
 *
 * 目标 App 若用 SQLCipher（net.sqlcipher）或 WCDB（com.tencent.wcdb，微信系）加密
 * 数据库，标准 SQLiteDatabase hook 抓不到。本类按名反射 hook 这两个库的
 * execSQL/query/insert/delete/update/rawQuery，输出 SQL 到 logcat。
 *
 * 库未集成时 findClass 失败自动跳过（与 Dia 一致的容错）。
 *
 * SP key：sql(总开关，与 SqlMonitorHook 共用) / sql_hook_native(原生层标记)
 */
class SqlcipherHook : DiaHook() {

    private val targets = listOf(
        "net.sqlcipher.database.SQLiteDatabase" to "SQLCipher",
        "com.tencent.wcdb.database.SQLiteDatabase" to "WCDB",
    )

    override fun install() {
        if (!prefs.getBoolean("sql", false)) return
        val detail = prefs.getBoolean("sql_detail", false)
        val methods = if (detail)
            listOf("execSQL", "query", "queryWithFactory", "insert", "insertWithOnConflict",
                   "delete", "update", "rawQuery")
        else listOf("execSQL", "query", "insert", "delete", "update", "rawQuery")

        for ((clsName, tag) in targets) {
            runCatching {
                val cls = Class.forName(clsName, false, classLoader)
                for (m in methods) {
                    XposedBridge.hookAllMethods(cls, m, object : MethodHook() {
                        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                            val sql = param.args.firstOrNull()?.toString() ?: "?"
                            Module.log("$tag >>> ${param.method.name}: $sql")
                        }
                    })
                }
                Module.log("SqlMonitor: $tag hooked")
            }.onFailure { /* 库不存在，跳过 */ }
        }

        if (prefs.getBoolean("sql_hook_native", false)) {
            // native 层 SQL hook 已在 Phase 5 由 libmydia_hook.so 提供（sqlite3_exec 层）
            Module.log("SqlMonitor: sql_hook_native -> 由原生模块提供（Phase 5）")
        }
    }
}
