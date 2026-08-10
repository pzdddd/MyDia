package com.pzdd.mydia.module.hook.enhance

import android.app.Activity
import android.content.Intent
import com.pzdd.mydia.module.Module
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 设置应用入口。对应 Dia 的 app_entry + AppEntryHook。
 *
 * 描述：「Specify an Activity of the application as the entry point」
 * 原理：让用户指定一个 Activity 类名作为 App 的「真正启动页」。
 *  - hook `Instrumentation.newActivity`：当系统要创建【原 launcher Activity】时，替换成用户指定的目标
 *  - 兜底 hook `Activity.onCreate`：如果 newActivity 没拦到（如已实例化），检测到原 launcher
 *    被打开就 startActivity 跳到目标并 finish 自己
 *
 * 开关：app_entry + app_activity_select（存目标 Activity 全类名）。
 */
class AppEntryHook : DiaHookEntry() {

    /** 用户指定的入口 Activity 全类名 */
    private var targetActivity: String = ""

    override fun installImpl() {
        if (!prefs.getBoolean("app_entry", false)) {
            Module.log("AppEntryHook: disabled"); return
        }
        targetActivity = prefs.getString("app_activity_select", "") ?: ""
        if (targetActivity.isBlank()) {
            Module.log("AppEntryHook: no target activity set, skip"); return
        }

        // 1) hook Instrumentation.newActivity(ClassLoader, String className, Intent)
        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.app.Instrumentation", classLoader,
                "newActivity",
                ClassLoader::class.java, String::class.java, Intent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val orig = param.args[1] as? String ?: return
                        val launcher = currentLauncher ?: return
                        if (orig == launcher) {
                            param.args[1] = targetActivity
                            Module.log("AppEntryHook: newActivity $orig -> $targetActivity")
                        }
                    }
                }
            )
        }

        // 2) 兜底：Activity.onCreate 检测到 launcher 被打开就跳转
        XposedBridge.hookAllMethods(Activity::class.java, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val act = param.thisObject as? Activity ?: return
                val launcher = currentLauncher ?: return
                val current = act.javaClass.name
                if (current == launcher && current != targetActivity) {
                    runCatching {
                        val cls = Class.forName(targetActivity, false, act.classLoader)
                        act.startActivity(Intent(act, cls))
                        act.finish()
                        Module.log("AppEntryHook: redirect $current -> $targetActivity (via onCreate)")
                    }.onFailure { Module.err("AppEntryHook redirect failed", it) }
                }
            }
        })
        Module.log("AppEntryHook: target=$targetActivity")
    }

    /** 缓存本包的 launcher Activity 类名（查询一次 PackageManager） */
    @Volatile private var currentLauncher: String? = null
    private fun resolveLauncher(pkg: String) {
        if (currentLauncher != null) return
        runCatching {
            val ctx = ApplicationContextHolder.get() ?: return
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            intent.`package` = pkg
            val ris = ctx.packageManager.queryIntentActivities(intent, 0)
            currentLauncher = ris.firstOrNull { it.activityInfo.packageName == pkg }?.activityInfo?.name
            Module.log("AppEntryHook: resolved launcher=$currentLauncher")
        }
    }
}
