package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge

/**
 * 线程名伪装。对应 Dia 的 ThreadHook。
 *
 * 有些 App 检测关键线程名（如 "OkHttp"、"Thread-3" 配合栈回溯），或反过来
 * 用线程名做稳定性自检。本 hook 把当前线程名改为指定值。
 *
 * SP key：thread_name_fake(总开关) / thread_name_fake_value(伪装线程名)
 */
class ThreadHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("thread_name_fake", false)) return
        val fake = (prefs.getString("thread_name_fake_value", "") ?: "").trim()
        if (fake.isEmpty()) {
            Module.log("ThreadHook: no thread name, skip.")
            return
        }

        runCatching {
            XposedBridge.hookAllMethods(
                Thread::class.java,
                "getName",
                XC_MethodReplacement.returnConstant(fake)
            )
        }
        Module.log("ThreadHook ACTIVE (thread name -> $fake).")
    }
}
