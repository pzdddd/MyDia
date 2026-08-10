package com.pzdd.mydia.module.hook.extras

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 传感器数据伪造（与 SensorDisableHook 并存：一个禁一个骗）。
 *
 * 对应 Dia 的 SensorHook（FakeCallSensorEventListenerTask 部分）：伪造传感器读数，
 * 让 App 拿到的加速度/陀螺仪/磁力计数据不是真实值，用于欺骗摇一摇/计步/方向检测。
 *
 * 每个类型一组预设值（可选「静态值」或「缓慢变化」）：
 *  - 加速度计(Sensor.TYPE_ACCELEROMETER=1)：默认模拟静止
 *  - 陀螺仪(Sensor.TYPE_GYROSCOPE=4)：默认模拟静止
 *  - 磁力计(Sensor.TYPE_MAGNETIC_FIELD=2)
 *  - 方向(Sensor.TYPE_ORIENTATION=3)
 *
 * SP key：sensor_fake(总开关) / sensor_fake_accel(逗号分隔 x,y,z，默认 0,0,9.81)
 */
class SensorHook : DiaHook() {

    override fun install() {
        if (!prefs.getBoolean("sensor_fake", false)) return

        val accel = parseVec(prefs.getString("sensor_fake_accel", "") ?: "", floatArrayOf(0f, 0f, 9.81f))

        XposedBridge.hookAllMethods(SensorEventListener::class.java, "onSensorChanged", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val event = param.args.getOrNull(0) as? SensorEvent ?: return
                val type = event.sensor?.type ?: return
                when (type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values[0] = accel[0]
                        event.values[1] = accel[1]
                        event.values[2] = accel[2]
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        event.values[0] = 0f
                        event.values[1] = 0f
                        event.values[2] = 0f
                    }
                }
            }
        })
        Module.log("SensorHook ACTIVE (accel=${accel.joinToString(",")}).")
    }

    private fun parseVec(raw: String, def: FloatArray): FloatArray {
        val parts = raw.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 3) return def
        val v = parts.mapNotNull { it.toFloatOrNull() }
        return if (v.size == 3) floatArrayOf(v[0], v[1], v[2]) else def
    }
}
