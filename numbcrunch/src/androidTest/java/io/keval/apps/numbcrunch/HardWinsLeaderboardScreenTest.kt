package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.engagement.*
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.leaderboard.HardWinsLeaderboardScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HardWinsLeaderboardScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connected = HardWinsLeaderboardState(
        context = HardWinsContext("local", 1, 10, "player", true), status = RankingStatus.READY,
        page = RankingPage(listOf(RankingRow("1", "玩家 with a long Unicode gamer name", "9,223,372,036,854,775,807")), null),
    )

    @Test fun allTimeRanksAndLongNamesRemainReachableWithLargeText() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                NumbCrunchTheme { Box(Modifier.size(320.dp, 480.dp)) {
                    HardWinsLeaderboardScreen(connected, {}, {}, {})
                } }
            }
        }
        compose.onNodeWithText(context.getString(R.string.hard_board_all_time)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("玩家 with a long Unicode gamer name")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Daily").assertDoesNotExist()
        compose.onNodeWithText("Weekly").assertDoesNotExist()
    }

    @Test fun unavailableRankingsOfferRetryWithoutChangingLocalCount() {
        var requests = 0
        compose.setContent { NumbCrunchTheme {
            HardWinsLeaderboardScreen(connected.copy(status = RankingStatus.ERROR, page = null), {}, { requests++ }, {})
        } }
        compose.onNodeWithText(context.getString(R.string.progress_retry)).performScrollTo().performClick()
        assertEquals(1, requests)
        compose.onNodeWithText(context.getString(R.string.hard_board_local_total, "10")).performScrollTo().assertIsDisplayed()
    }
}
