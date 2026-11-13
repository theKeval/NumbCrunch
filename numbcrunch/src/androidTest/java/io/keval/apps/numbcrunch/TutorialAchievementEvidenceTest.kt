package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.tutorial.TutorialEvidence
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.tutorial.TutorialScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TutorialAchievementEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun viewingEveryPageAndSolvingPracticeProducesEvidenceOnlyOnFinalAction() {
        var finalEvidence: TutorialEvidence? = null
        compose.setContent { NumbCrunchTheme {
            TutorialScreen({}, {}, onFinishTutorial = { finalEvidence = it })
        } }
        compose.onNodeWithText(context.getString(R.string.tutorial_next)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.tutorial_dot_description, 2, 3)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tutorial_next)).performClick()
        solvePractice()
        compose.runOnIdle { assertNull(finalEvidence) }
        compose.onNodeWithText(context.getString(R.string.tutorial_play)).performClick()
        compose.runOnIdle { assertNotNull(finalEvidence?.completion("owner", 10)) }
    }

    @Test fun jumpingPastCluePageDoesNotAwardEvenWhenPracticeIsSolved() {
        var finalEvidence: TutorialEvidence? = null
        compose.setContent { NumbCrunchTheme {
            TutorialScreen({}, {}, onFinishTutorial = { finalEvidence = it })
        } }
        compose.onNodeWithContentDescription(context.getString(R.string.tutorial_dot_description, 3, 3)).performClick()
        solvePractice()
        compose.onNodeWithText(context.getString(R.string.tutorial_play)).performClick()
        compose.runOnIdle {
            assertNotNull(finalEvidence)
            assertNull(finalEvidence?.completion("owner", 10))
        }
    }

    @Test fun firstRunSkipDismissesWithoutCompletionEvidence() {
        var dismissed = false
        var awarded = false
        compose.setContent { NumbCrunchTheme {
            TutorialScreen({}, {}, isFirstRun = true, onCompleteFirstRun = { dismissed = true },
                onFinishTutorial = { awarded = true })
        } }
        compose.onNodeWithText(context.getString(R.string.tutorial_skip)).performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(awarded) }
    }

    private fun solvePractice() {
        "245".forEach { digit ->
            compose.onNodeWithContentDescription(context.getString(R.string.cd_key_digit, digit.toString()))
                .performScrollTo().performClick()
        }
        compose.onNodeWithText(context.getString(R.string.key_enter)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.tutorial_practice_win)).performScrollTo().assertIsDisplayed()
    }
}
