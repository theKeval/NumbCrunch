package io.keval.apps.numbcrunch.gameplay.statistics

import androidx.lifecycle.SavedStateHandle
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.gameplay.game.TestProgressRepository
import kotlinx.coroutines.Dispatchers
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
class StatisticsViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun record(id: String, mode: RoundMode = RoundMode.SINGLE, difficulty: Difficulty = Difficulty.HARD, owner: String = "guest") =
        RoundRecord(id, owner, mode, difficulty, "012", 1)
    private fun win(id: String, count: Int) = (1 until count).fold(record(id)) { round, _ ->
        round.editInput("345", 2).submitGuess(2)
    }.editInput("012", 3).submitGuess(3)

    @Test fun `weighted winning average and ongoing count come from stored rounds`() {
        val repository = TestProgressRepository()
        repository.records.value = listOf(win("a", 4), win("b", 7), win("c", 10), record("paused").pause(4))
        val vm = StatisticsViewModel(repository, SavedStateHandle())
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(3L, vm.uiState.value.statistics.wins)
        assertEquals(21L, vm.uiState.value.statistics.winningGuessSum)
        assertEquals(7.0, vm.uiState.value.statistics.averageWinningGuesses!!, 0.0)
        assertEquals(1L, vm.uiState.value.ongoing)
        assertEquals(3L, vm.uiState.value.statistics.played)
    }

    @Test fun `combined filters use the same denominator and survive restoration`() {
        val repository = TestProgressRepository()
        repository.records.value = RoundMode.entries.flatMap { mode -> Difficulty.entries.map { difficulty ->
            record("$mode-$difficulty", mode, difficulty).editInput("012", 2).submitGuess(3)
        } } + record("paused").editInput("345", 2).submitGuess(3).pause(4)
        val handle = SavedStateHandle()
        val vm = StatisticsViewModel(repository, handle)
        vm.selectMode(RoundMode.SINGLE)
        vm.selectDifficulty(Difficulty.HARD)
        assertEquals(2L, vm.uiState.value.statistics.played)
        assertEquals(1L, vm.uiState.value.statistics.resolved)
        assertEquals(1.0, vm.uiState.value.statistics.winRate!!, 0.0)
        assertEquals(1L, vm.uiState.value.ongoing)
        val restored = StatisticsViewModel(repository, handle)
        assertEquals(vm.uiState.value, restored.uiState.value)
        vm.selectMode(RoundMode.DOUBLE)
        assertEquals(1L, vm.uiState.value.statistics.played)
        assertEquals(0L, vm.uiState.value.ongoing)
        vm.selectDifficulty(null); vm.selectMode(null)
        assertEquals(7L, vm.uiState.value.statistics.played)
    }

    @Test fun `statistics refresh after outcomes without callback accounting`() {
        val repository = TestProgressRepository()
        val vm = StatisticsViewModel(repository, SavedStateHandle())
        assertNull(vm.uiState.value.statistics.averageWinningGuesses)
        assertNull(vm.uiState.value.statistics.winRate)
        val started = record("giveup").editInput("345", 2).submitGuess(3)
        repository.records.value = listOf(win("win", 1), started.finish(RoundStatus.GIVEN_UP, 4),
            started.copy(id = "abandon").finish(RoundStatus.ABANDONED, 4), started.copy(id = "pause").pause(4))
        assertEquals(4L, vm.uiState.value.statistics.played)
        assertEquals(3L, vm.uiState.value.statistics.resolved)
        assertEquals(1.0 / 3, vm.uiState.value.statistics.winRate!!, 0.0)
        assertEquals(1L, vm.uiState.value.ongoing)
    }

    @Test fun `profile switch and explicit guest claim update visible totals once`() = runBlocking {
        val repository = TestProgressRepository()
        repository.records.value = listOf(win("guest-win", 4))
        val vm = StatisticsViewModel(repository, SavedStateHandle())
        assertEquals(1L, vm.uiState.value.statistics.wins)
        val account = repository.activatePlayerProfile("player")
        assertEquals(account.id, vm.uiState.value.profile?.id)
        assertEquals(0L, vm.uiState.value.statistics.wins)
        repository.claimGuestProgress("guest", account.id)
        assertEquals(1L, vm.uiState.value.statistics.wins)
        assertEquals(4.0, vm.uiState.value.statistics.averageWinningGuesses!!, 0.0)
        vm.refresh()
        assertEquals(1L, vm.uiState.value.statistics.wins)
    }

    @Test fun `read failure stays retryable and does not expose zero totals as success`() {
        val repository = TestProgressRepository().apply { failReads = true }
        val vm = StatisticsViewModel(repository, SavedStateHandle())
        assertTrue(vm.uiState.value.hasError)
        vm.selectMode(RoundMode.DOUBLE)
        assertTrue(vm.uiState.value.hasError)
        repository.failReads = false
        vm.refresh()
        assertFalse(vm.uiState.value.hasError)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(StatisticsFilter(RoundMode.DOUBLE), vm.uiState.value.filter)
    }
}
