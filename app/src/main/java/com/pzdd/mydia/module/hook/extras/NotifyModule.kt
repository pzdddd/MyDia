package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 「通知与提示」分类协调器。对应 Dia 的 mod_ex_notify_and_tips.xml 整页。
 *
 * 下辖：
 *  - NotificationHook  禁用通知（带关键字过滤）
 *  - ToastDisableHook  禁用 Toast（已有，在 hook 包；带关键字）
 *
 * ToastDisableHook 复用已有的（com.pzdd.mydia.module.hook.ToastDisableHook），
 * 它读 toast_disable / toast_keyword 两个 key（对齐 Dia mod_ex_notify_and_tips 的 ecei_disable_toast）。
 *
 * 这里只注册 NotificationHook；ToastDisableHook 已在 Module 主注册列表里。
 */
class NotifyModule : DiaHook() {
    override fun install() {
        Module.log("NotifyModule: registering notification/tips hooks")
        DiaHook.register(NotificationHook::class.java)
    }
}
