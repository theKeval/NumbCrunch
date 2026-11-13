package io.keval.apps.numbcrunch

import androidx.test.platform.app.InstrumentationRegistry
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GameStatsTransactionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val aName = "outbox-a-${UUID.randomUUID()}.db"
    private val bName = "outbox-b-${UUID.randomUUID()}.db"
    private val a = SqliteProgressRepository(context, aName) { 1 }
    private val b = SqliteProgressRepository(context, bName) { 1 }
    @After fun cleanUp() { a.close(); b.close(); context.deleteDatabase(aName); context.deleteDatabase(bName) }

    @Test fun terminalAndOutboxCommitOrRollbackTogetherAndReplayIsDeduplicated() = runBlocking {
        val profile = a.activatePlayerProfile("player")
        val start = a.createRound(round(profile.id))
        val entered = a.updateRound(start.editInput("123", 2), start.revision)
        context.openOrCreateDatabase(aName, 0, null).use {
            it.execSQL("CREATE TRIGGER reject_event BEFORE INSERT ON game_stats_outbox BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        }
        assertTrue(runCatching { a.updateRound(entered.submitGuess(3), entered.revision) }.isFailure)
        assertEquals(entered, a.findRound(profile.id, start.id))
        assertTrue(a.observePending("player").first().isEmpty())
        context.openOrCreateDatabase(aName, 0, null).use { it.execSQL("DROP TRIGGER reject_event") }
        val won = a.updateRound(entered.submitGuess(3), entered.revision)
        a.updateRound(won, entered.revision)
        assertEquals(1, a.observePending("player").first().size)
        assertTrue(a.observePending("another-player").first().isEmpty())
    }

    @Test fun importedRoundCanFinishAndCountLocallyWithoutReplayingPlatformEvent() = runBlocking {
        val source = a.activatePlayerProfile("player")
        val target = b.activatePlayerProfile("player")
        val start = a.createRound(round(source.id))
        val paused = a.updateRound(start.pause(2), start.revision)
        b.importResolvedProgress("player", a.exportCloudProgress("player"), paused.id)
        val resumed = b.updateRound(paused.resume(3), paused.revision)
        val entered = b.updateRound(resumed.editInput("123", 4), resumed.revision)
        b.updateRound(entered.submitGuess(5), entered.revision)
        assertEquals(1L, b.exportProgress(target.id).statistics.wins)
        assertTrue(b.observePending("player").first().isEmpty())
        assertTrue(a.observePending("player").first().isEmpty())
    }

    private fun round(owner: String) = RoundRecord("round", owner, RoundMode.SINGLE, Difficulty.HARD, "123", 1)
}
