package com.pzdd.mydia.module.hook.extras

import de.robv.android.xposed.XC_MethodHook

/**
 * extras 包内部用的标记基类。继承 [XC_MethodHook]，纯粹为统一各 hook 的基类来源。
 *
 * 注意：参数类型请用完整名 `XC_MethodHook.MethodHookParam`（typealias 在 override 匹配
 * Xposed platform type 时会失败），所以本类不提供 Param 别名。
 *
 * 用法：`object : MethodHook() { override fun afterHookedMethod(p: XC_MethodHook.MethodHookParam) }`
 */
abstract class MethodHook : XC_MethodHook()
