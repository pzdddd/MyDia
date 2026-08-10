package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * SQL 监控。对应 Dia 的 sql/SqlManager + WcdbHook/SqlcipherHook + mod_ex_dev 的 sql。
 *
 * hook SQLiteDatabase.execSQL / query / insert / delete / update，
 * 把执行的 SQL 语句打到 logcat（TAG=MyDia/Sql）。
 *
 * Dia 完整版还 hook 了 WCDB（微信加密库）和 SQLCipher；本骨架实现标准 SQLiteDatabase，
 * 第三方加密 DB 库可按相同模式扩展。
 *
 * sql_hook_native：native 层 SQL hook（需原生模块），本骨架不实现。
 *
 * SP key：sql(总开关) / sql_hook_native / sql_detail
 */
class SqlMonitorHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("sql", false)) return
        runCatching {
            val cls = Class.forName("android.database.sqlite.SQLiteDatabase")
            val methods = if (prefs.getBoolean("sql_detail", false))
                listOf("execSQL", "query", "queryWithFactory", "insert", "insertWithOnConflict",
                       "delete", "update", "rawQuery")
            else listOf("execSQL", "query", "insert", "delete", "update", "rawQuery")

            for (m in methods) {
                XposedBridge.hookAllMethods(cls, m, object : MethodHook() {
                    override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        // 多数方法 args[0] 是 SQL（execSQL/rawQuery）或表名（insert/delete/update）
                        val sql = param.args.firstOrNull()?.toString() ?: "?"
                        Module.log("Sql >>> ${param.method.name}: $sql")
                    }
                })
            }
        }
        if (prefs.getBoolean("sql_hook_native", false)) {
            Module.log("SqlMonitor: sql_hook_native requested (需原生模块，TODO)")
        }
        Module.log("SqlMonitor: installed")
    }
}
