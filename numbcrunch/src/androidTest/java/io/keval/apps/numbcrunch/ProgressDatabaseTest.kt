package io.keval.apps.numbcrunch

import androidx.test.platform.app.InstrumentationRegistry
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ProgressDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "progress-test-${UUID.randomUUID()}.db"
    private var repository = SqliteProgressRepository(context, name) { 1 }
    @After fun cleanUp() { repository.close(); context.deleteDatabase(name) }

    private suspend fun create(): RoundRecord {
        val owner = repository.guestProfile()
        return repository.createRound(RoundRecord(UUID.randomUUID().toString(), owner.id, RoundMode.SINGLE, Difficulty.MEDIUM, "573", 1))
    }

    @Test fun reopenRestoresExactRoundAndProfileWithoutInflatingTotals() = runBlocking {
        val created = create()
        val played = created.editInput("527", 2).submitGuess(2).useHint(0, 3).editInput("8", 4).pause(5)
        // Applying the validated steps individually is the normal app path.
        var saved = created
        for (next in listOf<(RoundRecord) -> RoundRecord>(
            { it.editInput("527", 2) }, { it.submitGuess(2) }, { it.useHint(0, 3) },
            { it.editInput("8", 4) }, { it.pause(5) },
        )) saved = repository.updateRound(next(saved), saved.revision)
        assertEquals(played, saved)
        repository.close()
        repository = SqliteProgressRepository(context, name)
        assertEquals(created.ownerId, repository.guestProfile().id)
        assertEquals(saved, repository.unfinishedRound(created.ownerId))
        repository.importRounds(created.ownerId, listOf(saved, saved))
        val snapshot = repository.exportProgress(created.ownerId)
        assertEquals(1, snapshot.rounds.size)
        assertEquals(1L, snapshot.statistics.played)
        assertEquals(1L, snapshot.statistics.hintsUsed)
        assertEquals(0L, snapshot.statistics.resolved)
    }

    @Test fun firstGuessFailureRollsBackAndRetryCommitsOnce() = runBlocking {
        val created = create()
        val input = repository.updateRound(created.editInput("527", 2), 0)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TRIGGER fail_guess BEFORE UPDATE ON rounds WHEN NEW.started_at IS NOT NULL BEGIN SELECT RAISE(ABORT, 'injected write failure'); END")
        }
        val submitted = input.submitGuess(3)
        assertTrue(runCatching { repository.updateRound(submitted, input.revision) }.isFailure)
        assertEquals(input, repository.findRound(input.ownerId, input.id))
        assertEquals(0L, repository.exportProgress(input.ownerId).statistics.played)
        context.openOrCreateDatabase(name, 0, null).use { it.execSQL("DROP TRIGGER fail_guess") }
        repository.updateRound(submitted, input.revision)
        repository.updateRound(submitted, input.revision)
        assertEquals(1L, repository.exportProgress(input.ownerId).statistics.played)
    }

    @Test fun winQuitRaceHasOneOutcomeAndOwnerIsolation() = runBlocking {
        val created = create()
        val input = repository.updateRound(created.editInput("573", 2), 0)
        val results = listOf(input.submitGuess(3), input.finish(RoundStatus.GIVEN_UP, 3)).map { candidate ->
            async { runCatching { repository.updateRound(candidate, input.revision) } }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        val records = repository.exportProgress(input.ownerId).rounds
        assertEquals(1, records.count { it.status.isTerminal })
        val associated = repository.profileForPlayer("opaque-platform-player")
        assertNotEquals(input.ownerId, associated.id)
        assertEquals(associated, repository.profileForPlayer("opaque-platform-player"))
        assertNull(repository.findRound(associated.id, input.id))
        assertTrue(repository.exportProgress(associated.id).rounds.isEmpty())
    }

    @Test fun conflictingImportRollsBackWholeBatchAndLegacyImportRunsOnce() = runBlocking {
        val owner = repository.guestProfile()
        val legacy = RoundRecord("legacy", owner.id, RoundMode.DOUBLE, Difficulty.HARD, "573", 1, origin = RoundOrigin.LEGACY)
            .editInput("527", 2).submitGuess(2).finish(RoundStatus.ABANDONED, 3)
        assertEquals(legacy, repository.importLegacyRound(legacy))
        assertNull(repository.importLegacyRound(legacy.copy(id = "second")))
        val imported = legacy.copy(id = "new", origin = RoundOrigin.LOCAL)
        assertTrue(runCatching { repository.importRounds(owner.id, listOf(imported, legacy.copy(difficulty = Difficulty.EASY))) }.isFailure)
        assertNull(repository.findRound(owner.id, "new"))
        assertEquals(1, repository.exportProgress(owner.id).rounds.size)
    }
}
