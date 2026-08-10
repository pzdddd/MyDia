package com.pzdd.mydia.module.hook.extras

import android.content.ClipData
import android.content.ClipboardManager
import android.text.TextUtils
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 剪贴板读写禁用。对应 Dia 的 ClipboardDisableHook + mod_ex_misc 的 clipboard。
 *
 * 三个独立开关：
 *  - clipboard_read_disable：hook getPrimaryClip/hasPrimaryClip/getPrimaryClipDescription 返回空/false
 *  - clipboard_write_disable：hook setPrimaryClip/clearPrimaryClip setResult null
 *  - clipboard_keyword_disable + clipboard_add_keyword_disable：只有剪贴板内容含关键字时才禁用
 *
 * SP key：clipboard_read_disable / clipboard_write_disable /
 *        clipboard_keyword_disable / clipboard_add_keyword_disable(关键字，空格分隔)
 */
class ClipboardDisableHook : DiaHook() {

    private val readDis get() = prefs.getBoolean("clipboard_read_disable", false)
    private val writeDis get() = prefs.getBoolean("clipboard_write_disable", false)
    private val keywordDis get() = prefs.getBoolean("clipboard_keyword_disable", false)
    private val keywords get() = prefs.getString("clipboard_add_keyword_disable", "") ?: ""

    private fun matchKeyword(clip: ClipData?): Boolean {
        if (clip == null) return false
        if (keywords.isEmpty()) return true  // 无关键字 = 全禁
        val sb = StringBuilder()
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            item.text?.let { sb.append(it) }
            item.htmlText?.let { sb.append(it) }
            item.uri?.let { sb.append(it) }
        }
        for (kw in keywords.split(" ")) {
            if (kw.isNotEmpty() && sb.contains(kw)) return true
        }
        return false
    }

    override fun install() {
        if (!readDis && !writeDis && !keywordDis) return

        val cm = ClipboardManager::class.java
        // 读：getPrimaryClip
        XposedBridge.hookAllMethods(cm, "getPrimaryClip", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (!readDis && !(keywordDis && matchKeyword(param.result as? ClipData))) return
                param.result = ClipData.newPlainText("FAKE", "")
                Module.log("Clipboard: getPrimaryClip -> empty")
            }
        })
        // 读：hasPrimaryClip -> false
        XposedBridge.hookAllMethods(cm, "hasPrimaryClip", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (readDis) { param.result = false; Module.log("Clipboard: hasPrimaryClip -> false") }
            }
        })
        XposedBridge.hookAllMethods(cm, "getPrimaryClipDescription", object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (readDis) param.result = null
            }
        })
        // 写：setPrimaryClip
        XposedBridge.hookAllMethods(cm, "setPrimaryClip", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (!writeDis && !(keywordDis && matchKeyword(param.args[0] as? ClipData))) return
                param.result = null
                Module.log("Clipboard: setPrimaryClip blocked")
            }
        })
        XposedBridge.hookAllMethods(cm, "clearPrimaryClip", object : MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (writeDis) param.result = null
            }
        })
        Module.log("ClipboardDisable: read=$readDis write=$writeDis keyword=$keywordDis('$keywords')")
    }
}
