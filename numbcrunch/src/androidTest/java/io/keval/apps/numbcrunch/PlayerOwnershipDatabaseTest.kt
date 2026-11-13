package io.keval.apps.numbcrunch

import androidx.test.platform.app.InstrumentationRegistry
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PlayerOwnershipDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "ownership-test-${UUID.randomUUID()}.db"
    private var repository = SqliteProgressRepository(context, name) { 10 }
    @After fun cleanUp() { repository.close(); context.deleteDatabase(name) }

    @Test fun accountSelectionAndGuestChoiceSurviveReopenAndReconnect() = runBlocking {
        val guest = repository.selectedProfile()
        val a = repository.activatePlayerProfile("a")
        assertEquals(a, repository.selectedProfile())
        repository.selectProfile(guest.id)
        repository.close()
        repository = SqliteProgressRepository(context, name)
        repository.activatePlayerProfile("a")
        assertEquals(guest, repository.selectedProfile())
        val b = repository.activatePlayerProfile("b")
        assertEquals(b, repository.selectedProfile())
        repository.activatePlayerProfile("a")
        assertEquals(a, repository.selectedProfile())
    }

    @Test fun claimKeepsBothUnfinishedRoundsAndImmutableOwnersExactlyOnce() = runBlocking {
        val guest = repository.guestProfile()
        val a = repository.activatePlayerProfile("a")
        val b = repository.profileForPlayer("b")
        val guestRound = repository.createRound(round("guest-round", guest.id))
        val accountRound = repository.createRound(round("account-round", a.id))
        val claim = repository.claimGuestProgress(guest.id, a.id)
        assertEquals(claim, repository.claimGuestProgress(guest.id, a.id))
        assertTrue(runCatching { repository.claimGuestProgress(guest.id, b.id) }.isFailure)
        assertEquals(setOf(guestRound, accountRound), repository.unfinishedRounds(a.id).toSet())
        assertTrue(runCatching { repository.createRound(round("new", a.id)) }.isFailure)
        assertTrue(runCatching { repository.selectProfile(guest.id) }.isFailure)
        assertNull(repository.findRound(b.id, guestRound.id))
        assertEquals(guestRound, repository.findRound(a.id, guestRound.id))
        val updated = repository.updateRound(guestRound.editInput("9", 11), guestRound.revision)
        assertEquals(guest.id, updated.ownerId)
        repository.close()
        repository = SqliteProgressRepository(context, name)
        val snapshot = repository.exportProgress(a.id)
        assertEquals(listOf(claim), snapshot.guestClaims)
        assertEquals(setOf(a.id, guest.id), snapshot.ownerIds)
        assertEquals(2, snapshot.rounds.size)
        assertEquals(0L, snapshot.statistics.played)
        assertNotEquals(guest.id, repository.guestProfile().id)
    }

    @Test fun claimFailureRollsBackThenRetryPreservesGuestFacts() = runBlocking {
        val guest = repository.guestProfile()
        val a = repository.profileForPlayer("a")
        repository.createRound(round("guest-round", guest.id))
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TRIGGER fail_claim BEFORE INSERT ON guest_claims BEGIN SELECT RAISE(ABORT, 'injected claim failure'); END")
        }
        assertTrue(runCatching { repository.claimGuestProgress(guest.id, a.id) }.isFailure)
        assertTrue(repository.exportProgress(a.id).guestClaims.isEmpty())
        assertEquals(1, repository.exportProgress(guest.id).rounds.size)
        context.openOrCreateDatabase(name, 0, null).use { it.execSQL("DROP TRIGGER fail_claim") }
        repository.claimGuestProgress(guest.id, a.id)
        assertEquals(1, repository.exportProgress(a.id).rounds.size)
    }

    @Test fun migrationFromVersionOneRetainsProfilesAndRound() = runBlocking {
        val guest = repository.guestProfile()
        val saved = repository.createRound(round("saved", guest.id))
        repository.close()
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("DROP TABLE guest_claims")
            db.version = 1
        }
        repository = SqliteProgressRepository(context, name)
        assertEquals(guest, repository.selectedProfile())
        assertEquals(saved, repository.findRound(guest.id, saved.id))
        val a = repository.activatePlayerProfile("a")
        repository.claimGuestProgress(guest.id, a.id)
        assertEquals(saved, repository.findRound(a.id, saved.id))
    }

    private fun round(id: String, ownerId: String) = RoundRecord(id, ownerId, RoundMode.SINGLE, Difficulty.HARD, "123", 10)
}
