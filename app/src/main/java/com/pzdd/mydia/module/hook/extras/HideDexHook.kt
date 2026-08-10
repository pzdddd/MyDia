package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import com.pzdd.mydia.module.hook.MultiDexHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File

/**
 * 隐藏 dex。对应 Dia 的 HideDexHook。
 *
 * 有些 App 通过扫描 ClassLoader 的 dex 路径（DexPathList.dexElements）判断
 * 有没有被注入额外的 dex（如模块注入了 frida / 自研 dex）。
 * 本 hook 从 DexPathList 里过滤掉命中黑名单的 dex 文件。
 *
 * 实现：hook `dalvik.system.DexPathList` 构造，遍历 dexElements，
 * 移除 dex 文件路径含指定关键字的元素。
 *
 * SP key：hide_dex(总开关) / hide_dex_list(要隐藏的 dex 路径关键字，空格分隔)
 */
class HideDexHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("hide_dex", false)) return
        val keywords = (prefs.getString("hide_dex_list", "") ?: "")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (keywords.isEmpty()) {
            Module.log("HideDexHook: no keywords, skip.")
            return
        }

        // 等主 ClassLoader 就绪后 hook DexPathList（它持有 dexElements）
        MultiDexHook.addObserver(object : MultiDexHook.Observer {
            override fun onClassLoader(cl: ClassLoader) {
                runCatching {
                    val dpl = XposedHelpers.findClass("dalvik.system.DexPathList", cl)
                    XposedBridge.hookAllConstructors(dpl, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val elements = XposedHelpers.getObjectField(param.thisObject, "dexElements")
                                val arr = elements as? Array<*> ?: return
                                val kept = arr.filter { el ->
                                    val path = XposedHelpers.getObjectField(el, "path") as? String ?: return@filter true
                                    keywords.none { path.contains(it) }
                                }
                                XposedHelpers.setObjectField(param.thisObject, "dexElements", kept.toTypedArray())
                            } catch (_: Throwable) {
                            }
                        }
                    })
                }
            }
        })

        Module.log("HideDexHook ACTIVE (hide=${keywords}).")
    }
}
