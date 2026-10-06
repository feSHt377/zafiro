package com.fesht3.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.fesht3.zafiro.app.ui.content.SettingsDetailPageContent
import com.fesht3.zafiro.app.ui.nav.SettingsDetailPage
import com.fesht3.zafiro.app.ui.nav.ZafiroPage

@Composable
internal fun SettingsDetailPageRoute(
    page: SettingsDetailPage,
    onPush: (ZafiroPage) -> Unit,
    onBack: () -> Unit,
) {
    SettingsDetailPageContent(
        group = page.group,
        onPush = onPush,
        onBack = onBack,
    )
}
