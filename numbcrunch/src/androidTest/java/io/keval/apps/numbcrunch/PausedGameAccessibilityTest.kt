package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.RoundMode
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.home.HomeScreen
import io.keval.apps.numbcrunch.gameplay.home.HomeUiState
import io.keval.apps.numbcrunch.gameplay.progress.OngoingRound
import org.junit.Rule
import org.junit.Test

class PausedGameAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun savedGameRemainsReachableOnCompactLayoutWithLargeText() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                NumbCrunchTheme {
                    Box(Modifier.size(320.dp, 480.dp)) {
                        HomeScreen(
                            uiState = HomeUiState(), onPlayerModeSelected = {}, onDifficultySelected = {},
                            onFriendSecretDigit = {}, onFriendSecretDelete = {}, onStartClick = {},
                            onTutorialClick = {}, onGameplayClick = {}, onAboutClick = {}, onSettingsClick = {},
                            ongoingRounds = listOf(OngoingRound("round", "owner", RoundMode.DOUBLE, Difficulty.MEDIUM, 1, true)),
                        )
                    }
                }
            }
        }
        val description = context.getString(R.string.progress_resume_description,
            context.getString(R.string.label_double_player), context.getString(R.string.difficulty_medium))
        compose.onNodeWithContentDescription(description).performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.progress_submitted_guesses, 1, 1))
            .performScrollTo().assertIsDisplayed()
    }
}
