package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.game.GameRoute
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GameCloudRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = "game-cloud-recovery-${UUID.randomUUID()}.db"
    private val repository = SqliteProgressRepository(context, database)
    @After fun cleanUp() { repository.close(); context.deleteDatabase(database) }

    @Test fun changedRoundReloadsThenCompletedReceiptReturnsHomeWithoutAnotherOutcome() = runBlocking {
        val owner = repository.activatePlayerProfile("fixture-player")
        val created = repository.createRound(RoundRecord("fixture-round", owner.id, RoundMode.SINGLE, Difficulty.HARD, "123", 1))
        var exits = 0
        compose.setContent { NumbCrunchTheme {
            GameRoute(null, Difficulty.HARD, repository, { exits++ }, {}, {}, owner.id, created.id)
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription(context.getString(R.string.cd_key_digit, "1")) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        repository.updateRound(created.editInput("8", 2), created)
        compose.onNodeWithContentDescription(context.getString(R.string.cd_key_digit, "1")).performClick()
        awaitText(R.string.progress_reload_saved)
        compose.onNodeWithText(context.getString(R.string.progress_reload_saved)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.cd_active_row, 1, "8"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val active = requireNotNull(repository.findRound(owner.id, created.id))
        val cloud = repository.exportCloudProgress("fixture-player")
        val won = active.editInput("123", 3).submitGuess(4)
        repository.importResolvedProgress("fixture-player", cloud.copy(
            versions = listOf(cloud.versions.single().advance(won)),
        ), null)
        compose.onNodeWithContentDescription(context.getString(R.string.cd_key_digit, "1")).performClick()
        awaitText(R.string.progress_completed_elsewhere)
        compose.onNodeWithText(context.getString(R.string.progress_back_home)).performClick()
        compose.runOnIdle { assertEquals(1, exits) }
        assertNull(repository.findRound(owner.id, created.id))
        val statistics = repository.exportProgress(owner.id).statistics
        assertEquals(1L, statistics.wins)
        assertEquals(0L, statistics.abandoned)
    }

    private fun awaitText(resource: Int) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(resource)).fetchSemanticsNodes().isNotEmpty() }
    }
}
