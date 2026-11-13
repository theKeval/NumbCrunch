package io.keval.apps.numbcrunch

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.cloud.*
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.gameplay.cloud.CloudBackupCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CloudBackupCardTest {
    @get:Rule val compose = createComposeRule()
    private val profile = PlayerProfile("profile", "account", 1)
    private val round = RoundRecord("round", profile.id, RoundMode.SINGLE, Difficulty.HARD, "246", 1)
    private val base = CloudRoundVersion.initial(round)
    private val conflict = CloudMerge.inspect(CloudProgress("account", listOf(profile), versions = listOf(
        base.advance(round.editInput("1", 2)), base.advance(round.editInput("2", 2)))))

    @Test fun pendingIsNotDisplayedAsSavedAndRetryWorks() {
        var retries = 0
        compose.setContent { NumbCrunchTheme {
            CloudBackupCard(CloudBackupState(BackupStatus.PENDING, "account", 1), { retries++ }, { _, _, _, _ -> }, { _, _, _ -> })
        } }
        compose.onNodeWithText("Offline or waiting to back up").assertIsDisplayed()
        compose.onNodeWithText("Saved", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Retry backup").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun cancellingConflictDoesNotChooseOrExposeSecretAndSwitchDismissesOldDialog() {
        val state = mutableStateOf(CloudBackupState(BackupStatus.NEEDS_ATTENTION, "account", 2, choice = conflict))
        var choices = 0
        compose.setContent { NumbCrunchTheme {
            CloudBackupCard(state.value, {}, { _, _, _, _ -> choices++ }, { _, _, _ -> choices++ })
        } }
        compose.onNodeWithText("Review saved progress").performClick()
        compose.onNodeWithText("Choose a version of this round").assertIsDisplayed()
        compose.onNodeWithText("246", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Decide later").performClick()
        compose.runOnIdle { assertEquals(0, choices) }
        compose.onNodeWithText("Review saved progress").performClick()
        compose.runOnIdle { state.value = CloudBackupState(BackupStatus.DEVICE_ONLY) }
        compose.onNodeWithText("Choose a version of this round").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, choices) }
    }
}
