package com.fesht3.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.fesht3.zafiro.app.ui.content.TakeoverRuleDetailContent
import com.fesht3.zafiro.app.ui.nav.TakeoverRuleDetailPage

@Composable
internal fun TakeoverRuleDetailRoute(
    page: TakeoverRuleDetailPage,
    onBack: () -> Unit,
) {
    TakeoverRuleDetailContent(
        page = page,
        onBack = onBack,
    )
}
