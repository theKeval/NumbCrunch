package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.domain.repository.TutorialRepository
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.navigation.GAMEPLAY_INFO_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.GAME_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.HOME_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.TUTORIAL_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.WELCOME_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.STATISTICS_ROUTE
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test

class NumbCrunchNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "navigation-${UUID.randomUUID()}.db"
    private val progressRepository = SqliteProgressRepository(context, databaseName)
    private lateinit var navController: NavHostController

    @After fun closeProgress() {
        progressRepository.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun completedIntroductionOpensHome() {
        launch(FakeTutorialRepository(completed = true))

        awaitRoute(HOME_ROUTE)
        compose.runOnIdle { assertNull(navController.previousBackStackEntry) }
    }

    @Test
    fun tutorialReplacesWelcomeAndSkipPersistsBeforeHome() {
        val repository = FakeTutorialRepository()
        launch(repository)
        awaitRoute(WELCOME_ROUTE)
        compose.onNodeWithText(context.getString(R.string.welcome_view_tutorial))
            .performScrollTo().performClick()

        awaitRoute(TUTORIAL_ROUTE)
        compose.runOnIdle { assertNull(navController.previousBackStackEntry) }
        compose.onNodeWithText(context.getString(R.string.tutorial_skip)).performClick()

        awaitRoute(HOME_ROUTE)
        compose.runOnIdle {
            assertTrue(repository.hasCompletedFirstRun.value)
            assertEquals(1, repository.completionCalls)
            assertNull(navController.previousBackStackEntry)
        }
    }

    @Test
    fun failedWelcomeSaveStaysRetryable() {
        val repository = FakeTutorialRepository().apply { failSave = true }
        launch(repository)
        awaitRoute(WELCOME_ROUTE)
        compose.onNodeWithText(context.getString(R.string.welcome_go_home))
            .performScrollTo().performClick()

        compose.onNodeWithText(context.getString(R.string.tutorial_completion_error))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.welcome_view_tutorial)).assertIsEnabled()
        compose.runOnIdle {
            assertEquals(WELCOME_ROUTE, navController.currentDestination?.route)
            repository.failSave = false
        }
        compose.onNodeWithText(context.getString(R.string.welcome_go_home))
            .performScrollTo().performClick()

        awaitRoute(HOME_ROUTE)
        compose.runOnIdle {
            assertTrue(repository.hasCompletedFirstRun.value)
            assertEquals(2, repository.completionCalls)
            assertNull(navController.previousBackStackEntry)
        }
    }

    @Test
    fun restoringDuringPendingSaveDoesNotLeaveWelcomeBusy() {
        val repository = FakeTutorialRepository().apply { saveGate = CompletableDeferred() }
        val restoration = launch(repository)
        awaitRoute(WELCOME_ROUTE)
        compose.onNodeWithText(context.getString(R.string.welcome_go_home))
            .performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.welcome_view_tutorial)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.welcome_go_home)).assertIsNotEnabled()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(context.getString(R.string.welcome_go_home)).assertIsEnabled()
        compose.runOnIdle { repository.saveGate = null }
        compose.onNodeWithText(context.getString(R.string.welcome_go_home))
            .performScrollTo().performClick()
        awaitRoute(HOME_ROUTE)
        compose.runOnIdle { assertEquals(2, repository.completionCalls) }
    }

    @Test
    fun restoredWelcomeSkipsOnboardingWhenCompletionWasAlreadyWritten() {
        val repository = FakeTutorialRepository()
        val restoration = launch(repository)
        awaitRoute(WELCOME_ROUTE)
        compose.runOnIdle { repository.hasCompletedFirstRun.value = true }

        restoration.emulateSavedInstanceStateRestore()

        awaitRoute(HOME_ROUTE)
        compose.runOnIdle { assertNull(navController.previousBackStackEntry) }
    }

    @Test
    fun selectedDifficultyAndInputSurviveHelp() {
        launch(FakeTutorialRepository(completed = true))
        awaitRoute(HOME_ROUTE)
        compose.onNodeWithText(context.getString(R.string.difficulty_medium))
            .performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_start_game))
            .performScrollTo().performClick()
        awaitRoute(GAME_ROUTE)
        val gameEntry = compose.runOnIdle { navController.currentBackStackEntry }
        compose.onNodeWithText(context.getString(R.string.difficulty_medium)).assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription(context.getString(R.string.cd_key_digit, "1")) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(context.getString(R.string.cd_key_digit, "1")).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.cd_active_row, 1, "1"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(context.getString(R.string.cd_help)).performClick()
        awaitRoute(GAMEPLAY_INFO_ROUTE)

        compose.onNodeWithContentDescription(context.getString(R.string.guide_back)).performClick()

        awaitRoute(GAME_ROUTE)
        compose.runOnIdle { assertEquals(gameEntry, navController.currentBackStackEntry) }
        compose.onNodeWithText(context.getString(R.string.difficulty_medium)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_active_row, 1, "1"))
            .assertIsDisplayed()
    }

    @Test
    fun pausedRoundResumesWithInputAndHintAfterHomeRestoration() {
        val saved = seedPausedRound()
        val restoration = launch(FakeTutorialRepository(completed = true))
        awaitRoute(HOME_ROUTE)
        awaitResumeCard()
        compose.onNodeWithText("012").assertDoesNotExist()
        compose.onNodeWithContentDescription("0 1 2").assertDoesNotExist()
        resumeCard().performScrollTo().performClick()
        awaitRoute(GAME_ROUTE)
        awaitInput("6")
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.game_hints_remaining, 0, 0)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_back_home)).performClick()
        compose.onNodeWithText(context.getString(R.string.progress_save_pause)).performScrollTo().performClick()
        awaitRoute(HOME_ROUTE)
        assertEquals(RoundStatus.PAUSED, runBlocking { progressRepository.findRound(saved.ownerId, saved.id) }?.status)
        restoration.emulateSavedInstanceStateRestore()
        awaitRoute(HOME_ROUTE)
        awaitResumeCard()
        resumeCard().performScrollTo().performClick()
        awaitRoute(GAME_ROUTE)
        awaitInput("6")
        val restored = runBlocking { progressRepository.findRound(saved.ownerId, saved.id) }!!
        assertEquals(saved.secret, restored.secret)
        assertEquals(saved.guesses, restored.guesses)
        assertEquals(saved.hintedGuessIndex, restored.hintedGuessIndex)
        assertEquals(0L, runBlocking { progressRepository.exportProgress(saved.ownerId) }.statistics.resolved)
    }

    @Test
    fun startingOverRequiresAnExplicitChoiceAndCancelPreservesSavedGame() {
        val saved = seedPausedRound()
        launch(FakeTutorialRepository(completed = true))
        awaitRoute(HOME_ROUTE)
        awaitResumeCard()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_start_game)).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(context.getString(R.string.progress_existing_title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.progress_abandon_this)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.progress_abandon_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.progress_cancel)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.progress_cancel)).performScrollTo().performClick()
        awaitRoute(HOME_ROUTE)
        assertEquals(saved, runBlocking { progressRepository.findRound(saved.ownerId, saved.id) })
        assertEquals(1, runBlocking { progressRepository.exportProgress(saved.ownerId) }.rounds.size)
    }

    @Test
    fun statisticsFiltersAndBackLeaveTheSavedRoundUntouched() {
        val saved = seedPausedRound()
        launch(FakeTutorialRepository(completed = true))
        awaitRoute(HOME_ROUTE)
        compose.onNodeWithContentDescription(context.getString(R.string.statistics_title)).performScrollTo().performClick()
        awaitRoute(STATISTICS_ROUTE)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(context.getString(R.string.statistics_all_modes)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(context.getString(R.string.statistics_ongoing)) and hasText("1"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.label_single_player)).performScrollTo().performClick()
        compose.onNode(hasText(context.getString(R.string.statistics_played)) and hasText("0"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.statistics_all_modes)).performScrollTo().performClick()
        compose.onNode(hasText(context.getString(R.string.statistics_played)) and hasText("1"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.guide_back)).performClick()
        awaitRoute(HOME_ROUTE)
        assertEquals(saved, runBlocking { progressRepository.findRound(saved.ownerId, saved.id) })
        assertEquals(1, runBlocking { progressRepository.exportProgress(saved.ownerId) }.rounds.size)
    }

    private fun seedPausedRound(): RoundRecord = runBlocking {
        val owner = progressRepository.selectedProfile()
        val saved = RoundRecord("saved-ui-round", owner.id, RoundMode.DOUBLE, Difficulty.MEDIUM, "012", 1)
            .editInput("345", 2).submitGuess(3).useHint(0, 4).editInput("6", 5).pause(6)
        progressRepository.importRounds(owner.id, listOf(saved))
        saved
    }

    private fun resumeDescription() = context.getString(R.string.progress_resume_description,
        context.getString(R.string.label_double_player), context.getString(R.string.difficulty_medium))

    private fun resumeCard() = compose.onNodeWithContentDescription(resumeDescription())

    private fun awaitResumeCard() = compose.waitUntil(timeoutMillis = 5_000) {
        compose.onAllNodesWithContentDescription(resumeDescription()).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitInput(input: String) = compose.waitUntil(timeoutMillis = 5_000) {
        compose.onAllNodesWithContentDescription(context.getString(R.string.cd_active_row, 2, input)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun launch(repository: FakeTutorialRepository): StateRestorationTester {
        val container = AppContainer(context, repository, progressRepository)
        return StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                NumbCrunchTheme {
                    navController = rememberNavController()
                    NumbCrunchNavHost(navController, container)
                }
            }
        }
    }

    private fun awaitRoute(route: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            ::navController.isInitialized && navController.currentDestination?.route == route
        }
        compose.waitForIdle()
    }

    private class FakeTutorialRepository(completed: Boolean = false) : TutorialRepository {
        override val hasCompletedFirstRun = MutableStateFlow(completed)
        var failSave = false
        var saveGate: CompletableDeferred<Unit>? = null
        var completionCalls = 0

        override suspend fun markFirstRunComplete() {
            completionCalls++
            saveGate?.await()
            if (failSave) throw IOException("Test save failure")
            hasCompletedFirstRun.value = true
        }
    }
}
