package com.pzdd.mydia.module.hook.extras

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 摇一摇触发。对应 Dia 的 ShakeHook（HookSensorEventListener）。
 *
 * 通过 hook SensorManager.registerListener，在真实加速度事件基础上叠加一个摇动检测：
 * 加速度变化超过阈值（默认 12 m/s²）即触发一次「摇一摇」回调。App 的摇一摇检测
 * 一般看线性加速度，这里在 hook 层模拟同样的判定逻辑，供 KeyTriggerHook 等联动。
 *
 * 本实现聚焦「记录 + 触发通知」：
 *  - 记录 registerListener/unregisterListener 的传感器类型（logcat）
 *  - 检测到摇动时输出日志（供联动方消费）
 *
 * SP key：shake(总开关) / shake_threshold(阈值，默认 12.0)
 */
class ShakeHook : DiaHook() {

    private var threshold = 12.0f
    private var lastShakeTime = 0L

    override fun install() {
        if (!prefs.getBoolean("shake", false)) return
        threshold = (prefs.getString("shake_threshold", "") ?: "")
            .toFloatOrNull() ?: 12.0f

        // 监听 registerListener：App 注册传感器时打点
        runCatching {
            XposedBridge.hookAllMethods(SensorManager::class.java, "registerListener", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val listener = param.args.getOrNull(0)
                    val sensor = param.args.getOrNull(1) as? Sensor
                    Module.log("ShakeHook: registerListener ${sensor?.type ?: "?"} ok=${param.result}")
                    // 传感器类型 1 = 加速度计，注册到加速度计时启动摇动检测
                    if (sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                        // 注入一个叠加的传感器事件监听（简化：只打点）
                        Module.log("ShakeHook: accelerometer listener registered")
                    }
                }
            })
        }

        // 监听 onSensorChanged：真实加速度事件流入时叠加摇动判定
        runCatching {
            XposedBridge.hookAllMethods(SensorEventListener::class.java, "onSensorChanged", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args.getOrNull(0) as? android.hardware.SensorEvent ?: return
                    if (event.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
                    val x = event.values.getOrNull(0) ?: return
                    val y = event.values.getOrNull(1) ?: return
                    val z = event.values.getOrNull(2) ?: return
                    val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                    val now = System.currentTimeMillis()
                    if (magnitude > threshold && now - lastShakeTime > 800) {
                        lastShakeTime = now
                        Module.log("ShakeHook: SHAKE detected (mag=${"%.1f".format(magnitude)})")
                    }
                }
            })
        }

        Module.log("ShakeHook ACTIVE (threshold=$threshold).")
    }
}
