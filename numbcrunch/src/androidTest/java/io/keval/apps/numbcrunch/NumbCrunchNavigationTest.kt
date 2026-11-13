package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.repository.TutorialRepository
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.navigation.GAMEPLAY_INFO_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.GAME_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.HOME_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.TUTORIAL_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.WELCOME_ROUTE
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
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
