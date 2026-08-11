package com.pzdd.mydia.ui.prefs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh

/**
 * 全部配置页的集中注册表。
 *
 * 取代原来的 `res/xml/prefs_*.xml` + `strings.xml`。
 * 每个详情页 ([PrefScreen.items]) 的 [Pref.key] 与注入侧
 * [com.pzdd.mydia.module.Module] 读取的 SP key 完全一致。
 *
 * 入口页（主界面、增强模式目录）由各 Activity 的 Screen composable 用
 * [Pref.Action] 现场构造（因为含导航回调），详情页则在这里静态声明。
 */
object PrefRegistry {

    // ---------- 共享的列表项 ----------

    /** 按键触发手势（对话框/Activity 强制结束共用）。对应 arrays.xml 的 trigger_gestures。 */
    val TRIGGER_GESTURES = listOf(
        "未启用" to "-1",
        "双击音量减" to "0",
        "双击返回" to "1",
        "三击音量减" to "2",
        "三击返回" to "3",
        "双击音量加" to "4",
        "长按返回" to "5",
        "同时按音量+/-" to "6",
    )

    /** 网络类型伪造。 */
    val FAKE_NETWORK = listOf(
        "不伪造" to "0",
        "伪装成 WiFi" to "1",
        "伪装成移动网络" to "2",
    )

    /** UiMode。 */
    val UI_MODE = listOf(
        "跟随系统" to "0",
        "强制深色" to "1",
        "强制浅色" to "2",
    )

    /** 随机文件格式。 */
    val RANDOM_RANGE = listOf(
        "数字串" to "0",
        "UUID" to "1",
        "字母数字混合" to "2",
    )

    // ---------- 增强模式 · 对话框 ----------
    // 对应原 prefs_enhance.xml 的「对话框」分类，hook 在 enhance 包。

    val dialog = PrefScreen(
        key = "dialog",
        title = "对话框",
        icon = Icons.Filled.Refresh,
        items = listOf(
            Pref.Header("对话框取消"),
            Pref.Switch(
                key = "alert_close_ex",
                title = "增强版对话框取消",
                summary = "反射强制改 mCancelable，针对绕过 setCancelable 的 App",
                default = false,
            ),
            Pref.Switch(
                key = "alert",
                title = "禁用对话框",
                summaryOn = "拦截 ViewGroup.addView，按关键字/id 拦截弹层",
                default = false,
            ),
            Pref.Switch(
                key = "alert_auto",
                title = "全自动拦截",
                summary = "无需关键字，自动判断弹层",
                default = true,
                dependency = "alert",
            ),
            Pref.Switch(
                key = "disable_alert_mode",
                title = "全盘扫描模式",
                summaryOn = "扫描所有 ViewGroup（更准但更慢）",
                summaryOff = "单次扫描（更快）",
                default = false,
                dependency = "alert",
            ),
            Pref.EditText(
                key = "alert_keyword",
                title = "关键字",
                summary = "弹层文案含关键字才拦截（空格分隔，正则）",
                dependency = "alert",
            ),
            Pref.EditText(
                key = "alert_id",
                title = "View ID",
                summary = "按 id 拦截（如 com.foo:id/dialog_title）",
                dependency = "alert",
            ),
            Pref.Switch(
                key = "toast",
                title = "拦截时 Toast 提示",
                summary = "命中拦截后弹提示",
                default = false,
                dependency = "alert",
            ),
            Pref.ListChoice(
                key = "alert_enabled",
                title = "按键触发禁用对话框",
                summary = "选一种手势运行时切换拦截开关",
                entries = TRIGGER_GESTURES,
                default = "-1",
                dependency = "alert",
            ),
        ),
    )

    // ---------- 增强模式 · 按钮 ----------

    val button = PrefScreen(
        key = "button",
        title = "按钮",
        icon = Icons.Filled.PlayArrow,
        items = listOf(
            Pref.Switch("hide_btn", "隐藏按钮", summaryOn = "hook setVisibility，GONE/INVISIBLE→VISIBLE", default = false),
            Pref.Switch("dis_btn", "启用按钮", summaryOn = "hook setEnabled/setClickable，false→true", default = false),
            Pref.Header("自动点击按钮"),
            Pref.Switch("click_btn", "自动点击", summaryOn = "hook WindowManager.addView，BFS 扫描后 performClick", default = false),
            Pref.EditText("click_btn_keyword", "按钮文字关键字", summary = "按文字匹配（空格分隔）", dependency = "click_btn"),
            Pref.EditText("click_btn_id", "按钮 ID", summary = "按 id 匹配", dependency = "click_btn"),
            Pref.EditText("click_delay_ms", "延时点击（毫秒）", summary = "addView 后多久开始扫描", default = "0", numeric = true, dependency = "click_btn"),
            Pref.EditText("click_time", "点击次数", summary = "0 表示点一次", default = "0", numeric = true, dependency = "click_btn"),
            Pref.Switch("click_btn_tip", "点击时 Toast", default = false, dependency = "click_btn"),
        ),
    )

    // ---------- 增强模式 · 活动界面 ----------

    val activity = PrefScreen(
        key = "activity",
        title = "活动界面",
        icon = Icons.Filled.List,
        items = listOf(
            Pref.Header("设置应用入口"),
            Pref.Switch("app_entry", "重定向启动 Activity", summaryOn = "把 launcher 重定向到指定 Activity", default = false),
            Pref.EditText("app_activity_select", "目标 Activity 全类名", summary = "如 com.foo.MainActivity", dependency = "app_entry"),
            Pref.Header("禁用指定 Activity"),
            Pref.Switch("disable_activity", "禁用 Activity", summaryOn = "黑名单 Activity 直接 finish", default = false),
            Pref.EditText("disable_activity_select", "Activity 全类名列表", summary = "逗号分隔", dependency = "disable_activity"),
            Pref.Header("强制结束当前 Activity"),
            Pref.Switch("activity_force_finish", "强制结束", summaryOn = "按手势触发 finish", default = false),
            Pref.Switch("exit_auto", "启动即自动结束", summaryOn = "无需按键触发，App 一启动就启用结束逻辑", default = false, dependency = "activity_force_finish"),
            Pref.ListChoice("activity_enabled", "触发手势", entries = TRIGGER_GESTURES, default = "-1"),
        ),
    )

    // ---------- 模拟与伪装 ----------

    val fake = PrefScreen(
        key = "fake",
        title = "模拟与伪装",
        icon = Icons.Filled.Phone,
        summary = "设备属性 / 时间 / 时区 / WiFi / 网络类型",
        items = listOf(
            Pref.Header("设备属性"),
            Pref.Switch("device_props", "启用设备属性模拟", default = false),
            Pref.Switch("device_props_random", "随机生成", summary = "每次启动随机（覆盖下面手填值）", default = false, dependency = "device_props"),
            Pref.EditText("android_id", "android_id", summary = "留空则不改", dependency = "device_props"),
            Pref.EditText("device_id", "device_id（IMEI）", summary = "留空则不改", dependency = "device_props"),
            Pref.EditText("model", "model", dependency = "device_props"),
            Pref.EditText("product", "product", dependency = "device_props"),
            Pref.EditText("board", "board", dependency = "device_props"),
            Pref.EditText("brand", "brand", dependency = "device_props"),
            Pref.EditText("manufacturer", "manufacturer", dependency = "device_props"),
            Pref.EditText("device", "device", dependency = "device_props"),
            Pref.Header("系统时间伪造"),
            Pref.Switch("time", "启用时间伪造", summaryOn = "hook System.currentTimeMillis / Date / URL", default = false),
            Pref.Switch("time_keep", "固定模式", summaryOn = "返回固定值（否则在原值上偏移）", default = false, dependency = "time"),
            Pref.EditText("time_difference", "偏移量（毫秒）", summary = "固定模式关闭时生效，可填负数", numeric = true, dependency = "time"),
            Pref.EditText("time_keep_value", "固定时间值（毫秒）", summary = "固定模式下的绝对时间戳", numeric = true, dependency = "time_keep"),
            Pref.Switch("native_time", "原生层时间", summaryOn = "hook libc gettimeofday（需原生模块，MyDia 仅标记）", default = false, dependency = "time"),
            Pref.Header("时区伪造"),
            Pref.Switch("time_zone", "启用时区伪造", summaryOn = "hook TimeZone.getDefault/getTimeZone", default = false),
            Pref.EditText("time_zone_list", "时区 ID", summary = "如 Asia/Shanghai", dependency = "time_zone"),
            Pref.Header("WiFi 伪造"),
            Pref.Switch("wifi", "启用 WiFi 伪造", summaryOn = "hook WifiInfo.getSSID/getBSSID/getMacAddress", default = false),
            Pref.EditText("wifi_name", "WiFi 名（SSID）", summary = "如 MyHomeWiFi", dependency = "wifi"),
            Pref.EditText("wifi_mac", "MAC 地址（BSSID）", summary = "如 aa:bb:cc:dd:ee:ff", dependency = "wifi"),
            Pref.Header("网络类型伪造"),
            Pref.ListChoice("fake_network", "伪装网络类型", summary = "让 App 以为在 WiFi 或移动网络下", entries = FAKE_NETWORK, default = "0"),
            Pref.Header("系统设置伪装"),
            Pref.Switch("settings_fake", "启用 Settings 伪装", summaryOn = "hook Settings.Secure/System 读取，如 adb_enabled=0", default = false),
            Pref.EditText("settings_list", "key=value 列表", summary = "每行/逗号分隔，如 adb_enabled=0", multiLine = true, dependency = "settings_fake"),
            Pref.Header("传感器伪造"),
            Pref.Switch("sensor_fake", "启用传感器伪造", summaryOn = "hook onSensorChanged，伪造加速度/陀螺仪读数", default = false),
            Pref.EditText("sensor_fake_accel", "加速度值 x,y,z", summary = "逗号分隔，默认 0,0,9.81（静止）", dependency = "sensor_fake"),
        ),
    )

    // ---------- 通知与提示 ----------

    val notify = PrefScreen(
        key = "notify",
        title = "通知与提示",
        icon = Icons.Filled.Notifications,
        summary = "禁用通知 / 禁用 Toast（关键字过滤）",
        items = listOf(
            Pref.Header("禁用通知"),
            Pref.Switch("notify", "启用禁用通知", summaryOn = "拦截 NotificationManager.notify / startForeground", default = false),
            Pref.EditText("notify_keyword", "关键字（空格分隔，正则）", summary = "命中才拦截；空 = 全拦", dependency = "notify"),
            Pref.Switch("notify_tip", "拦截时 Toast 提示", default = false, dependency = "notify"),
            Pref.Header("禁用 Toast"),
            Pref.Switch("toast_disable", "启用禁用 Toast", summaryOn = "hook Toast.show", default = false),
            Pref.EditText("toast_keyword", "关键字（空格分隔，正则）", summary = "命中才拦截；空 = 全拦", dependency = "toast_disable"),
        ),
    )

    // ---------- 反检测 ----------

    val anti = PrefScreen(
        key = "anti",
        title = "反检测",
        icon = Icons.Filled.Lock,
        summary = "隐藏 Xposed/Root/模拟器/多开/VPN，GPS 伪造",
        items = listOf(
            Pref.Header("通用"),
            Pref.Switch("vpn", "隐藏 VPN", summaryOn = "隐藏 tun0/ppp0/TRANSPORT_VPN", default = false),
            Pref.Header("隐藏检测项"),
            Pref.Switch("hide", "隐藏 Root", summaryOn = "拦截 su/busybox/magisk，去 test-keys", default = false),
            Pref.Switch("hide_xposed", "隐藏 Xposed", summaryOn = "过滤 stack trace / loadClass / 包名检测", default = false),
            Pref.Switch("fake_xposed", "拦截 Xposed 类加载", summaryOn = "Class.forName(de.robv.*) 抛 ClassNotFoundException", default = false),
            Pref.Switch("hide_emulator", "隐藏模拟器", summaryOn = "改 Build 字段去 generic/sdk 特征", default = false),
            Pref.Switch("hide_multi_app", "隐藏多开/分身", summaryOn = "抹掉多开容器路径特征", default = false),
            Pref.EditText("hide_multi_app_select", "额外容器包名", summary = "逗号分隔，追加到内置列表", dependency = "hide_multi_app"),
            Pref.Header("反调试"),
            Pref.Switch("debug", "反调试", summaryOn = "isDebuggerConnected → false", default = false),
            Pref.Switch("debug_force", "强制非 debuggable", summaryOn = "isDebuggable 也返回 false", default = false, dependency = "debug"),
            Pref.Header("包名 / 线程 / 栈伪装"),
            Pref.Switch("package_name_fake", "伪装包名", summaryOn = "ActivityThread.currentPackageName 返回指定值", default = false),
            Pref.EditText("package_name_fake_value", "伪装的包名", summary = "如 com.foo.bar", dependency = "package_name_fake"),
            Pref.Switch("thread_name_fake", "伪装线程名", summaryOn = "Thread.getName 返回指定值", default = false),
            Pref.EditText("thread_name_fake_value", "伪装的线程名", dependency = "thread_name_fake"),
            Pref.Switch("stack_filter", "过滤调用栈", summaryOn = "从 getStackTrace 移除指定包名的帧", default = false),
            Pref.EditText("stack_filter_pkg", "要过滤的包名前缀", summary = "空格分隔，如 com.pzdd.mydia", dependency = "stack_filter"),
            Pref.Switch("protect", "模块自身防护", summaryOn = "从 PackageManager 查询结果抹掉模块包", default = false),
            Pref.EditText("protect_pkg", "要隐藏的包名", summary = "留空 = 本模块包名", dependency = "protect"),
            Pref.Header("环境检测记录"),
            Pref.Switch("check_env", "检测记录", summaryOn = "App 探测 root/hook/模拟器/调试器时输出 logcat", default = false),
            Pref.Header("GPS 位置伪造"),
            Pref.Switch("gps", "启用 GPS 伪造", summaryOn = "hook Location.getLatitude/getLongitude", default = false),
            Pref.Switch("gps_open_permission", "权限检测也欺骗", summaryOn = "checkPermission 返回已授权", default = false, dependency = "gps"),
            Pref.EditText("gps_location", "位置（纬度,经度）", summary = "如 31.2304,121.4737（上海）", dependency = "gps"),
        ),
    )

    // ---------- 大杂烩 ----------

    val misc = PrefScreen(
        key = "misc",
        title = "大杂烩",
        icon = Icons.Filled.MoreVert,
        summary = "剪贴板 / 传感器 / 后台隐藏 / so库 / 随机文件 / HTTP代理 / 启动禁网 / UiMode",
        items = listOf(
            Pref.Switch("hide_on_background", "后台隐藏", summaryOn = "从最近任务列表隐藏", default = false),
            Pref.ListChoice("ui_mode", "UiMode", summary = "强制深色/浅色", entries = UI_MODE, default = "0"),
            Pref.Header("剪贴板"),
            Pref.Switch("clipboard_read_disable", "禁用读取剪贴板", default = false),
            Pref.Switch("clipboard_write_disable", "禁用写入剪贴板", default = false),
            Pref.Switch("clipboard_keyword_disable", "仅含关键字时禁用", default = false),
            Pref.EditText("clipboard_add_keyword_disable", "关键字列表", summary = "空格分隔，命中任一则禁用", dependency = "clipboard_keyword_disable"),
            Pref.Header("传感器"),
            Pref.Switch("sensor_disable", "禁用传感器", default = false),
            Pref.Switch("accelerometer", "加速度计", default = false, dependency = "sensor_disable"),
            Pref.Switch("gyroscope", "陀螺仪", default = false, dependency = "sensor_disable"),
            Pref.Header("禁止退出"),
            Pref.Switch("exit", "禁止退出 App", summaryOn = "防止不适配/检测异常就自动退出（拦 finish / System.exit / 退后台）", default = false),
            Pref.ListChoice("exit_enabled", "按键触发切换", summary = "运行时按手势开关「禁止退出」", entries = TRIGGER_GESTURES, default = "-1", dependency = "exit"),
            Pref.Header("启动禁网"),
            Pref.Switch("network", "启动时短暂禁网", summaryOn = "启动后一段时间阻断网络", default = false),
            Pref.EditText("network_time", "禁网时长（毫秒）", summary = "默认 5000", default = "5000", numeric = true, dependency = "network"),
            Pref.Header("禁用 so 库"),
            Pref.Switch("disable_so_library", "禁用指定 so 加载", summaryOn = "拦截 loadLibrary", default = false),
            Pref.EditText("disable_so_library_list", "so 名列表", summary = "空格分隔，如 msaoaidsec secneo", dependency = "disable_so_library"),
            Pref.Header("随机设备 id 文件"),
            Pref.Switch("random_file_content", "随机文件内容", summaryOn = "读取指定文件时返回随机内容", default = false),
            Pref.ListChoice("select_random_range", "随机格式", entries = RANDOM_RANGE, default = "0", dependency = "random_file_content"),
            Pref.EditText("input_random_file", "目标文件路径", summary = "空格分隔绝对路径"),
            Pref.Header("HTTP 代理"),
            Pref.Switch("http_proxy", "启用 HTTP 代理", default = false),
            Pref.EditText("http_proxy_host", "HOST", dependency = "http_proxy"),
            Pref.EditText("http_proxy_port", "PORT", numeric = true, dependency = "http_proxy"),
            Pref.Header("强制代理（抓包）"),
            Pref.Switch("force_proxy", "强制走代理", summaryOn = "所有 HTTP(S) 流量强制走指定代理", default = false),
            Pref.EditText("force_proxy_host", "代理 HOST", dependency = "force_proxy"),
            Pref.EditText("force_proxy_port", "代理 PORT", numeric = true, default = "8080", dependency = "force_proxy"),
            Pref.Header("摇一摇"),
            Pref.Switch("shake", "摇一摇检测", summaryOn = "检测摇动并输出日志", default = false),
            Pref.EditText("shake_threshold", "触发阈值", summary = "默认 12.0", dependency = "shake"),
            Pref.Header("窗口操作"),
            Pref.Switch("window_monitor", "窗口监控", summaryOn = "记录 addView/removeView", default = false),
            Pref.EditText("window_block", "阻止的 view 类名", summary = "空格分隔，命中则阻止添加", dependency = "window_monitor"),
            Pref.Header("隐藏文件"),
            Pref.Switch("file_hide", "隐藏敏感文件", summaryOn = "su/xposed 相关路径伪装为不存在", default = false),
            Pref.EditText("file_hide_list", "额外路径关键字", summary = "空格分隔，追加到内置列表", dependency = "file_hide"),
            Pref.Header("命令执行"),
            Pref.Switch("runtime_hook", "exec 监控", summaryOn = "记录并伪装 su/which 输出", default = false),
            Pref.EditText("runtime_hide_cmds", "伪装命令前缀", summary = "空格分隔，默认 su which busybox", dependency = "runtime_hook"),
            Pref.EditText("runtime_fake_output", "伪装输出内容", summary = "命中命令的 stdout 替换成此内容；留空 = 返回空（命令像是没装）", dependency = "runtime_hook"),
            Pref.Header("JSON 监控"),
            Pref.Switch("json_monitor", "JSON 序列化监控", summaryOn = "记录 Gson/fastjson 序列化内容", default = false),
            Pref.Header("HTTP 重写引擎"),
            Pref.Switch("http_rewrite", "启用 HTTP 重写", summaryOn = "对明文 HTTP 请求/响应做字符串替换", default = false),
            Pref.EditText("http_rewrite_rules", "重写规则（JSON）", summary = "如 [{\"host\":\"api.xxx.com\",\"req\":{\"match\":\"旧串\",\"replace\":\"新串\"}}]", multiLine = true, dependency = "http_rewrite"),
            Pref.Header("原生层"),
            Pref.Switch("native_hook", "原生 hook", summaryOn = "libmydia_hook.so：时间伪造/exit 拦截/文件打点（需配合 native_time）", default = false),
        ),
    )

    // ---------- 高级功能 ----------

    val advanced = PrefScreen(
        key = "advanced",
        title = "高级功能",
        icon = Icons.Filled.Star,
        summary = "信任用户证书 / 签名伪造 / 方法置空",
        items = listOf(
            Pref.Switch("trust_user_certs", "信任用户证书", summaryOn = "让 App 信任用户安装的 CA（抓 HTTPS）", default = false),
            Pref.Header("签名伪造"),
            Pref.Switch("app_signatures_fake", "启用签名伪造", summaryOn = "绕过 App 签名自检", default = false),
            Pref.EditText("app_signatures_select", "签名配置", summary = "每行「包名=hex签名」", multiLine = true, dependency = "app_signatures_fake"),
            Pref.Header("应用版本伪装"),
            Pref.Switch("app_version_fake", "启用版本伪装", summaryOn = "伪造本 App 的 versionCode / versionName，绕过强制升级检测", default = false),
            Pref.EditText(
                "version_code",
                "内部版本号 (versionCode)",
                summary = "数字，用于版本比较。留空则不改",
                numeric = true,
                dependency = "app_version_fake",
            ),
            Pref.EditText(
                "version_name",
                "外部版本号 (versionName)",
                summary = "任意字符串，用户可见。如 1.2.3。留空则不改",
                dependency = "app_version_fake",
            ),
            Pref.Header("方法置空"),
            Pref.Switch("method", "启用方法置空", summaryOn = "让指定方法不执行原逻辑", default = false),
            Pref.EditText("method_empty", "方法签名列表", summary = "每行一个，如 com.foo.Bar.init()V", multiLine = true, dependency = "method"),
            Pref.Header("WebView 注入"),
            Pref.Switch("webview_hook", "WebView 监控", summaryOn = "记录 loadUrl / 可选重定向 / JS 注入", default = false),
            Pref.EditText("webview_redirect", "重定向 URL", summary = "空 = 不重定向", dependency = "webview_hook"),
            Pref.EditText("webview_js", "注入 JS", summary = "onPageFinished 后执行", multiLine = true, dependency = "webview_hook"),
        ),
    )

    // ---------- 开发者 ----------

    val dev = PrefScreen(
        key = "dev",
        title = "开发者",
        icon = Icons.Filled.Edit,
        summary = "Activity/Fragment 名提示 / 清数据 / 等待调试 / Intent/HTTP/SQL 监控",
        items = listOf(
            Pref.Header("调试辅助"),
            Pref.Switch("activity_show_name", "Activity 名提示", summaryOn = "onResume 时 Toast 类名", default = false),
            Pref.Switch("fragment_show_name", "Fragment 名提示", summaryOn = "onCreate 时 Toast 类名", default = false),
            Pref.Switch("clear_data_onstart", "启动清数据", summaryOn = "每次启动清空 App 数据", default = false),
            Pref.Switch("wait_for_debuggable", "等待调试器", summaryOn = "启动阻塞等调试器 attach", default = false),
            Pref.Switch("app_debuggable_mode", "强制可调试", summaryOn = "让非 debuggable 的 App 也可调试（需 LSPosed 作用域勾选）", default = false, dependency = "wait_for_debuggable"),
            Pref.Header("Intent 监控"),
            Pref.Switch("monitor_intent_switch", "启用 Intent 监控", summaryOn = "logcat TAG=MyDia 输出 startActivity", default = false),
            Pref.Header("HTTP 监控"),
            Pref.Switch("monitor_http_switch", "启用 HTTP/Socket 监控", summaryOn = "logcat 输出 connect/openConnection/明文抓包", default = false),
            Pref.Switch("monitor_http_capture", "抓取明文内容", summaryOn = "包装 Socket 流，抓 HTTP 明文", default = true, dependency = "monitor_http_switch"),
            Pref.Header("SQL 监控"),
            Pref.Switch("sql", "启用 SQL 监控", summaryOn = "logcat 输出 execSQL/query/insert/delete/update", default = false),
            Pref.Switch("sql_detail", "详细模式", summaryOn = "hook 更多 SQLiteDatabase 方法", default = false, dependency = "sql"),
            Pref.Switch("sql_hook_native", "原生层 SQL", summaryOn = "hook libc 层 SQLite（需原生模块，当前仅标记）", default = false, dependency = "sql"),
            Pref.Header("方法栈打印"),
            Pref.Switch("print_stack", "方法调用栈打印", summaryOn = "指定方法每次调用打印堆栈", default = false),
            Pref.EditText("print_stack_list", "目标（类名=方法名）", summary = "每行一个；只写类名 = hook 全部方法", multiLine = true, dependency = "print_stack"),
            Pref.Header("dex 工具"),
            Pref.Switch("hide_dex", "隐藏 dex", summaryOn = "从 DexPathList 移除指定 dex", default = false),
            Pref.EditText("hide_dex_list", "dex 路径关键字", summary = "空格分隔", dependency = "hide_dex"),
            Pref.Switch("dex_inject", "注入 dex", summaryOn = "注入指定 dex 到目标 ClassLoader", default = false),
            Pref.EditText("dex_inject_path", "dex 文件绝对路径", dependency = "dex_inject"),
            Pref.Switch("class_loader_monitor", "类加载监控", summaryOn = "记录 loadClass / 隐藏类", default = false),
            Pref.EditText("class_loader_hide", "隐藏类名前缀", summary = "空格分隔，命中抛 CNFE", dependency = "class_loader_monitor"),
            Pref.Header("注入提示"),
            Pref.Switch("inject_tips", "注入成功提示", summaryOn = "注入后弹 Toast", default = false),
            Pref.EditText("inject_tips_text", "提示文案", summary = "默认：已注入 MyDia", dependency = "inject_tips"),
            Pref.Header("方法追踪"),
            Pref.Switch("trace", "方法调用追踪", summaryOn = "记录入参/出参/耗时", default = false),
            Pref.EditText("trace_list", "目标（类名=方法名）", summary = "每行一个；只写类名 = hook 全部方法", multiLine = true, dependency = "trace"),
        ),
    )

    /** 基础全局对话框取消（per-app）。功能列表页的顶层项。 */
    val basicDialog = PrefScreen(
        key = "basic_dialog",
        title = "基础全局对话框取消",
        icon = Icons.Filled.Refresh,
        items = listOf(
            Pref.Switch(
                "global_alert_close",
                "全局对话框取消",
                summary = "hook Dialog.show，反射强制改 mCancelable",
                default = true,
            ),
            Pref.Switch(
                "disable_exit",
                "禁止退出 App",
                summary = "拦截 finish / System.exit",
                default = false,
            ),
            Pref.Switch(
                "disable_toast",
                "禁用 Toast",
                default = false,
            ),
        ),
    )

    /** 增强模式下的全部 9 个分类页（按显示顺序）。 */
    val enhanceCategories: List<PrefScreen> = listOf(
        dialog, button, activity,
        fake, notify, anti, misc, advanced, dev,
    )

    /** 按 key 查找详情页（含基础对话框页）。 */
    fun byKey(key: String): PrefScreen? =
        (enhanceCategories + basicDialog).firstOrNull { it.key == key }
}
