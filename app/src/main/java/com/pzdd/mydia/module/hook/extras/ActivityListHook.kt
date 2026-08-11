package com.pzdd.mydia.module.hook.extras

import android.content.Context
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.pzdd.mydia.module.LibXposedEntry
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.ApplicationHook
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 目标 App 的 Activity 列表自动枚举（对齐 Dia：注入后无需进 MyDia 软件即可获得）。
 *
 * 在目标 App 进程内用 PackageManager 枚举全部 Activity，然后：
 *  1. 写入 LSPosed Remote Preferences（`activity_list` JSON 数组，组名 = 目标包名）——
 *     MyDia UI 的「选择入口/禁用 Activity」页直接读 remote 就有列表，不依赖 UI 侧
 *     PackageManager（QUERY_ALL_PACKAGES / 权限差异）。
 *  2. 逐条输出到模块日志 → 远程日志控制台，不用进软件就能看到列表。
 *
 * 无条件注册（无需开关）：任何目标 App 被注入后自动执行一次。
 */
class ActivityListHook : DiaHook(), ApplicationHook.OnAppReady {

    override fun install() {
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady(this)
    }

    override fun onReady(ctx: Context) {
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_ACTIVITIES)
            val names = info.activities?.map { it.name }?.distinct() ?: emptyList()
            val launcher = runCatching {
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component?.className
            }.getOrNull()

            // 1) 写 remote：UI 选择器直接读（列表 + launcher 标记）
            LibXposedEntry.instance?.getRemotePreferences(ctx.packageName)?.edit()
                ?.putString(KEY, Gson().toJson(ActivityEntryList(names, launcher)))
                ?.commit()

            // 2) 日志输出（远程日志控制台可见，无需进软件）
            Module.log("ActivityList: ${names.size} activities (launcher=$launcher)")
            names.forEach { Module.log("ActivityList:   $it") }
        }.onFailure { Module.err("ActivityListHook enumerate failed", it) }
    }

    /** remote SP key：目标 App 的 Activity 列表（JSON）。 */
    companion object {
        const val KEY = "activity_list"
    }
}

/** 序列化结构：Activity 列表 + 默认 launcher。 */
data class ActivityEntryList(
    val activities: List<String>,
    val launcher: String?,
)
