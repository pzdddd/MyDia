package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.module.ActivationManager
import com.pzdd.mydia.ui.prefs.LocalPrefs
import com.pzdd.mydia.ui.prefs.rememberBoolPref
import com.pzdd.mydia.ui.prefs.rememberGlobalSp

private const val HOST_PACKAGE = "com.pzdd.mydia"

/**
 * 首页：模块激活状态。
 *
 * 激活检测用【官方标准机制】：[ActivationManager] 通过 `libxposed-service` 的
 * `XposedServiceHelper` 绑定 LSPosed binder 服务。binder 收到 = 框架已安装 + 模块已启用。
 *
 * **不需要把模块自身加入 Xposed 作用域**——binder 由 LSPosed Manager 进程通过
 * `XposedProvider` 推送给 App 自身进程。
 */
@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val sp = rememberGlobalSp()
    // 【关键】必须把全局 SP 注入 LocalPrefs，否则 rememberBoolPref 读 LocalPrefs.current 会抛异常
    CompositionLocalProvider(LocalPrefs provides sp) {
        HomeScreenContent(contentPadding, context)
    }
}

@Composable
private fun HomeScreenContent(
    contentPadding: PaddingValues,
    context: android.content.Context,
) {
    // 直接订阅 ActivationManager 的响应式状态
    val state = ActivationManager.activeState.value
    val switchModule = rememberBoolPref("switchModule", false)
    val enabledCount = remember(context) { countEnabledApps(context) }

    // 首页顶部有液态玻璃顶栏（状态栏 + 标题区），让出空间避免激活卡被盖住
    val topBarHeight = WindowInsets.statusBars.asPaddingValues()
        .calculateTopPadding() + 56.dp
    val padded = PaddingValues(
        start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
        end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
        top = topBarHeight,
        bottom = contentPadding.calculateBottomPadding(),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padded,
    ) {
        item { StatusCard(state) }
        item { Spacer(Modifier.height(12.dp)) }
        // 信息行包进一张液态玻璃卡片（首页只有一两张卡片，不会触发 native 崩溃）
        item {
            val svc = (state as? ActivationManager.ActivationState.Activated)?.service
            val fwName = svc?.frameworkName ?: "未连接"
            val fwVer = svc?.frameworkVersion ?: "—"
            val apiVer = svc?.apiVersion?.toString() ?: "—"
            // 信息行包进一张半透明卡片（玻璃质感，无 native 捕获，安全不崩）
            androidx.compose.material3.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                ),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow("模块总开关", if (switchModule.value) "已开启" else "已关闭")
                    HorizontalDivider()
                    InfoRow("已启用的应用数", "$enabledCount 个")
                    HorizontalDivider()
                    InfoRow("激活状态", activationLabel(state))
                    HorizontalDivider()
                    InfoRow("Xposed 框架", fwName)
                    HorizontalDivider()
                    InfoRow("框架版本", fwVer)
                    HorizontalDivider()
                    InfoRow("API 版本", apiVer)
                    HorizontalDivider()
                    InfoRow("模块包名", HOST_PACKAGE)
                    HorizontalDivider()
                    InfoRow("框架管理器", detectFramework(context))
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Text(
                text = hintText(state),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private fun activationLabel(state: ActivationManager.ActivationState): String = when (state) {
    is ActivationManager.ActivationState.Activated -> "✓ 已激活"
    ActivationManager.ActivationState.NotActivated -> "✗ 未激活"
    ActivationManager.ActivationState.Unknown -> "… 检测中"
}

private fun hintText(state: ActivationManager.ActivationState): String = when (state) {
    is ActivationManager.ActivationState.Activated ->
        "✓ 模块已激活。请到「应用」页选择要 hook 的 App，打开右侧开关即可生效。"
    ActivationManager.ActivationState.NotActivated ->
        "⚠ 模块未激活。请在 LSPosed Manager 中启用本模块。\n" +
            "（无需把模块自身加入作用域，激活检测通过 LSPosed binder 服务完成）"
    ActivationManager.ActivationState.Unknown ->
        "正在连接 LSPosed 服务…"
}

@Composable
private fun StatusCard(state: ActivationManager.ActivationState) {
    // 三态：已激活（绿）/ 未激活（红）/ 检测中（灰）
    val (color, title, subtitle) = when (state) {
        is ActivationManager.ActivationState.Activated -> Triple(
            Color(0xFF2E7D32),
            "模块已激活",
            "框架：${state.service.frameworkName} ${state.service.frameworkVersion}\nAPI：${state.service.apiVersion}",
        )
        ActivationManager.ActivationState.NotActivated -> Triple(
            Color(0xFFC62828),
            "模块未激活",
            "请在 LSPosed Manager 中启用本模块",
        )
        ActivationManager.ActivationState.Unknown -> Triple(
            Color(0xFF666666),
            "正在检测…",
            "正在连接 LSPosed binder 服务",
        )
    }
    val icon = if (state is ActivationManager.ActivationState.Activated)
        Icons.Filled.CheckCircle else Icons.Filled.Warning

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state is ActivationManager.ActivationState.Unknown) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = color,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(48.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 检测 LSPosed / EdXposed / Xposed 框架管理器是否安装。 */
private fun detectFramework(context: android.content.Context): String {
    val pkgs = listOf(
        "org.lsposed.manager" to "LSPosed",
        "io.github.lsposed.manager" to "LSPosed",
        "org.meowcat.edxposed.manager" to "EdXposed",
        "de.robv.android.xposed.installer" to "Xposed",
    )
    return pkgs.firstNotNullOfOrNull { (pkg, name) ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); name }.getOrNull()
    } ?: "未检测到"
}

/** 统计有多少 App 的 per-app 开关已开启（SP 文件在 shared_prefs 目录，文件名 = `<包名>.xml`）。 */
private fun countEnabledApps(context: android.content.Context): Int {
    val dir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
    val files = dir.listFiles() ?: return 0
    return files.count { f ->
        val pkg = f.name.removeSuffix(".xml")
        if (pkg.isEmpty() || f.name == "digXposed.xml") return@count false
        runCatching {
            context.getSharedPreferences(pkg, android.content.Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
        }.getOrDefault(false)
    }
}
