package com.pzdd.mydia.ui.prefs

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * 渲染整张 [PrefScreen]：用 [sp] 注入 [LocalPrefs]，再 LazyColumn 列出所有 [Pref]。
 *
 * @param sp   本页读写哪份 SP（全局 digXposed 或 per-app `<包名>`）
 */
@Composable
fun PrefScreenView(
    screen: PrefScreen,
    sp: SharedPreferences,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    CompositionLocalProvider(LocalPrefs provides sp) {
        PrefGroupedColumn(
            items = screen.items,
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}
