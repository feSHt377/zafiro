package com.fesht3.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.fesht3.zafiro.app.ui.content.DonePageContent
import com.fesht3.zafiro.app.ui.nav.HomePage
import com.fesht3.zafiro.app.ui.nav.ZafiroPage
import com.fesht3.zafiro.repo.XRepo
import kotlinx.coroutines.launch

@Composable
internal fun DonePageRoute(
    onResetTo: (ZafiroPage) -> Unit,
) {
    val scope = rememberCoroutineScope()

    DonePageContent(
        onEnterHome = {
            scope.launch {
                completeOnboarding()
                onResetTo(HomePage)
            }
        },
    )
}

private suspend fun completeOnboarding() {
    if (XRepo.onboardingCompleted()) {
        return
    }
    XRepo.setOnboardingCompleted(true)
}
