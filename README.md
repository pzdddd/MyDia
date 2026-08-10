# MyDia — Dia 复刻骨架工程（可直接编译 ✓）

这是按 `Dia_23.11.apk` 逆向分析结果重写的 **最小可运行 Xposed/LSPosed 模块骨架**。
对应分析文档：`../dia_analysis/docs/`（尤其是 `06_复刻路线图.md`、`07_骨架工程示例.md`）。

> **已在本机验证可编译**：`./gradlew assembleDebug` 产出 `MyDia-debug.apk`（约 22MB，含 Compose runtime），
> 内含完整 Xposed 模块声明 + **libxposed API 102 新入口**（LSPosed 推荐）。
>
> **闪退诊断**：内置 [CrashCatcher](app/src/main/java/com/pzdd/mydia/CrashCatcher.kt)，
> App 崩溃时自动把栈写入 `files/crash.log`，**下次启动弹窗显示崩溃栈**，无 logcat 环境也能定位。
>
> **UI 已全面重构为 Jetpack Compose Material3**：废弃 XML Preference，配置全部用 Kotlin（`PrefRegistry.kt`）声明，
> Material You 动态色彩（默认关闭，用纯 **MIUI 配色**）。**底部 3 Tab（首页/应用/设置）+ per-app 配置模型**：每个 App 独立开关与功能配置。
>
> **架构升级**：所有功能配置从全局 SP 迁移到 **per-app SP**（文件名=包名），
> 与注入侧 `Module.appPrefs` 对齐，实现严格的 per-app 隔离。

## 已实现的功能（P0 地基 + P1 灵魂功能 + P2 高阶三大引擎 + P3 增强模式六大分类 + Compose Material3 UI）

### 基础（P0/P1）

| 功能 | 文件 | 说明 |
|---|---|---|
| **Xposed 入口** | `module/LibXposedEntry.kt` | **libxposed API 102**（纯新 API，唯一入口），`META-INF/xposed/java_init.list` 注册，LSPosed 识别 `xposedapi=102` |
| 单例/配置/日志 | `module/Module.kt` | 读 world-readable SP，分发到各 Hook |
| **Hook 框架基类** | `module/hook/DiaHook.kt` | 插件式注册（照搬 Dia 的 `dialog.box.hook.DiaHook`） |
| App 就绪入口 | `module/hook/ApplicationHook.kt` | hook `Application.attach`，解决「等 Context」时序 |
| **对话框取消** | `module/hook/DialogCancelHook.kt` | Dia 灵魂功能，几十行 |
| 禁止退出 | `module/hook/DisableExitHook.kt` | 演示「禁用某方法」模板 |
| 屏蔽 Toast | `module/hook/ToastDisableHook.kt` | 同上 |
| **Shell 监控** | `module/hook/ShellMonitorHook.kt` + `monitor/ShellMonitorReceiver.kt` | Dia「监控类」范式：Hook 拦截 → 广播回传 |
| 配置面板 | `ui/MainActivity.kt` + `res/xml/prefs.xml` | PreferenceFragment，写 `digXposed` SP |

### 高阶三大引擎（P2）

#### ① 方法改写引擎（Dia 最硬核）— `module/rewrite/` + `module/hook/MethodRewriteHook.kt`

| 文件 | 作用 |
|---|---|
| `rewrite/Rule.kt` `rewrite/RuleGroup.kt` `rewrite/Rewrite.kt` | 规则数据模型（对齐 Dia `dialog.box.expand.rewrite.*`），Gson 序列化 |
| `rewrite/RuleGroupDataStore.kt` | 规则库持久化（读 SP 里的 `method_rewrite_mod_list` JSON） |
| `rewrite/SmaliSignatureConverter.kt` | smali 类型签名 → Java Class（`I`→int, `Ljava/lang/String;`→String, `[B`→byte[]） |
| `hook/MethodRewriteHook.kt` | 引擎本体：读规则→反射定位方法→before 改参/after 改返回+日志；支持多 dex 重试 |
| `hook/MultiDexHook.kt` | 监听新 dex/ClassLoader 加载（DexClassLoader、LoadedApk），让迟到的类也能补 hook |

**规则示例**（写到 SP key=`method_rewrite_mod_list`）：
```json
[{"id":"g1","name":"去广告","enabled":true,"priority":10,
  "rules":[{"id":"r1","className":"com.foo.Bar","methodName":"isVip",
   "signature":"()Z","enabled":true,
   "rewrites":[{"index":-1,"type":2,"replace":"true","match":"\u0001NAN\u0002"}]}]}]
```
- `index=-1` 改返回值；`type=2` 是 BOOLEAN；`match` 用 NAN 哨兵表示「无条件替换」
- 字符串改写支持正则/大小写/整体匹配；bytes 支持十六进制 find&replace（见 `Rewrite.apply`）

#### ② Frida 注入 — `module/hook/FridaHook.kt` + `assets/frida-gadget/`

- 在目标 App 的 `Application.attach` 后 `System.loadLibrary("frida-gadget")`
- gadget.so + config 从 MyDia assets 释放到目标 App 的 `filesDir` 再 load（不需改目标 App）
- 进程过滤：`frida_listen`（监听模式）/ `select_active_process`（按进程集合）
- **前置**：自行把 `libfrida-gadget.so` 放到 `app/src/main/assets/frida-gadget/`（体积大不随工程分发）；不放会打清晰日志提示
- 配置文件 `frida-gadget.config` 已默认提供（`shared` 模式，socket 走 `/data/local/tmp/mydia-frida.sock`）

#### ④ 增强模式（对话框/按钮/Activity 改写）— `module/hook/enhance/`

这是 Dia 的 mod_ex 模块（对话框/按钮/活动界面三大类），用户口中的「增强模式」。统一由 [EnhanceModule] 门控（`mod_ex` 总开关），下设 8 个子 hook：

**对话框相关**（对应 Dia `mod_ex_dialog`）

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| 增强版对话框取消 | `AlertCloseExHook` | `alert_close_ex` | hook `Dialog.show` 后反射改 `mCancelable` 字段，针对绕过 `setCancelable` 的 App |
| 禁用对话框 | `AlertDisableHook` | `alert` + `ViewGroupHook` | hook `ViewGroup.addView`，按关键字/id 拦截弹层；支持全检/单检模式 |

**按钮相关**（对应 Dia `mod_ex_btn`）

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| 显示隐藏按钮 | `HideButtonHook` | `hide_btn` | hook `View.setVisibility`，把 GONE/INVISIBLE 改回 VISIBLE |
| 取消按钮禁用 | `DisableButtonHook` | `dis_btn` | hook `View.setEnabled`/`setClickable`，把 false 改回 true |
| 自动点击按钮 | `AutoClickButtonHook` + `ViewScanner` | `click_btn` + `ClickBtnHook` + `ViewHelper` | hook `WindowManager.addView`，扫描新 view 按关键字/id 找按钮 `performClick` |

**活动界面**（对应 Dia `mod_ex_activity`）

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| 设置应用入口 | `AppEntryHook` | `app_entry` + `AppEntryHook` | hook `Instrumentation.newActivity`/`Activity.onCreate`，把 launcher 重定向到指定 Activity |
| 禁用指定 Activity | `DisableActivityHook` | `disable_activity` + `DisableActivityHook` | hook `onCreate`/`startActivity`/`startActivityForResult`，黑名单命中即 finish/拦截 |
| 强制结束 Activity | `KeyTriggerHook`（activity 分支） | `activity` + `KeyEventHook` | 按组合键运行时 finish 当前 Activity |

**贯穿：按键触发**（对应 Dia `KeyEventHook`）— `KeyTriggerHook`
禁用对话框/禁止退出/结束 Activity 都支持【运行时按键 toggle】，不用回 MyDia 改配置。7 种手势（对齐 Dia `condition_value`）：

| id | 手势 | id | 手势 |
|---|---|---|---|
| 0 | 双击音量减 | 4 | 双击音量加 |
| 1 | 双击返回键 | 5 | 长按返回键 |
| 2 | 三击音量减 | 6 | 同时按音量加减 |
| 3 | 三击返回键 | | |

在 `alert_enabled` / `exit_enabled` / `activity_enabled` 里存手势 id（`-1` = 不绑定）。

> Dia 原版这些 hook 的核心逻辑在 MTProtector 加密的 `otherModEx()` 里，jadx 无法静态恢复。本实现基于 Dia 的功能描述（strings.xml）、prefs 结构、以及扒到的非加密类（`ClickBtnHook`/`ViewGroupHook`/`AppEntryHook`/`KeyEventHook`/`ViewHelper`）用 Kotlin 干净重写，行为对齐。

**UI**：主界面「高级」分类里勾「启用增强模式」总开关 → 点「增强模式」进入 [EnhanceActivity]（`res/xml/prefs_enhance.xml`）配置三大类细节。

#### ⑤ 算法监控（MD5/AES/HMAC/Base64）— `algorithm/` + `monitor/AlgorithmMonitorReceiver.kt`

| 文件 | 作用 |
|---|---|
| `algorithm/ObjectInfo.kt` | 单次加解密调用的状态记录（累计输入、输出、key、iv、栈） |
| `algorithm/AlgorithmHookManager.kt` | 装钩子（MessageDigest/Mac/Cipher/Base64）+ 广播回传 |
| `monitor/AlgorithmMonitorReceiver.kt` | App 端收广播，写内存缓冲 + cache 文件 |
| `ui/AlgorithmLogActivity.kt` | 日志展示页（每秒刷新） |
| `hook/AlgorithmMonitorHook.kt` | 触发器：在 ApplicationReady 后启动监控 |

**数据流**：目标 App 调用 `MessageDigest.digest()` → 注入侧 hook 累计输入、抓输出 → 显式广播（component 指定）回 MyDia 进程 → Receiver 写 `MonitorLogStore` → `AlgorithmLogActivity` 展示 in/out/key/iv/stack。

> 对应 Dia 的 `AlgorithmHook`（@MTProtector 加密）+ `AlgorithmHookManager` + `AlgorithmMonitorReceiver`。Dia 原版的 afterHookedMethod 反编译不完整，这里用 Kotlin 完整重写了 update/digest/doFinal/init 的状态追踪。

---

### 增强模式六大分类（P3）— `module/hook/extras/`

对应 Dia `mod_ex_*` 页的其余六大部分（模拟伪装/通知提示/反检测/大杂烩/高级/开发者）。每个分类一个 Module 协调器 + N 个子 hook，配置声明集中写在 `ui/prefs/PrefRegistry.kt`。**入口已统一收纳进「增强模式」目录页**（主界面 → 进入增强模式 → 选分类），全部复用 `CategoryActivity` + Compose `PrefScreenView`。除特别标注外，所有 hook 的核心逻辑均从 Dia 反编译的 Java 类完整翻译。

#### ⑥ 模拟与伪装 — `FakeModule` / `PrefRegistry.fake`

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| 设备属性 | `DevicePropsHook` | `DevicePropsHook` + `device_props` | 改 Build.* 静态字段 + hook Settings(android_id) / TelephonyManager(device_id)；支持随机 |
| 系统时间 | `FakeTimeHook` | `FakeTimeHook` | hook System.currentTimeMillis / Date / URLConnection，偏移或固定 |
| 时区 | `TimeZoneHook` | `TimeZoneHook` | hook TimeZone.getDefault / getTimeZone |
| WiFi | `WifiHook` | `WifiHook` | hook WifiInfo.getSSID/getBSSID/getMacAddress |
| 网络类型 | `FakeNetworkHook` | `FakeNetworkHook` | hook NetworkInfo.getType/isConnected 等，WiFi↔移动网络互换 |

#### ⑦ 通知与提示 — `NotifyModule` / `PrefRegistry.notify`

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| 禁用通知 | `NotificationHook` | `NotificationHook` | hook NotificationManager.notify/notifyAsUser + Service.startForeground，关键字过滤 |
| 禁用 Toast | `ToastDisableHook`（已有） | `ToastDisableHook` | hook Toast.show，关键字过滤 |

#### ⑧ 反检测 — `AntiDetectionModule` / `PrefRegistry.anti`

> Dia 原版 check/* 和 HideXposed 都是空壳（逻辑在加密的 otherModEx），本分类基于通用反检测知识重写。

| 子功能 | 实现类 | 原理 |
|---|---|---|
| 隐藏 Xposed | `HideXposedHook` | 过滤 stack trace / loadClass / 包名检测 |
| 隐藏 Root | `HideRootHook` | 拦截 Runtime.exec(su) + File.exists(常规 su 路径) + Build.TAGS 去 test-keys |
| 隐藏模拟器 | `HideEmulatorHook` | 改 Build.FINGERPRINT/PRODUCT/MODEL 去 generic/sdk 特征 |
| 隐藏多开 | `HideMultiAppHook` | 多开容器路径检测（占位，需 Application context 完善） |
| 隐藏 VPN | `VPNHook`（照搬 Dia） | hasTransport(TRANSPORT_VPN) / getName(tun0) / getType(TYPE_VPN) |
| GPS 伪造 | `LocationHook`（照搬 Dia） | getLatitude/getLongitude + setLatitude/setLongitude + 权限欺骗 |

#### ⑨ 大杂烩 — `MiscModule` / `PrefRegistry.misc`

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| 后台隐藏 | `HideOnBackgroundHook`（照搬） | `HideOnBackgroundHook` | ActivityManager.AppTask.setExcludeFromRecents |
| UiMode | `UiModeHook` | `UiModeHook` | Resources.getConfiguration.uiMode 改 night 位 |
| 剪贴板 | `ClipboardDisableHook`（照搬） | `ClipboardDisableHook` | getPrimaryClip/setPrimaryClip/hasPrimaryClip，读写/关键字三开关 |
| 传感器 | `SensorDisableHook`（照搬 smali） | `SensorDisableHook` | getSensorList 过滤加速度计/陀螺仪 type |
| 禁止退出 | `DisableExitHook`（已有） | `AppExitHook` | 拦截 finish/System.exit |
| 启动禁网 | `DisableNetworkOnStartHook` | `SocketForDisableNetworkHook` | ConnectivityManager + Socket.connect，启动后 N 毫秒后恢复 |
| so 库 | `SoLibraryHook`（照搬） | `SoLibraryHook` | Runtime.loadLibrary0 黑名单拦截 |
| 随机文件 | `RandomFileHook` | `RandomFileHook` | 指定路径返回随机内容（标记模式，字节级待完善） |
| HTTP 代理 | `HttpProxyHook` | `proxyhook.HttpProxyHook`（空壳） | System.setProperty http.proxyHost/Port + hook getProperty 兑底 |

#### ⑩ 高级功能 — `AdvancedFeaturesModule` / `PrefRegistry.advanced`

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| 信任用户证书 | `TrustUserCertsHook`（照搬） | `advanced.TrustUserCertsHook` | 临时降 targetSdk<24 调 getDefaultBuilder，让用户 CA 被信任（抓 HTTPS） |
| 签名伪造 | `AppSignaturesHook` | `appsignatures.AppSignaturesHook` | hook PackageManager.getPackageInfo 替换 signatures（PMS 代理层留 TODO） |
| 方法置空 | `MethodEmptyHook` | `method`/`method_empty` | hook 指定方法 beforeHookedMethod setResult(null)，原方法不执行 |

> 方法改写引擎（①）也属于高级分类，因是独立大引擎放在上面。

#### ⑪ 开发者 — `DevModule` / `PrefRegistry.dev`（Dia 最大分类）

| 子功能 | 实现类 | Dia 对应 | 原理 |
|---|---|---|---|
| Activity 名提示 | `ActivityShowNameHook` | `activity_show_name` | onResume Toast 类名 |
| Fragment 名提示 | `FragmentShowNameHook` | `fragment_show_name` | Fragment.onCreate Toast 类名 |
| 启动清数据 | `ClearDataOnStartHook` | `ClearDataOnStartHook` | Application.attachBaseContext 调 clearApplicationUserData |
| 等待调试器 | `WaitForDebuggableHook` | `WaitForDebuggableHook` | Application.onCreate 调 Debug.waitForDebugger |
| Intent 监控 | `IntentMonitorHook` | `IntentHook`（照搬） | startActivity* 拆 Intent → JSON → logcat |
| HTTP 监控 | `HttpMonitorHook` | `socket/SocketHook` | Socket.connect + URL.openConnection → logcat（字节级抓包待完善） |
| SQL 监控 | `SqlMonitorHook` | `sql/SqlManager` | SQLiteDatabase execSQL/query/insert/delete/update → logcat |

> Shell 监控（② 类似） / 算法监控（②） / Frida 注入（③） 也在主注册列表，属于广义的开发者调试功能。

## UI 架构（Jetpack Compose Material3）

全 App UI 已从 XML Preference 全面迁移到 **Jetpack Compose Material3**：

- `ui/theme/`：**MIUI 风格主题**（`Theme.kt` 用 Miuix 源码提取的色值构建 Material3 ColorScheme；`Type.kt` MIUI 排版；`Shape.kt` 大圆角）+ 深色模式跟随系统
- `ui/miuix/`：**MIUI 风格组件**（色值提取自 [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) 源码）
  - `MiuixColors.kt`：MIUI light/dark 真实色值（primary #3482FF / surface #F7F7F7 / divider #E0E0E0 等）
  - `MiuixSwitch.kt`：MIUI 胶囊开关（49×28dp，开启蓝色填充，按 Miuix 真实尺寸）
- `ui/prefs/`：自研轻量 Preference 框架，替代 `androidx.preference`（不再用 PreferenceFragmentCompat）
  - `Pref.kt`：sealed class 数据模型（`Switch` / `EditText` / `ListChoice` / `Action` / `Header`）+ `dependency` 条件显示
  - `PrefStore.kt`：用 **`LocalPrefs` CompositionLocal** 注入当前页读写哪份 SP（全局 `digXposed` 或 per-app `<包名>`），`rememberBoolPref` / `rememberStringPref` 自动跟随
  - `PrefComponents.kt`：Material3 渲染（`ListItem` + `Switch` / `AlertDialog` + `OutlinedTextField` / `RadioButton`）
  - `PrefScreenView.kt`：`CompositionLocalProvider(LocalPrefs provides sp)` + `LazyColumn` 渲染整页
  - `PrefRegistry.kt`：**全部 9 个分类的配置集中声明**（替代原 8 个 `res/xml/prefs_*.xml`），key 与注入侧读取的 SP key 1:1 对齐
- `ui/*Screen.kt` + `ui/*Activity.kt`：底部 3 Tab 主界面 / 应用列表 / 设置 / 功能列表 / 增强模式 / 分类详情 / 算法日志

**导航结构**（底部 3 Tab + per-app 配置）：

```
MainActivity（底部 Tab）
├─ 首页 Tab    HomeScreen      模块激活状态（通过 ActivationManager 连接 LSPosed binder 服务检测）
├─ 应用 Tab    AppsScreen      全部已安装 App 列表，每行 per-app 开关 + 点击进入功能列表
│   └─ AppFunctionListActivity（某 App 功能列表）
│      ├─ 基础全局对话框取消（直接开关）
│      └─ 增强模式（mod_ex 总开关 + 入口）
│         └─ EnhanceActivity（9 个分类入口）
│            ├─ dialog / button / activity  （对话框 / 按钮 / 活动界面）
│            └─ fake / notify / anti / misc / advanced / dev  （六大扩展分类）
│               └─ CategoryActivity（通用详情页，渲染 PrefRegistry.byKey(cat)）
└─ 设置 Tab    SettingsScreen  全局配置（switchModule / log_enable）
```

**SP 模型（per-app 隔离）**：

| SP 文件 | 内容 | 读写方 |
|---|---|---|
| `digXposed`（全局） | `switchModule` 总开关 / `log_enable` / `self_active` 自激活标志 | 设置页、首页 |
| `<包名>`（per-app） | `enabled` per-app 总开关 + 所有功能配置（`global_alert_close` / `mod_ex` / ...） | 应用页、功能列表、增强模式 |

注入侧 `Module.appPrefs = XSharedPreferences("com.pzdd.mydia", <当前进程包名>)` 读取该 App 自己的配置；
UI 侧 `rememberAppSp(pkg)` 写同一份文件。LSPosed 因 `xposedsharedprefs=true` 自动 world-readable。

## 目录结构

```
MyDia/
├── settings.gradle.kts              # 含 Aliyun 镜像（国内网络更稳）
├── build.gradle.kts
├── gradle.properties
├── local.properties.example         # 改成你的 SDK 路径后重命名为 local.properties
├── gradle/
│   ├── libs.versions.toml           # 版本目录
│   └── wrapper/                     # gradle 9.0.0 wrapper
├── gradlew / gradlew.bat
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml      # 含全部 Xposed meta-data
│       ├── assets/                # frida-gadget 配置（无 xposed_init，纯 API 102）
│       ├── java/com/pzdd/mydia/
│       │   ├── App.kt               # Application
│       │   ├── module/              # 注入侧（跑在目标 App 进程）
│       │   ├── monitor/             # 广播接收
│       │   └── ui/                  # Dia App 自身 UI（Compose Material3）
│       │       ├── theme/           # MIUI 主题：Theme.kt（Miuix 色值）/ Type.kt / Shape.kt
│       │       ├── miuix/           # MIUI 组件：MiuixColors.kt / MiuixSwitch.kt
│       │       ├── prefs/           # Compose Preference 框架（LocalPrefs 注入 SP）+ PrefRegistry（9 分类配置声明）
│       │       ├── MainActivity.kt  # 底部 3 Tab（首页/应用/设置）
│       │       ├── HomeScreen.kt    # 首页：模块激活状态
│       │       ├── AppsScreen.kt    # 应用列表：per-app 开关 + 点击进功能列表
│       │       ├── SettingsScreen.kt# 设置：全局配置
│       │       ├── AppFunctionListActivity.kt / .kt  # 某 App 功能列表（基础对话框取消 + 增强模式入口）
│       │       ├── EnhanceActivity.kt / EnhanceScreen.kt  # 增强模式目录（9 分类）
│       │       ├── CategoryActivity.kt / CategoryScreen.kt# 分类详情页
│       │       ├── AppLoader.kt     # PackageManager 加载已安装 App
│       │       └── CommonUi.kt（DiaScaffold）
│       └── res/{values,xml}/
└── MyDia-debug.apk                  # 本机已编译产物（可直接装）
```

## 构建

### 前置
- JDK 17
- Android SDK（含 platform android-36、build-tools 35.x）
- 网络能访问 Maven（默认配了 Aliyun 镜像 + Google + jitpack + api.xposed.info）

### 步骤
```bash
cp local.properties.example local.properties
# 编辑 local.properties，把 sdk.dir 改成你的 Android SDK 路径
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 版本（已验证可编译的组合）
| 组件 | 版本 |
|---|---|
| Gradle | 9.0.0 |
| AGP | 8.13.0 |
| Kotlin | 2.1.0 |
| compileSdk / minSdk / targetSdk | 36 / 24 / 34 |
| Xposed API（旧） | `de.robv.android.xposed:api:82`（compileOnly） |
| **libxposed API 102** | `io.github.libxposed:api:102` + `:service:102`（compileOnly，LSPosed 新 API） |
| core-ktx | `1.13.1`（稳定版，不用最新的 1.17 以免 API 36 专属代码路径在旧设备异常） |
| dexkit | `org.luckypray:dexkit:2.2.0`（方法查找引擎，含 4 ABI native 库） |
| Gson | `com.google.code.gson:gson:2.11.0`（规则 JSON 序列化） |
| **Compose BOM** | `androidx.compose:compose-bom:2024.10.00`（UI 全意渲染） |
| **Material3** | `androidx.compose.material3`（组件层）+ **MIUI 配色**（`ui/miuix/`） |
| activity-compose | `1.9.3`（`setContent` + `enableEdgeToEdge`） |
| Compose Compiler | `org.jetbrains.kotlin.plugin.compose:2.1.0`（Kotlin 2.0+ 独立插件） |

> 想用更保守的组合：AGP 8.7.3 + Gradle 8.11.1 + Kotlin 2.0.21 + compileSdk 35 也可以，
> 改 `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`、`app/build.gradle.kts` 三处即可。

## 安装与验证

1. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. 打开 **LSPosed Manager** → 模块 → 启用「MyDia」（LSPosed 会自动识别 libxposed 102 入口）
3. 作用域勾选一个测试 App（默认已带 `com.android.settings` 作示例，见 `res/values/arrays.xml`）
   - 激活检测靠 LSPosed Manager 进程推送的 binder（通过 libxposed-service 的 XposedProvider），**无需**把 com.pzdd.mydia 加入作用域
4. 打开 MyDia，**首页**确认模块激活状态 → **应用**页给目标 App 开右侧开关 → 点进去配功能
5. 打开测试 App，触发一个 `setCancelable(false)` 的对话框 → 应该能点外部/返回键取消 ✅
6. `adb logcat | grep MyDia` 看注入日志：
   - `[libxposed 102] module loaded: ...`（新 API 入口）
   - `[MyDia] injected: process=...`
   - `[MyDia] DialogCancelHook ACTIVE.`

### 闪退排查

若打开 MyDia 闪退：
1. **再次打开** App —— 内置 [CrashCatcher](app/src/main/java/com/pzdd/mydia/CrashCatcher.kt) 会在下次启动**弹窗显示上次崩溃栈**，复制出来即可定位
2. 或看 LSPosed Manager → 日志（如果模块已激活）
3. 或 `adb logcat | grep -E "AndroidRuntime|MyDia"`

### libxposed 102 入口架构（完全对齐官方 example）

MyDia 是**纯 libxposed API 102 模块**，配置完全照搬 [`libxposed/example`](https://github.com/libxposed/example)。
LSPosed 新版靠 **`META-INF/xposed/module.prop`** 识别模块与判定 API 版本，**不再用 Manifest 的 xposed meta-data**。

| 文件 / 配置 | 作用 | 值 |
|---|---|---|
| `META-INF/xposed/java_init.list` | **加载入口** | `com.pzdd.mydia.module.LibXposedEntry` |
| `META-INF/xposed/module.prop` | **【关键】API 版本声明** | `minApiVersion=101` + `targetApiVersion=102`（LSPosed 据此显示 “API 102”） |
| `packaging.merges` | 确保上述文件打入 APK | `merges += "META-INF/xposed/"` |
| Manifest | 仅保留 SP 可读标记 | `xposedsharedprefs=true`（XSharedPreferences 跨进程读需要） |
| 入口类 | 继承 `XposedModule`，无参构造 | `module/LibXposedEntry.kt` |
| 适用框架 | | **LSPosed** |

> **⚠ 三大“不能”（踩坑总结）**
> 1. **不能放 `assets/xposed_init`** — 一旦存在，LSPosed 走 legacy 加载路径，期望入口实现
>    `IXposedHookLoadPackage.handleLoadPackage()`。但本模块入口继承 `XposedModule`（新 API），
>    没有 `handleLoadPackage`，后果：显示 “Legacy”、`onPackageLoaded()` 永不调用、模块不激活。
> 2. **不能在 Manifest 放 `xposedmodule/xposedminversion/xposedapi` meta-data** — 官方 example
>    完全没用这些。保留 `xposedapi=102` 也没用——LSPosed 看的是 module.prop 的 targetApiVersion。
> 3. **不能靠 Manifest 的 `xposedapi=102` 声明版本** — 实际判定看 `module.prop`。
>
> **识别 / 加载流程**：LSPosed 扫描 APK 的 `META-INF/xposed/module.prop` → 识别为 API 102 模块 →
> 读 `java_init.list` 拿入口类 → 反射 new（`XposedModule` 无参构造）→ `attachFramework()`
> 注入框架 → 回调 `onModuleLoaded` / `onPackageLoaded`。
>
> **为何之前反复在 legacy / 不识别之间横跳？** 一直误以为 API 版本靠 Manifest meta-data 声明，
> 实际靠 module.prop。加上 `assets/xposed_init` 又会触发 legacy 路径。两误叠加导致三个故障：
> 显示 legacy、`onPackageLoaded` 不调用、不激活。删 `assets/xposed_init` + 加 module.prop 后全通。

**为何 hook 代码还用旧 `XposedBridge`？**
LSPosed 运行时同时提供新/旧两套 API（兼容层）。hook 实现层用 `XposedBridge.findAndHookMethod`
等静态方法更简洁，`de.robv.android.xposed:api:82` 只作 `compileOnly`（不打包进 APK，运行时
由 LSPosed 提供）。这不影响「模块是 API 102」的判定——加载路径只看 init 文件。入口
`LibXposedEntry.onPackageLoaded()` 委托给 `Module.onLoadPackage()`。

## 加新功能（标准流程）

1. 新建 `app/src/main/java/com/pzdd/mydia/module/hook/XxxHook.kt`：
   ```kotlin
   class XxxHook : DiaHook() {
       override fun install() {
           if (!appPrefs.getBoolean("xxx_enable", false)) return
           // 用 globalPrefs / appPrefs / classLoader / ApplicationHook 按需 hook
       }
   }
   ```
2. 在 `Module.kt` 的 `DiaHook.register(...)` 列表加一行 `XxxHook::class.java`
3. 在 `ui/prefs/PrefRegistry.kt`（详情页）或 `ui/MainScreen.kt`（主界面）加一个对应 key 的 `Pref.Switch(...)` 声明
4. 重新编译装机即可。

## 下一步（按 dia_analysis/docs/06_复刻路线图.md）

- 阶段 4：**方法改写引擎**（取消 `app/build.gradle.kts` 里 dexkit 的注释，照 `docs/02_功能清单.md §D` 的 `Rule`/`Rewrite` 数据结构实现 `MethodRewriteHook`）
- 阶段 5：**Frida 注入**（`FridaHook`：在 `ApplicationHook.onReady` 后 `System.loadLibrary(gadget)`，照 `docs/03 §3`）
- 阶段 6：设备伪装 / 反检测（`DevicePropsHook` 改 `Build.*`、`SystemProperties.get` 等）

## 与原版 Dia 的对照

| 原版 Dia（dialog.box） | 本骨架 |
|---|---|
| `com.mhook.dialog.Module` | `module/Module.kt`（单例） |
| `dialog.box.hook.DiaHook` | `module/hook/DiaHook.kt` |
| `dialog.box.hook.ApplicationHook` | `module/hook/ApplicationHook.kt` |
| `Module.alertClose()` | `module/hook/DialogCancelHook.kt` |
| `task.hook.RuntimeHook` + `ShellMonitorReceiver` | `module/hook/ShellMonitorHook.kt` + `monitor/ShellMonitorReceiver.kt` |
| `dialog.box.expand.rewrite.{Rule,Rewrite,RuleGroup,RuleGroupDataStore,SmaliSignatureConverter}` | `module/rewrite/*`（同结构，fastjson→Gson） |
| `dialog.box.hook.MethodRewriteHook` | `module/hook/MethodRewriteHook.kt` + `MultiDexHook.kt`（m14363 值转换用 Kotlin 重写） |
| `dialog.box.hook.MultiDexHook` | `module/hook/MultiDexHook.kt` |
| `dialog.box.hook.FridaHook` + `task.ui.expand.FridaActivity` | `module/hook/FridaHook.kt` + `assets/frida-gadget/` |
| `task.hook.algorithm.{AlgorithmHook,AlgorithmHookManager}` + `task.receiver.AlgorithmMonitorReceiver` | `algorithm/*` + `monitor/AlgorithmMonitorReceiver.kt` + `ui/AlgorithmLogActivity.kt` |
| `Module.otherModEx()` (MTProtector 加密) + `ClickBtnHook` + `ViewGroupHook` + `AppEntryHook` + `DisableActivityHook` + `KeyEventHook` + `ViewHelper` | `module/hook/enhance/*`（增强模式 8 个子 hook + ViewScanner + EnhanceActivity） |
| `res/xml/mod_ex_{dialog,btn,activity}.xml` + `condition_value` 数组 | `ui/prefs/PrefRegistry.kt` 的 `dialog`/`button`/`activity` + `TRIGGER_GESTURES`（Compose） |
| `mod_ex_fake.xml` + `DevicePropsHook`/`FakeTimeHook`/`TimeZoneHook`/`WifiHook`/`FakeNetworkHook` | `module/hook/extras/{DeviceProps,FakeTime,TimeZone,Wifi,FakeNetwork}Hook.kt` + `FakeModule` |
| `mod_ex_notify_and_tips.xml` + `NotificationHook` + `ToastDisableHook` | `module/hook/extras/NotificationHook.kt` + `NotifyModule` |
| `mod_ex_anti_detection.xml` + `check/*`(空壳) + `HideXposedHook`(空壳) + `VPNHook` + `LocationHook` | `module/hook/extras/{HideXposed,HideRoot,HideEmulator,HideMultiApp,VPN,Location}Hook.kt` + `AntiDetectionModule`（反检测逻辑重写） |
| `mod_ex_misc.xml` + `ClipboardDisableHook`/`SensorDisableHook`/`HideOnBackgroundHook`/`SoLibraryHook`/`RandomFileHook`/`HttpProxyHook`(空壳)/`SocketForDisableNetworkHook`/`UiModeHook` | `module/hook/extras/{Clipboard,Sensor,HideOnBackground,SoLibrary,RandomFile,HttpProxy,DisableNetworkOnStart,UiMode}Hook.kt` + `MiscModule` |
| `mod_ex_advanced.xml` + `advanced/TrustUserCertsHook` + `appsignatures/AppSignaturesHook` + `method` | `module/hook/extras/{TrustUserCerts,AppSignatures,MethodEmpty}Hook.kt` + `AdvancedFeaturesModule` |
| `mod_ex_dev.xml` + `ClearDataOnStartHook`/`WaitForDebuggableHook`/`IntentHook`/`socket/SocketHook`/`sql/SqlManager` + `activity_show_name`/`fragment_show_name` | `module/hook/extras/{ActivityShowName,FragmentShowName,ClearDataOnStart,WaitForDebuggable,IntentMonitor,HttpMonitor,SqlMonitor}Hook.kt` + `DevModule` |
| `XSharedPreferences("dialog.box","digXposed")` | `XSharedPreferences("com.pzdd.mydia","digXposed")` |
| `res/xml/*.xml` + `PreferenceFragmentCompat` + `AppCompatActivity` | `ui/prefs/*` + `PrefScreenView` + `ComponentActivity` + `setContent`（Compose Material3） |

完整原理见 `../dia_analysis/docs/`。
