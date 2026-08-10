package com.pzdd.mydia.module.rewrite

import androidx.annotation.Keep

/**
 * 单条改写规则。对应 Dia 的 dialog.box.expand.rewrite.Rule。
 *
 * 一条 [Rule] 定位【一个方法】（className + methodName + 参数 smali 签名），
 * 里面挂一组 [Rewrite]（每个 Rewrite 改一个入参，或改返回值）。
 *
 * 用 JSON 存在 world-readable SP 里，注入侧读出来直接 Gson 反序列化。
 *
 * 示例（取消某个弹框 / 固定返回值）：
 * ```json
 * {
 *   "id":"r1","className":"com.foo.Bar","methodName":"showAds",
 *   "signature":"()Z","enabled":true,
 *   "rewrites":[{"index":-1,"type":4,"replace":"true"}]
 * }
 * ```
 * index=-1 表示改返回值；type=4 (VOID→其实这里用 BOOLEAN)；
 * 具体 type 语义见 [Rewrite]。
 */
@Keep
data class Rule(
    /** 规则唯一 id，用于 hashCode 去重 */
    var id: String = "",
    /** 目标类全限定名（支持 dexkit 简写/内部类 $ 写法） */
    var className: String = "",
    /** 目标方法名 */
    var methodName: String = "",
    /** 方法签名，仅用于显示与日志，查找不依赖它（dexkit 按参数类型查找） */
    var signature: String = "",

    var enabled: Boolean = false,
    /** true=改写规则直接生效；false=只打日志不改值（dry-run） */
    var bypass: Boolean = false,
    /** hook 构造函数而不是普通方法 */
    var isConstructor: Boolean = false,

    // ---- 调试选项 ----
    var printLog: Boolean = false,
    var printLogLocal: Boolean = false,
    var printLogStackTrace: Boolean = false,
    var trace: Boolean = false,
    var traceBufferSizeMB: Int = 8,
    var dumpHprofData: Boolean = false,
    var dumpHprofDataAfterMethod: Boolean = false,
    /** 一次性：把本进程 LoadedApk.getPackageName() 临时替换掉，用于“伪装包名”场景 */
    var simulationPackageName: Boolean = false,
    var simulationPackageNameValue: String = "",

    var rewrites: MutableList<Rewrite> = mutableListOf(),
) {
    override fun hashCode(): Int = id.hashCode()
}
