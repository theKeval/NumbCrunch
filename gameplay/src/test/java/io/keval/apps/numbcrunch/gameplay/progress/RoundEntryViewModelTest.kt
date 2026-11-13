package io.keval.apps.numbcrunch.gameplay.progress

import androidx.lifecycle.SavedStateHandle
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.gameplay.game.GameViewModel
import io.keval.apps.numbcrunch.gameplay.game.TestProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoundEntryViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun entry(repository: TestProgressRepository) = RoundEntryViewModel(repository, now = { 10 }, generateSecret = { "012" })
    private fun game(repository: TestProgressRepository, destination: RoundDestination) =
        GameViewModel(SavedStateHandle(), null, Difficulty.HARD, repository, now = { 10 },
            initialOwnerId = destination.ownerId, initialRoundId = destination.roundId)

    @Test fun `all modes and difficulties pause and restore exact state without a loss`() {
        RoundMode.entries.forEach { mode -> Difficulty.entries.forEach { difficulty ->
            val repository = TestProgressRepository()
            val entry = entry(repository)
            entry.requestStart(NewRoundRequest(mode, difficulty, "012"))
            val destination = requireNotNull(entry.uiState.value.destination)
            entry.consumeDestination(destination)
            val game = game(repository, destination)
            "345".forEach(game::onDigit)
            game.onEnter()
            if (difficulty == Difficulty.MEDIUM) game.onHint(0)
            game.onDigit('6')
            var leaves = 0
            game.onSaveAndPause { leaves++ }
            game.onSaveAndPause { leaves++ }
            game.onDigit('7')
            assertEquals(1, leaves)
            assertNull(game.uiState.value.revealedSecret)
            val saved = repository.records.value.single()
            assertEquals(RoundStatus.PAUSED, saved.status)
            assertEquals("012", saved.secret)
            assertEquals("6", saved.currentInput)
            val before = PlayerStatistics.fromRounds(repository.records.value)
            assertEquals(1L, before.played)
            assertEquals(0L, before.resolved)
            entry.resume(entry.uiState.value.ongoingRounds.single())
            val restored = game(repository, requireNotNull(entry.uiState.value.destination))
            assertEquals(saved.id, restored.uiState.value.roundId)
            assertEquals(saved.ownerId, restored.uiState.value.ownerId)
            assertEquals(difficulty, restored.uiState.value.difficulty)
            assertEquals(listOf("345"), restored.uiState.value.guesses.map { it.guess })
            assertEquals(saved.hintedGuessIndex, restored.uiState.value.hintedGuessIndex)
            assertEquals("6", restored.uiState.value.currentInput)
            assertEquals(before, PlayerStatistics.fromRounds(repository.records.value))
        } }
    }

    @Test fun `zero guess saved game needs an explicit abandon before a new round`() {
        val repository = TestProgressRepository()
        val entry = entry(repository)
        entry.requestStart(NewRoundRequest(RoundMode.DOUBLE, Difficulty.MEDIUM, "012"))
        val first = requireNotNull(entry.uiState.value.destination)
        entry.consumeDestination(first)
        entry.requestStart(NewRoundRequest(RoundMode.SINGLE, Difficulty.EASY))
        assertNull(entry.uiState.value.destination)
        assertEquals(1, repository.records.value.size)
        val choice = entry.uiState.value.choices.single()
        assertFalse(choice.toString().contains("012"))
        entry.requestAbandon(choice)
        entry.cancelAbandon()
        assertEquals(RoundStatus.ACTIVE, repository.records.value.single().status)
        entry.requestAbandon(choice)
        entry.confirmAbandon()
        val second = requireNotNull(entry.uiState.value.destination)
        assertNotEquals(first.roundId, second.roundId)
        assertEquals(2, repository.records.value.size)
        assertEquals(RoundStatus.ABANDONED, repository.records.value.first().status)
        assertEquals(0L, PlayerStatistics.fromRounds(repository.records.value).played)
    }

    @Test fun `rapid start requests and failed create retry produce one round`() {
        val repository = TestProgressRepository()
        val entry = entry(repository)
        repository.failNextWrite = true
        val request = NewRoundRequest(RoundMode.SINGLE, Difficulty.HARD)
        entry.requestStart(request)
        entry.requestStart(request)
        assertTrue(entry.uiState.value.hasWriteError)
        assertNull(entry.uiState.value.destination)
        entry.retryWrite()
        entry.requestStart(request)
        assertEquals(1, repository.records.value.size)
        assertNotNull(entry.uiState.value.destination)
    }

    @Test fun `failed pause never navigates and can continue or retry`() {
        val repository = TestProgressRepository()
        val entry = entry(repository)
        entry.requestStart(NewRoundRequest(RoundMode.SINGLE, Difficulty.MEDIUM))
        val game = game(repository, requireNotNull(entry.uiState.value.destination))
        "345".forEach(game::onDigit); game.onEnter()
        repository.failNextWrite = true
        var navigated = false
        game.onSaveAndPause { navigated = true }
        assertFalse(navigated)
        assertEquals(RoundStatus.ACTIVE, repository.records.value.single().status)
        game.onDismissPersistenceError()
        assertFalse(game.uiState.value.hasPersistenceError)
        game.onDigit('6')
        repository.failNextWrite = true
        game.onSaveAndPause { navigated = true }
        game.onRetryPersistence()
        assertTrue(navigated)
        assertEquals(RoundStatus.PAUSED, repository.records.value.single().status)
        assertEquals("6", repository.records.value.single().currentInput)
    }

    @Test fun `retry after lost create acknowledgement opens the committed round`() {
        val repository = TestProgressRepository().apply { failAfterNextCreate = true }
        val entry = entry(repository)
        entry.requestStart(NewRoundRequest(RoundMode.SINGLE, Difficulty.HARD))
        val committed = repository.records.value.single()
        assertTrue(entry.uiState.value.hasWriteError)
        assertNull(entry.uiState.value.destination)
        entry.retryWrite()
        assertEquals(RoundDestination(committed.ownerId, committed.id), entry.uiState.value.destination)
        assertEquals(1, repository.records.value.size)
        assertTrue(entry.uiState.value.choices.isEmpty())
    }

    @Test fun `claim alternatives require individual choices and retain immutable owners`() = runBlocking {
        val repository = TestProgressRepository()
        val guest = repository.guestProfile()
        val account = repository.profileForPlayer("player")
        val first = RoundRecord("first", guest.id, RoundMode.SINGLE, Difficulty.HARD, "012", 1).pause(2)
        val second = RoundRecord("second", account.id, RoundMode.DOUBLE, Difficulty.EASY, "345", 1).pause(2)
        repository.records.value = listOf(first, second)
        repository.claimGuestProgress(guest.id, account.id)
        val entry = entry(repository)
        entry.requestStart(NewRoundRequest(RoundMode.SINGLE, Difficulty.MEDIUM))
        assertEquals(2, entry.uiState.value.choices.size)
        entry.requestAbandon(entry.uiState.value.choices.first { it.id == first.id })
        entry.confirmAbandon()
        assertNull(entry.uiState.value.destination)
        assertEquals(listOf(second.id), entry.uiState.value.choices.map { it.id })
        entry.resume(entry.uiState.value.choices.single())
        assertEquals(RoundDestination(account.id, second.id), entry.uiState.value.destination)
        assertEquals(listOf(guest.id, account.id), repository.records.value.map { it.ownerId })
        assertEquals(2, repository.records.value.size)
    }

    @Test fun `active round stays with original owner after profile switches and replay uses new selection`() = runBlocking {
        val repository = TestProgressRepository()
        val entry = entry(repository)
        entry.requestStart(NewRoundRequest(RoundMode.SINGLE, Difficulty.HARD))
        val destination = requireNotNull(entry.uiState.value.destination)
        entry.consumeDestination(destination)
        val game = game(repository, destination)
        val account = repository.activatePlayerProfile("other")
        "012".forEach(game::onDigit); game.onEnter()
        assertEquals(destination.ownerId, repository.records.value.single().ownerId)
        entry.requestStart(requireNotNull(game.replayRequest()))
        assertEquals(account.id, requireNotNull(entry.uiState.value.destination).ownerId)
        assertEquals(2, repository.records.value.size)
    }

    @Test fun `completed games cannot resume`() = runBlocking {
        val repository = TestProgressRepository()
        val entry = entry(repository)
        entry.requestStart(NewRoundRequest(RoundMode.SINGLE, Difficulty.HARD))
        val destination = requireNotNull(entry.uiState.value.destination)
        entry.consumeDestination(destination)
        val staleCard = entry.uiState.value.ongoingRounds.single()
        val game = game(repository, destination)
        "012".forEach(game::onDigit); game.onEnter()
        entry.resume(staleCard)
        assertTrue(entry.uiState.value.hasWriteError)
        assertNull(entry.uiState.value.destination)
        assertEquals(RoundStatus.WON, repository.records.value.single().status)
    }

    @Test fun `account switch read failure clears cards and rejects stale resume or abandon`() = runBlocking {
        val repository = TestProgressRepository()
        val accountA = repository.activatePlayerProfile("A")
        val saved = RoundRecord("saved-A", accountA.id, RoundMode.SINGLE, Difficulty.HARD, "012", 1).pause(2)
        repository.records.value = listOf(saved)
        val entry = entry(repository)
        val stale = entry.uiState.value.ongoingRounds.single()
        repository.failReads = true
        repository.activatePlayerProfile("B")
        assertTrue(entry.uiState.value.hasReadError)
        assertTrue(entry.uiState.value.ongoingRounds.isEmpty())
        assertTrue(entry.uiState.value.choices.isEmpty())
        entry.resume(stale)
        entry.requestAbandon(stale)
        entry.confirmAbandon()
        assertNull(entry.uiState.value.destination)
        assertNull(entry.uiState.value.abandonTarget)
        assertEquals(saved, repository.records.value.single())
        repository.failReads = false
        entry.refresh()
        entry.resume(stale)
        assertTrue(entry.uiState.value.hasWriteError)
        assertNull(entry.uiState.value.destination)
        assertEquals(saved, repository.records.value.single())
    }

    @Test fun `account switch cancels an in-flight saved-round action before navigation`() = runBlocking {
        val repository = TestProgressRepository()
        val accountA = repository.activatePlayerProfile("A")
        val saved = RoundRecord("saved-A", accountA.id, RoundMode.SINGLE, Difficulty.HARD, "012", 1).pause(2)
        repository.records.value = listOf(saved)
        val entry = entry(repository)
        val gate = CompletableDeferred<Unit>()
        repository.updateGate = gate
        entry.resume(entry.uiState.value.ongoingRounds.single())
        assertTrue(entry.uiState.value.isBusy)
        repository.activatePlayerProfile("B")
        gate.complete(Unit)
        assertNull(entry.uiState.value.destination)
        assertTrue(entry.uiState.value.ongoingRounds.isEmpty())
        assertFalse(entry.uiState.value.isBusy)
        assertEquals(saved, repository.records.value.single())
    }
}
