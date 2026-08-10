package com.pzdd.mydia.module.hook.extras

import android.hardware.Sensor
import android.hardware.SensorManager
import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * 禁用传感器。对应 Dia 的 SensorDisableHook（smali 反编译，本实现等价重写）。
 *
 * 两个细分开关（对齐 Dia 的 accelerometer / gyroscope）：
 *  - accelerometer=true：从传感器列表滤掉加速度计相关（TYPE_ACCELEROMETER=1, GRAVITY=9,
 *    LINEAR_ACCELERATION=10, SIGNIFICANT_MOTION=17 不在此, ACCELEROMETER_UNCALIBRATED=35,
 *    LOW_LATENCY_OFFBODY_DETECT=38, HEADTRACKER=40）
 *  - gyroscope=true：滤掉陀螺仪相关（TYPE_GYROSCOPE=4, GYROSCOPE_UNCALIBRATED=16,
 *    HEART_RATE? no, SENSOR_TYPE=39, HEADTRACKER=41）
 *
 * hook SensorManager.getSensorList / getDynamicSensorList，过滤返回列表。
 *
 * SP key：sensor_disable(总开关) / accelerometer / gyroscope
 */
class SensorDisableHook : DiaHook() {

    private val accelDis get() = prefs.getBoolean("accelerometer", false)
    private val gyroDis get() = prefs.getBoolean("gyroscope", false)

    /** 加速度计类 type（Dia smali 里的 1,10,35,38,40） */
    private val accelTypes = setOf(1, 10, 35, 38, 40)
    /** 陀螺仪类 type（Dia smali 里的 4,16,39,41） */
    private val gyroTypes = setOf(4, 16, 39, 41)

    override fun install() {
        if (!prefs.getBoolean("sensor_disable", false)) return

        val cb = object : MethodHook() {
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val list = param.result as? List<*> ?: return
                val filtered = list.filter { s ->
                    val type = (s as? Sensor)?.type ?: -1
                    !(accelDis && accelTypes.contains(type)) && !(gyroDis && gyroTypes.contains(type))
                }
                param.result = filtered
            }
        }
        XposedBridge.hookAllMethods(SensorManager::class.java, "getSensorList", cb)
        XposedBridge.hookAllMethods(SensorManager::class.java, "getDynamicSensorList", cb)
        Module.log("SensorDisable: installed accel=$accelDis gyro=$gyroDis")
    }
}
