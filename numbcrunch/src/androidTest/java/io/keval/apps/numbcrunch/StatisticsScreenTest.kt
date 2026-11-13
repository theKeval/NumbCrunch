package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.progress.PlayerProfile
import io.keval.apps.numbcrunch.domain.progress.PlayerStatistics
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.statistics.StatisticsScreen
import io.keval.apps.numbcrunch.gameplay.statistics.StatisticsUiState
import java.text.NumberFormat
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val base = StatisticsUiState(isLoading = false, profile = PlayerProfile("local", null, 1), recordedSince = 1)

    @Test fun weightedAverageAndNoWinEmptyValuesHaveReadableLabels() {
        compose.setContent {
            NumbCrunchTheme(darkTheme = false, dynamicColor = false) {
                StatisticsScreen(base.copy(statistics = PlayerStatistics(played = 3, wins = 3, winningGuessSum = 21)),
                    {}, {}, {}, {})
            }
        }
        compose.onNode(hasText(context.getString(R.string.statistics_average)) and hasText("7"))
            .performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.statistics_wins)) and hasText("3"))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun noWinsShowsDashAndExplanationInsteadOfZeroAverage() {
        compose.setContent { NumbCrunchTheme(darkTheme = false, dynamicColor = false) { StatisticsScreen(base, {}, {}, {}, {}) } }
        compose.onNode(hasText(context.getString(R.string.statistics_average)) and hasText("—"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.statistics_no_wins)).performScrollTo().assertIsDisplayed()
    }

    @Test fun longNameAndLargeCountsRemainReachableAtLargeTextOnCompactScreen() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                NumbCrunchTheme(darkTheme = false, dynamicColor = false) {
                    Box(Modifier.size(320.dp, 480.dp)) {
                        StatisticsScreen(base.copy(statistics = PlayerStatistics(played = Long.MAX_VALUE)), {}, {}, {}, {},
                            playerName = "A player with a very long gamer name that wraps across several lines")
                    }
                }
            }
        }
        compose.onNode(hasText(context.getString(R.string.statistics_played)) and hasText(NumberFormat.getIntegerInstance().format(Long.MAX_VALUE)))
            .performScrollTo().assertIsDisplayed()
    }
}
