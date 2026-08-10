package com.pzdd.mydia.module.hook.enhance

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.pzdd.mydia.module.Module
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * View 树扫描 + 点击工具。对应 Dia 的 com.mhook.dialog.tool.common.ViewHelper。
 *
 * 增强模式多个功能都依赖它：
 *  - 自动点击按钮：按关键字/id 找到按钮 → performClick()
 *  - 禁用对话框：按关键字/id 判断弹出的 view 是否目标 → 拦截 addView
 *
 * 所有 view 操作都 post 到主线程（view 操作必须主线程）。
 */
object ViewScanner {

    val mainHandler = Handler(Looper.getMainLooper())

    /** view 可点击且自身和所有祖先都可见 */
    fun isViewClickableAndVisible(v: View?): Boolean {
        if (v == null || !v.isClickable) return false
        var cur: View? = v
        while (cur != null) {
            if (cur.visibility != View.VISIBLE) return false
            cur = cur.parent as? View
        }
        return true
    }

    /**
     * 在 [root] 子树里找含任一 [keywords] 文本的 TextView（正则匹配），命中返回 true。
     * 对应 Dia ViewHelper.m12218。
     */
    fun containsKeyword(root: View?, keywords: Array<String>): Boolean {
        if (root == null || keywords.isEmpty()) return false
        val queue = ArrayDeque<View>().apply { addLast(root) }
        while (queue.isNotEmpty()) {
            val v = queue.pollLast()
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) queue.addLast(v.getChildAt(i))
            }
            if (v is TextView) {
                val text = v.text?.toString() ?: ""
                for (kw in keywords) {
                    if (matches(text, kw)) {
                        Module.log("ViewScanner find: $text (kw=$kw)")
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 在 [root] 子树里按 [ids] 找 view（资源 id 名），命中返回 true。
     * 对应 Dia ViewHelper.m12217。
     */
    fun containsId(root: View?, ids: Array<String>): Boolean {
        if (root == null || ids.isEmpty()) return false
        val ctx = root.context
        val pkg = ctx.packageName
        val res = ctx.applicationContext.resources
        for (idName in ids) {
            if (TextUtils.isEmpty(idName)) continue
            val id = res.getIdentifier(idName, "id", pkg)
            if (id != 0 && root.findViewById<View>(id) != null) return true
        }
        return false
    }

    /**
     * 在 [root] 子树里找匹配的 TextView 并逐个点击。
     * 对应 Dia ViewHelper.m12221。返回点击的个数。
     */
    fun clickByKeyword(root: View?, keywords: Array<String>, delayMs: Long = 0): Int {
        if (root == null || keywords.isEmpty()) return 0
        val queue = ArrayDeque<View>().apply { addLast(root) }
        var clicked = 0
        while (queue.isNotEmpty()) {
            val v = queue.pollLast()
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) queue.addLast(v.getChildAt(i))
            }
            if (v is TextView && v.isClickable) {
                val text = v.text?.toString() ?: ""
                for (kw in keywords) {
                    if (matches(text, kw) && isViewClickableAndVisible(v)) {
                        val ref = WeakReference(v)
                        mainHandler.postDelayed({
                            ref.get()?.let {
                                Module.log("ViewScanner click: $text")
                                it.performClick()
                            }
                        }, delayMs.coerceAtLeast(0))
                        clicked++
                        break
                    }
                }
            }
        }
        return clicked
    }

    /** 在 [root] 子树里按 [ids] 找 view 并点击。返回点击个数。 */
    fun clickById(root: View?, ids: Array<String>, delayMs: Long = 0): Int {
        if (root == null || ids.isEmpty()) return 0
        val ctx = root.context
        val res = ctx.applicationContext.resources
        val pkg = ctx.packageName
        var clicked = 0
        for (idName in ids) {
            if (TextUtils.isEmpty(idName)) continue
            val id = res.getIdentifier(idName, "id", pkg)
            if (id == 0) continue
            val target = root.findViewById<View>(id) ?: continue
            if (isViewClickableAndVisible(target)) {
                val ref = WeakReference(target)
                mainHandler.postDelayed({
                    ref.get()?.performClick()
                    Module.log("ViewScanner click id: $idName")
                }, delayMs.coerceAtLeast(0))
                clicked++
            }
        }
        return clicked
    }

    /** 正则匹配，容错（非法正则退化为字面包含） */
    private fun matches(text: String, pattern: String): Boolean = try {
        Pattern.compile(pattern).matcher(text).find()
    } catch (_: PatternSyntaxException) {
        text.contains(pattern)
    }

    /** 把空格分隔的字符串切成数组（Dia 用 TextUtils.split） */
    fun splitKeywords(s: String?): Array<String> =
        if (s.isNullOrBlank()) emptyArray() else TextUtils.split(s, " ")
}
