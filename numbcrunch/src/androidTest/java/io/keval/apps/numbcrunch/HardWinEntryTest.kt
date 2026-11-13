package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.GuessRules
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.game.GameScreen
import io.keval.apps.numbcrunch.gameplay.game.GameUiState
import io.keval.apps.numbcrunch.gameplay.home.PlayerMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HardWinEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlySingleHardWinOffersLeaderboardAndOpeningDoesNotReplay() {
        val state = mutableStateOf(GameUiState(
            isWon = true, showWinDialog = true, revealedSecret = "321",
            guesses = listOf(GuessRules.evaluate("321", "321")),
        ))
        var opens = 0
        var replays = 0
        compose.setContent { NumbCrunchTheme {
            GameScreen(
                uiState = state.value, onBack = {}, onHelp = {}, onSettings = {},
                onQuit = {}, onConfirmQuit = {}, onDismissQuit = {}, onQuitRevealDone = {},
                onDigit = {}, onDelete = {}, onEnter = {}, onHint = {}, onConfirmExit = {},
                onDismissExit = {}, onPlayAgain = { replays++ }, onWinHome = {},
                onDismissWinDialog = {}, onLeaderboardClick = { opens++ },
            )
        } }
        val title = ApplicationProvider.getApplicationContext<Context>().getString(R.string.hard_board_title)
        for (mode in PlayerMode.entries) for (difficulty in Difficulty.entries) {
            compose.runOnIdle { state.value = state.value.copy(playerMode = mode, difficulty = difficulty) }
            if (mode == PlayerMode.SINGLE && difficulty == Difficulty.HARD) {
                compose.onNodeWithText(title).assertIsDisplayed().performClick()
            } else compose.onNodeWithText(title).assertDoesNotExist()
        }
        assertEquals(1, opens)
        assertEquals(0, replays)
    }
}
