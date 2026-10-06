package com.fesht3.zafiro.app.ui

import androidx.compose.runtime.Composable
import com.niki914.uikit.infra.nav.NavigationEntry
import com.fesht3.zafiro.app.ui.content.CustomPyToolDetailContent
import com.fesht3.zafiro.app.ui.content.CustomPyToolsSettingsContent
import com.fesht3.zafiro.app.ui.model.StartupAssistantUi
import com.fesht3.zafiro.app.ui.nav.BuiltinToolGroupDetailPage
import com.fesht3.zafiro.app.ui.nav.ConfigurePage
import com.fesht3.zafiro.app.ui.nav.ConversationHistoryPage
import com.fesht3.zafiro.app.ui.nav.CustomPyToolDetailPage
import com.fesht3.zafiro.app.ui.nav.CustomPyToolsPage
import com.fesht3.zafiro.app.ui.nav.DonePage
import com.fesht3.zafiro.app.ui.nav.ExecutionRuleDetailPage
import com.fesht3.zafiro.app.ui.nav.HomePage
import com.fesht3.zafiro.app.ui.nav.McpServerDetailPage
import com.fesht3.zafiro.app.ui.nav.ProviderPickPage
import com.fesht3.zafiro.app.ui.nav.PromptEditPage
import com.fesht3.zafiro.app.ui.nav.SavedConfigDetailPage
import com.fesht3.zafiro.app.ui.nav.SettingsDetailPage
import com.fesht3.zafiro.app.ui.nav.SettingsHomePage
import com.fesht3.zafiro.app.ui.nav.SettingsProviderPickPage
import com.fesht3.zafiro.app.ui.nav.SkillDetailPage
import com.fesht3.zafiro.app.ui.nav.StartupPage
import com.fesht3.zafiro.app.ui.nav.TakeoverRuleDetailPage
import com.fesht3.zafiro.app.ui.nav.ThemeSettingsPage
import com.fesht3.zafiro.app.ui.nav.ZafiroPage
import com.fesht3.zafiro.app.ui.route.BuiltinToolGroupDetailRoute
import com.fesht3.zafiro.app.ui.route.ConfigurePageRoute
import com.fesht3.zafiro.app.ui.route.ConversationHistoryPageRoute
import com.fesht3.zafiro.app.ui.route.DonePageRoute
import com.fesht3.zafiro.app.ui.route.ExecutionRuleDetailRoute
import com.fesht3.zafiro.app.ui.route.HomePageRoute
import com.fesht3.zafiro.app.ui.route.McpServerDetailRoute
import com.fesht3.zafiro.app.ui.route.ProviderPickPageRoute
import com.fesht3.zafiro.app.ui.route.PromptEditRoute
import com.fesht3.zafiro.app.ui.route.SavedConfigDetailRoute
import com.fesht3.zafiro.app.ui.route.SettingsDetailPageRoute
import com.fesht3.zafiro.app.ui.route.SettingsHomePageRoute
import com.fesht3.zafiro.app.ui.route.SettingsProviderPickPageRoute
import com.fesht3.zafiro.app.ui.route.SkillDetailRoute
import com.fesht3.zafiro.app.ui.route.StartupPageRoute
import com.fesht3.zafiro.app.ui.route.TakeoverRuleDetailRoute
import com.fesht3.zafiro.app.ui.route.ThemeSettingsPageRoute

@Composable
fun ZafiroPageContent(
    entry: NavigationEntry<ZafiroPage>,
    startupAssistantUi: StartupAssistantUi,
    onPush: (ZafiroPage) -> Unit,
    onPushFromLeft: (ZafiroPage) -> Unit,
    onPop: () -> Unit,
    onPopMultiple: (Int) -> Unit,
    onPopToRight: () -> Unit,
    onResetTo: (ZafiroPage) -> Unit,
    selectedConversationId: String?,
    onConversationSelected: (String) -> Unit,
    onConversationSelectionConsumed: (String) -> Unit,
    activeConversationId: String?,
    activeConversationTitle: String?,
    onActiveConversationChanged: (String?, String?) -> Unit,
    onCurrentConversationDeleted: suspend (String) -> Unit,
) {
    when (val page = entry.page) {
        StartupPage -> StartupPageRoute(
            startupAssistantUi = startupAssistantUi,
            onPush = onPush,
        )

        ProviderPickPage -> ProviderPickPageRoute(
            onPush = onPush,
        )

        SettingsProviderPickPage -> SettingsProviderPickPageRoute(
            onPush = onPush,
        )

        ThemeSettingsPage -> ThemeSettingsPageRoute()

        is ConfigurePage -> ConfigurePageRoute(
            page = page,
            onPush = onPush,
        )

        is SavedConfigDetailPage -> SavedConfigDetailRoute(
            page = page,
            onBack = onPop,
            onPopMultiple = onPopMultiple,
        )

        DonePage -> DonePageRoute(
            onResetTo = onResetTo,
        )

        HomePage -> HomePageRoute(
            onPush = onPush,
            onPushFromLeft = onPushFromLeft,
            selectedConversationId = selectedConversationId,
            onConversationSelectionConsumed = onConversationSelectionConsumed,
            onActiveConversationChanged = onActiveConversationChanged,
        )

        ConversationHistoryPage -> ConversationHistoryPageRoute(
            activeConversationId = activeConversationId,
            activeConversationTitle = activeConversationTitle,
            onBack = onPopToRight,
            onConversationSelected = { id ->
                onConversationSelected(id)
                onPopToRight()
            },
            onCurrentConversationDeleted = onCurrentConversationDeleted,
            onActiveConversationRenamed = { newTitle ->
                onActiveConversationChanged(activeConversationId, newTitle)
            },
        )

        SettingsHomePage -> SettingsHomePageRoute(
            onPush = onPush,
        )

        is SettingsDetailPage -> SettingsDetailPageRoute(
            page = page,
            onPush = onPush,
            onBack = onPop,
        )

        PromptEditPage -> PromptEditRoute(
            onBack = onPop,
        )

        is McpServerDetailPage -> McpServerDetailRoute(
            page = page,
            onBack = onPop,
        )

        is ExecutionRuleDetailPage -> ExecutionRuleDetailRoute(
            page = page,
            onBack = onPop,
        )

        is TakeoverRuleDetailPage -> TakeoverRuleDetailRoute(
            page = page,
            onBack = onPop,
        )

        is SkillDetailPage -> SkillDetailRoute(
            page = page,
            onBack = onPop,
        )

        is CustomPyToolDetailPage -> CustomPyToolDetailContent(
            page = page,
            onBack = onPop,
        )

        is BuiltinToolGroupDetailPage -> BuiltinToolGroupDetailRoute(
            page = page,
            onBack = onPop,
            onPush = onPush,
        )

        CustomPyToolsPage -> CustomPyToolsSettingsContent(
            onOpenToolDetail = { name, index, isCreating ->
                onPush(CustomPyToolDetailPage(name, index, isCreating))
            },
        )
    }
}
