package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.game.GameRoute
import io.keval.apps.numbcrunch.gameplay.game.GameScreen
import io.keval.apps.numbcrunch.gameplay.game.GameUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.After
import java.util.UUID
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import org.junit.Test

class GameFeedbackTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "feedback-${UUID.randomUUID()}.db"
    private val progressRepository = SqliteProgressRepository(context, databaseName)
    private var submittedRows = 0
    @After fun closeProgress() { progressRepository.close(); context.deleteDatabase(databaseName) }
    private val clues: List<String> get() = listOf(
        clue("5", R.string.clue_exact),
        clue("2", R.string.clue_absent),
        clue("7", R.string.clue_close),
    )

    @Test
    fun easyRevealsSpokenCluesOnlyAfterSubmission() {
        launch(Difficulty.EASY)
        typeGuess()
        assertClueCount(0)

        compose.onNodeWithContentDescription(context.getString(R.string.cd_key_enter)).performClick()

        awaitClueCount(1)
        clues.forEach { description ->
            compose.onNodeWithContentDescription(rowDescription(1))
                .assert(hasContentDescription(description))
        }
        assertNoTileMarkers()
    }

    @Test
    fun hardKeepsDigitCluesOutOfBothSemanticsTrees() {
        launch(Difficulty.HARD)
        submitGuess()

        assertClueCount(0)
        clues.forEach { description ->
            compose.onNodeWithContentDescription(rowDescription(1))
                .assert(!hasContentDescription(description))
        }
    }

    @Test
    fun mediumRevealsOnlyTheChosenOccurrenceAndRestoresSpentHint() {
        val restoration = launch(Difficulty.MEDIUM)
        submitGuess()
        submitGuess()
        assertClueCount(0)
        compose.onNodeWithText("i", useUnmergedTree = true).assertDoesNotExist()

        val remark = context.getString(R.string.cd_remark_info, "1R, 1W")
        compose.onAllNodesWithContentDescription(remark)[1].performTouchInput { click() }
        val dismiss = compose.onNodeWithContentDescription(context.getString(R.string.remark_info_dismiss))
        dismiss.assertIsDisplayed().performClick()
        assertClueCount(0)
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.game_hints_remaining, 1, 1))
            .assertIsDisplayed()

        compose.onNodeWithContentDescription(context.getString(R.string.cd_game_hint, 2))
            .performTouchInput { click() }

        awaitClueCount(1)
        assertSecondRowOnly()
        assertNoHintActions()
        assertNoTileMarkers()

        restoration.emulateSavedInstanceStateRestore()

        awaitClueCount(1)
        assertSecondRowOnly()
        assertNoHintActions()
    }

    @Test
    fun mediumKeepsTheSameDigitAndRowSizesAsEasyAndHard() {
        val difficulty = mutableStateOf(Difficulty.EASY)
        compose.setContent {
            NumbCrunchTheme {
                Box(Modifier.width(320.dp)) {
                    GameScreen(
                        uiState = GameUiState(difficulty = difficulty.value, currentInput = "527"),
                        onBack = {},
                        onHelp = {},
                        onSettings = {},
                        onQuit = {},
                        onConfirmQuit = {},
                        onDismissQuit = {},
                        onQuitRevealDone = {},
                        onDigit = {},
                        onDelete = {},
                        onEnter = {},
                        onHint = {},
                        onConfirmExit = {},
                        onDismissExit = {},
                        onPlayAgain = {},
                        onWinHome = {},
                        onDismissWinDialog = {},
                    )
                }
            }
        }

        val activeRow = hasContentDescription(context.getString(R.string.cd_active_row, 1, "5 2 7"))
        val digit = hasText("5") and hasAnyAncestor(activeRow)
        val easyDigit = compose.onNode(digit, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.size
        val easyRow = compose.onNode(activeRow).fetchSemanticsNode().boundsInRoot.size

        for (mode in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            compose.runOnIdle { difficulty.value = mode }
            assertEquals(easyDigit, compose.onNode(digit, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.size)
            assertEquals(easyRow, compose.onNode(activeRow).fetchSemanticsNode().boundsInRoot.size)
        }
    }

    private fun launch(difficulty: Difficulty): StateRestorationTester =
        StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                NumbCrunchTheme {
                    GameRoute(
                        progressRepository = progressRepository,
                        friendSecretNumber = "573",
                        difficulty = difficulty,
                        onExitToHome = {},
                        onHelp = {},
                        onSettings = {},
                    )
                }
            }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasContentDescription(context.getString(R.string.cd_key_digit, "5")) and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }

    private fun typeGuess() {
        "527".forEach { digit ->
            compose.onNodeWithContentDescription(context.getString(R.string.cd_key_digit, digit.toString()))
                .performClick()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.cd_active_row, submittedRows + 1, "5 2 7"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun submitGuess() {
        typeGuess()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_key_enter)).performClick()
        submittedRows += 1
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription(rowDescription(submittedRows)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun clue(digit: String, meaning: Int): String =
        context.getString(R.string.guide_digit_description, digit, context.getString(meaning))

    private fun rowDescription(row: Int): String = context.getString(R.string.cd_guess_row, row, "5 2 7", "1R, 1W")

    private fun awaitClueCount(count: Int) {
        compose.waitUntil(timeoutMillis = 5_000) {
            clues.all { description ->
                compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == count
            }
        }
        assertClueCount(count)
    }

    private fun assertClueCount(count: Int) {
        clues.forEach { description ->
            compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .assertCountEquals(count)
            compose.onAllNodesWithContentDescription(description).assertCountEquals(count)
        }
    }

    private fun assertSecondRowOnly() {
        clues.forEach { description ->
            compose.onNodeWithContentDescription(rowDescription(1)).assert(!hasContentDescription(description))
            compose.onNodeWithContentDescription(rowDescription(2)).assert(hasContentDescription(description))
        }
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.game_hints_remaining, 0, 0))
            .assertIsDisplayed()
    }

    private fun assertNoHintActions() {
        compose.onNodeWithText("i", useUnmergedTree = true).assertDoesNotExist()
        listOf(1, 2).forEach { row ->
            compose.onAllNodesWithContentDescription(context.getString(R.string.cd_game_hint, row))
                .assertCountEquals(0)
        }
    }

    private fun assertNoTileMarkers() {
        listOf("R", "W", "×").forEach { marker ->
            compose.onNodeWithText(marker, useUnmergedTree = true).assertDoesNotExist()
        }
    }
}
