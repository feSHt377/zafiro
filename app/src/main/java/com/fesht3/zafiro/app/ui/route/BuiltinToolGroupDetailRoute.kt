package com.fesht3.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.fesht3.zafiro.app.ui.content.BuiltinToolGroupDetailContent
import com.fesht3.zafiro.app.ui.nav.BuiltinToolGroupDetailPage
import com.fesht3.zafiro.app.ui.nav.ZafiroPage

@Composable
internal fun BuiltinToolGroupDetailRoute(
    page: BuiltinToolGroupDetailPage,
    onBack: () -> Unit,
    onPush: (ZafiroPage) -> Unit,
) {
    BuiltinToolGroupDetailContent(
        page = page,
        onBack = onBack,
        onPush = onPush,
    )
}
