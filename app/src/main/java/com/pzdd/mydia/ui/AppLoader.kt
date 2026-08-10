package com.pzdd.mydia.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一个已安装 App 的轻量信息（避免持有完整 ApplicationInfo 导致内存占用）。
 */
data class AppInfo(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val enabled: Boolean, // per-app 模块开关（读 SP，不是 App 自身 enabled）
)

/**
 * 在 IO 线程加载全部已安装 App。
 *
 * @param includeSystem 是否包含系统 App
 */
suspend fun loadInstalledApps(context: Context, includeSystem: Boolean): List<AppInfo> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { includeSystem || !it.isSystemApp() }
            .map { ai ->
                // per-app 总开关存在以包名为名的 SP 文件里（与注入侧 appPrefs 对齐）
                // 读模式 MODE_PRIVATE（Android 13+ 禁止 WORLD_READABLE 模式）；
                // 写入方已负责 chmod 0644，注入侧可跨进程读
                val appSp = context.getSharedPreferences(ai.packageName, Context.MODE_PRIVATE)
                AppInfo(
                    pkg = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    isSystem = ai.isSystemApp(),
                    enabled = appSp.getBoolean("enabled", false),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

/** 懒加载 App 图标（缓存到内存）。 */
fun loadAppIcon(context: Context, pkg: String): Drawable? = runCatching {
    context.packageManager.getApplicationIcon(pkg)
}.getOrNull()

/** Drawable 转 Bitmap（用于 Compose Image）。 */
fun drawableToBitmap(drawable: Drawable): android.graphics.Bitmap {
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

/** ApplicationInfo 是否系统 App。 */
private fun ApplicationInfo.isSystemApp(): Boolean =
    (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
