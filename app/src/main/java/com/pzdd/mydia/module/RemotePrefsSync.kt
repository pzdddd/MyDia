package com.pzdd.mydia.module

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import java.io.File

/**
 * 把本地 SharedPreferences 文件同步到 LSPosed Remote Preferences（daemon 数据库）。
 *
 * **为什么**：注入侧（目标 App 进程）读模块配置有两个通道：
 *  1. Remote Preferences（[LibXposedEntry.instance.getRemotePreferences]）—— daemon binder，
 *     天然绕过 SELinux 文件隔离（MIUI 上 app_data_file 目录跨 uid 读取被 SELinux 拒绝）；
 *  2. 直接读 SP 文件——需 chmod 0644 且 SELinux 放行（不可靠）。
 *
 * 所以 UI 侧写配置后，App 进程把 SP 文件内容同步一份到 remote，注入侧优先读 remote。
 * 同步在 [ActivationManager] 绑定 LSPosed service 后执行（每次启动一次，幂等）。
 */
object RemotePrefsSync {

    /**
     * 把 shared_prefs 目录下所有模块 SP 文件同步到 remote（组名 = 文件名）。
     * 由 ActivationManager 绑定 service 后调用。
     */
    fun syncAll(context: Context, service: XposedService) {
        runCatching {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".xml")) {
                    val group = f.name.removeSuffix(".xml")
                    if (group.isNotEmpty()) {
                        val local = context.getSharedPreferences(group, Context.MODE_PRIVATE)
                        val remote = service.getRemotePreferences(group)
                        copyToRemote(local, remote)
                    }
                }
            }
        }
    }

    /** 拷贝单个本地 SP 到 remote（键值全量覆盖）。 */
    fun copyToRemote(local: SharedPreferences, remote: SharedPreferences) {
        runCatching {
            val editor = remote.edit()
            local.all.forEach { (k, v) ->
                when (v) {
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is Set<*> -> editor.putStringSet(k, v.map { it.toString() }.toSet())
                }
            }
            editor.commit()
        }
    }

    /**
     * UI 侧每次写本地 SP 后调用：把该 SP 同步到 remote（daemon 数据库），
     * 让注入侧（常驻目标进程）能立刻读到最新配置，无需重启目标 App。
     *
     * 组名 = SP 文件名（反射 SharedPreferencesImpl.mName），
     * 与注入侧 PrefsFile.of(name) 的组名一致。
     */
    fun syncLocal(sp: SharedPreferences) {
        val service = ActivationManager.service ?: return
        runCatching {
            val name = sp.javaClass.getDeclaredField("mName").apply { isAccessible = true }
                .get(sp) as? String ?: return
            val remote = service.getRemotePreferences(name)
            copyToRemote(sp, remote)
        }
    }
}
