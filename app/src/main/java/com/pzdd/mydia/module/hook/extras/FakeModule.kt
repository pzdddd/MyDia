package com.pzdd.mydia.module.hook.extras

import com.pzdd.mydia.module.Module
import com.pzdd.mydia.module.hook.DiaHook

/**
 * 「模拟与伪装」分类协调器。对应 Dia 的 mod_ex_fake.xml 整页。
 *
 * 下辖 7 个子 hook：
 *  - DevicePropsHook  设备属性（Build.* / android_id / device_id）
 *  - FakeTimeHook     系统时间伪造
 *  - TimeZoneHook     时区伪造
 *  - WifiHook         WiFi SSID/BSSID 伪造
 *  - FakeNetworkHook  网络类型伪造（WiFi↔移动网络）
 *  - SettingsHook     Settings.Secure/System 伪装（adb_enabled 等）
 *  - SensorHook       传感器数据伪造（加速度/陀螺仪）
 *
 * 没有 mod_ex 总开关门控（各子 hook 自带开关），这里只做聚合注册。
 * 设备属性子页（mod_ex_device_props）的 prefs key 也由 DevicePropsHook 直接读。
 */
class FakeModule : DiaHook() {
    override fun install() {
        Module.log("FakeModule: registering simulation/camouflage hooks")
        DiaHook.register(
            DevicePropsHook::class.java,
            FakeTimeHook::class.java,
            TimeZoneHook::class.java,
            WifiHook::class.java,
            FakeNetworkHook::class.java,
            SettingsHook::class.java,
            SensorHook::class.java,
        )
    }
}
