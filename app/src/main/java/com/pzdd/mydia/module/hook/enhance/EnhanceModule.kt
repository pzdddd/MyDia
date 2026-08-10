package com.pzdd.mydia.module.hook.enhance

import android.content.Context
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.ApplicationHook
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XposedBridge

/**
 * 增强模式总入口。对应 Dia 的 mod_ex 总开关 + otherModEx()（MTProtector 加密部分）。
 *
 * 职责：
 *  1. 读 per-app prefs 的 mod_ex 总开关；关了就一个子 hook 都不装
 *  2. 缓存 Application Context（[ApplicationContextHolder]）
 *  3. 按顺序注册所有增强子 hook（对话框/按钮/Activity 三大类）
 *  4. 把 KeyTriggerHook 与 AlertDisableHook 互相关联（按键能 toggle alert）
 *
 * 加新增强功能 = 新建 XxxHook extends [DiaHookEntry] + 这里 register 一行。
 *
 * 完整功能清单（对齐 Dia mod_ex_dialog / mod_ex_btn / mod_ex_activity）：
 *  对话框：AlertCloseExHook(增强取消) / AlertDisableHook(禁用) / DialogCancel(基础取消,已有)
 *  按钮：  HideButtonHook(显示隐藏) / DisableButtonHook(取消禁用) / AutoClickButtonHook(自动点击)
 *  Activity：AppEntryHook(改入口) / DisableActivityHook(禁用) / ActivityForceFinish(按键结束)
 *  贯穿：  KeyTriggerHook(按键触发 toggle)
 */
class EnhanceModule : DiaHook(), ApplicationHook.OnAppReady {

    override fun install() {
        // mod_ex 总开关：关了整个增强模式都不加载（per-app SP）
        if (!prefs.getBoolean("mod_ex", false)) {
            Module.log("EnhanceModule: mod_ex disabled, skip all enhance hooks")
            return
        }
        Module.log("EnhanceModule: mod_ex ON, waiting for Application ready...")
        DiaHook.get(ApplicationHook::class.java)?.addOnAppReady(this)
    }

    override fun onReady(ctx: Context) {
        ApplicationContextHolder.set(ctx)

        // 注册顺序：先装 alert/exit 这类被 KeyTrigger 控制的，再装 KeyTrigger 本身
        val alertDisable = AlertDisableHook().also { registerEntry(it) }
        registerEntry(AlertCloseExHook())
        registerEntry(HideButtonHook())
        registerEntry(DisableButtonHook())
        registerEntry(AutoClickButtonHook())
        registerEntry(AppEntryHook())
        registerEntry(DisableActivityHook())
        // 强制结束 Activity / 禁止退出 也由 KeyTrigger 驱动，装 KeyTrigger
        KeyTriggerHook().also { trigger ->
            trigger.alertDisableHook = alertDisable
            registerEntry(trigger)
        }
        Module.log("EnhanceModule: all enhance hooks registered")
    }

    private fun registerEntry(entry: DiaHookEntry) {
        // 复用 DiaHook 的错误兜底：每个 hook 自己 onReady 时 installImpl
        val ctx = ApplicationContextHolder.get()
        if (ctx == null) {
            Module.log("EnhanceModule: registerEntry ${entry.javaClass.simpleName} skipped (ctx null)")
            return
        }
        Module.log("EnhanceModule: registerEntry ${entry.javaClass.simpleName}")
        entry.onReady(ctx)
    }
}
