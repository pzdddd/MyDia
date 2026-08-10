package com.pzdd.mydia.ui.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局 SP 文件名。存模块总开关 / 日志开关。
 * **必须**和注入侧 `XSharedPreferences("com.pzdd.mydia", "digXposed")` 一致。
 */
const val PREFS_GLOBAL = "digXposed"

/**
 * 写 SP 用的模式。
 *
 * 【重要】不能用 MODE_WORLD_READABLE：Android N(24) 起该模式废弃、Android 13 起
 * `getSharedPreferences` 直接抛 SecurityException（崩溃）。所以 UI 一律用 MODE_PRIVATE
 * 写，**每次写入后手动 chmod 0644**（见 [chmodPref]），让注入侧 XSharedPreferences
 * （直接 open 文件读）能跨进程读取。
 *
 * 注入侧 XSharedPreferences 读取链路：目标 App 进程 open `/data/data/com.pzdd.mydia/
 * shared_prefs/<name>.xml`，要求文件权限 0644。MODE_PRIVATE 生成 0600 → 注入侧读不到
 * → hook 开关默认 false → 功能不生效（本项目之前的根因）。
 */
const val PREFS_MODE = Context.MODE_PRIVATE

/**
 * 把 SP 文件 chmod 成 0644（仅属主可写，所有用户可读）。
 * 通过反射 SharedPreferencesImpl.mFile 拿到文件路径，写入后同步执行。
 */
fun chmodPref(sp: SharedPreferences) {
    runCatching {
        val f = sp.javaClass.getDeclaredField("mFile").apply { isAccessible = true }
        (f.get(sp) as? File)?.let {
            it.setReadable(true, false)   // 所有用户可读
            it.setWritable(true, true)    // 仅属主可写
        }
    }
}

/**
 * 把 shared_prefs 目录下所有模块 SP 文件 chmod 成 0644。
 *
 * 兜底场景：升级前用 MODE_PRIVATE 写入的遗留 0600 文件（当时没做写后 chmod）。
 * 幂等，每次 App 启动调用一次开销可忽略（文件数 = App 数 + 全局，几十个）。
 */
fun migrateWorldReadable(context: Context) {
    runCatching {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".xml")) {
                f.setReadable(true, false)
                f.setWritable(true, true)
            }
        }
    }
}

/**
 * 当前 Compose 子树读写哪份 SP。
 *
 * 由各 Screen 在外层用 `CompositionLocalProvider` 注入：
 *  - 「设置」页注入全局 SP（[PREFS_GLOBAL]）
 *  - 「应用 → 功能列表 / 增强模式 / 分类」注入该 App 自己的 SP（文件名 = 包名）
 *
 * 这样 [PrefItemView] / [rememberBoolPref] 等无需层层传参，自动读写正确的 SP，
 * 且 SP key 与注入侧 [com.pzdd.mydia.module.Module.appPrefs] 完全对齐。
 */
val LocalPrefs = compositionLocalOf<SharedPreferences> {
    error("LocalPrefs not provided: 请在外层用 CompositionLocalProvider 注入 SP")
}

/**
 * 单个 SP 文件的响应式状态持有者。
 *
 * **核心作用**：让「同一 SP + 同一 key」在整个 Composition 中共享同一个 [MutableState]。
 *
 * ### 解决的 bug
 * 原来 [rememberBoolPref] / [rememberStringPref] 每个 Composable 调用点各自 `remember`，
 * 导致 A 处（SwitchRow）改了 `device_props` 的值，B 处（EditTextRow 的 dependency 判断）
 * 读的是**另一个独立的 state**，不会 recompose → 依赖开关的子项（如设备属性的
 * model / brand / android_id 等输入框）**开了开关也不显示**，必须退出页面重进才出现。
 *
 * ### 修复
 * 现在所有读取同一 key 的地方都拿到同一个 wrapper：A 处改 value 会写 SP 并更新内部
 * `mutableStateOf`，所有读这个 wrapper 的 Composable 立即 recompose。
 */
class PrefStateHolder(private val sp: SharedPreferences) {
    private val boolCache = ConcurrentHashMap<String, MutableState<Boolean>>()
    private val strCache = ConcurrentHashMap<String, MutableState<String>>()

    /** 包装一个布尔 key 为响应式状态；写值时同步落盘 SP、chmod 0644、同步 remote。 */
    fun bool(key: String, default: Boolean): MutableState<Boolean> =
        boolCache.computeIfAbsent(key) {
            val inner = mutableStateOf(sp.getBoolean(key, default))
            object : MutableState<Boolean> {
                override var value: Boolean
                    get() = inner.value
                    set(newValue) {
                        // commit 同步落盘（apply 异步，可能 chmod 时文件还没生成）
                        sp.edit().putBoolean(key, newValue).commit()
                        chmodPref(sp)
                        // 同步到 LSPosed remote：注入侧常驻进程立刻读到最新配置
                        com.pzdd.mydia.module.RemotePrefsSync.syncLocal(sp)
                        inner.value = newValue
                    }
                override fun component1(): Boolean = value
                override fun component2(): (Boolean) -> Unit = { value = it }
            }
        }

    /** 包装一个字符串 key 为响应式状态；写值时同步落盘 SP、chmod 0644、同步 remote。 */
    fun str(key: String, default: String): MutableState<String> =
        strCache.computeIfAbsent(key) {
            val inner = mutableStateOf(sp.getString(key, default) ?: default)
            object : MutableState<String> {
                override var value: String
                    get() = inner.value
                    set(newValue) {
                        sp.edit().putString(key, newValue).commit()
                        chmodPref(sp)
                        com.pzdd.mydia.module.RemotePrefsSync.syncLocal(sp)
                        inner.value = newValue
                    }
                override fun component1(): String = value
                override fun component2(): (String) -> Unit = { value = it }
            }
        }
}

/**
 * 进程级 holder 缓存（按 SP 实例）。
 *
 * Android 对同一 name 的 `getSharedPreferences` 始终返回同一实例，
 * 故可按 SP 实例缓存 holder，保证同一 SP 在整个 Composition 树中只有一个 holder，
 * 进而保证「同一 key 共享同一 MutableState」。
 */
private val holderCache = ConcurrentHashMap<SharedPreferences, PrefStateHolder>()

/** 取当前 [LocalPrefs] 对应的共享 holder。 */
@Composable
fun rememberPrefHolder(): PrefStateHolder {
    val sp = LocalPrefs.current
    return remember(sp) { holderCache.getOrPut(sp) { PrefStateHolder(sp) } }
}

/** 全局 SP（digXposed）。给「设置」页 / 首页用。 */
@Composable
fun rememberGlobalSp(): SharedPreferences =
    LocalContext.current.let { ctx -> remember(ctx) { ctx.getSharedPreferences(PREFS_GLOBAL, PREFS_MODE) } }

/** 某 App 的 per-app SP（文件名 = 包名）。给「应用 → 功能列表」用，与注入侧 appPrefs 对齐。 */
@Composable
fun rememberAppSp(pkg: String): SharedPreferences =
    LocalContext.current.let { ctx -> remember(ctx, pkg) { ctx.getSharedPreferences(pkg, PREFS_MODE) } }

/**
 * 用当前 [LocalPrefs] 的 SP 包装一个布尔值为 Compose 状态。
 *
 * 同一 SP + 同一 key 在整个 Composition 中返回**同一个** [MutableState]，
 * 因此在一处改值，所有读该 key 的地方（含 dependency 判断）都会立即 recompose。
 */
@Composable
fun rememberBoolPref(key: String, default: Boolean): MutableState<Boolean> =
    rememberPrefHolder().bool(key, default)

/**
 * 用当前 [LocalPrefs] 的 SP 包装一个字符串为 Compose 状态。
 *
 * 同一 SP + 同一 key 在整个 Composition 中返回**同一个** [MutableState]。
 */
@Composable
fun rememberStringPref(key: String, default: String): MutableState<String> =
    rememberPrefHolder().str(key, default)
