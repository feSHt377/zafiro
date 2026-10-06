package com.fesht3.zafiro.app.ui.model

import androidx.test.core.app.ApplicationProvider
import com.fesht3.zafiro.repo.FakeDomainSettingsStore
import com.fesht3.zafiro.repo.XRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AboutSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule =
        MainDispatcherRule()

    @Before
    fun setUp() {
        XRepo.installStoreForTest(FakeDomainSettingsStore())
        XRepo.init(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        XRepo.resetForTest()
    }

    @Test
    fun openItem_withGithub_emitsOpenUri() = runTest {
        val viewModel = AboutSettingsViewModel()
        val effectDeferred = async { viewModel.uiEffect.first() }

        viewModel.sendIntent(AboutSettingsIntent.OpenItem(AboutSettingsItemId.Github))
        advanceUntilIdle()

        assertEquals(
            AboutSettingsEffect.OpenUri("https://github.com/feSHt377/zafiro"),
            effectDeferred.await(),
        )
    }

    @Test
    fun openItem_withFeatureFeedback_emitsOpenFeedbackIssue() = runTest {
        val viewModel = AboutSettingsViewModel()
        val effectDeferred = async { viewModel.uiEffect.first() }

        viewModel.sendIntent(AboutSettingsIntent.OpenItem(AboutSettingsItemId.FeatureFeedback))
        advanceUntilIdle()

        val effect = effectDeferred.await() as AboutSettingsEffect.OpenFeedbackIssue
        assertEquals("[FEATURE] ", effect.title)
    }

    @Test
    fun openItem_withBugFeedback_emitsOpenFeedbackIssue() = runTest {
        val viewModel = AboutSettingsViewModel()
        val effectDeferred = async { viewModel.uiEffect.first() }

        viewModel.sendIntent(AboutSettingsIntent.OpenItem(AboutSettingsItemId.BugFeedback))
        advanceUntilIdle()

        val effect = effectDeferred.await() as AboutSettingsEffect.OpenFeedbackIssue
        assertEquals("[BUG] ", effect.title)
    }
}
