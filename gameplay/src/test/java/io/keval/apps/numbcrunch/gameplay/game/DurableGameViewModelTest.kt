package io.keval.apps.numbcrunch.gameplay.game

import androidx.lifecycle.SavedStateHandle
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.PlayerStatistics
import io.keval.apps.numbcrunch.domain.progress.RoundStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DurableGameViewModelTest {
    private val repository = TestProgressRepository()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        GameViewModel(handle, null, Difficulty.HARD, repository, now = { 10 }) { "573" }

    @Test fun `initial persistence failure permits home and retries the same new round`() {
        repository.failNextWrite = true
        var generated = 0
        val vm = GameViewModel(SavedStateHandle(), null, Difficulty.HARD, repository, now = { 10 }) {
            generated += 1
            "573"
        }
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.hasPersistenceError)
        assertTrue(vm.onBackRequested())
        vm.onRetryPersistence()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.hasPersistenceError)
        assertEquals(1, generated)
        assertEquals(1, repository.records.value.size)
    }

    @Test fun `first guess failure keeps input and retry counts exactly once`() {
        val vm = vm()
        "527".forEach(vm::onDigit)
        repository.failNextWrite = true
        vm.onEnter()
        assertTrue(vm.uiState.value.hasPersistenceError)
        assertEquals("527", vm.uiState.value.currentInput)
        assertEquals(0L, PlayerStatistics.fromRounds(repository.records.value).played)
        vm.onRetryPersistence()
        assertEquals(1L, PlayerStatistics.fromRounds(repository.records.value).played)
        assertEquals(1, vm.uiState.value.guesses.size)
        vm.onRetryPersistence()
        assertEquals(1, vm.uiState.value.guesses.size)
    }

    @Test fun `failed leave does not navigate or lose the round`() {
        val vm = vm()
        "527".forEach(vm::onDigit)
        vm.onEnter()
        repository.failNextWrite = true
        var navigated = false
        vm.onConfirmExit { navigated = true }
        assertFalse(navigated)
        assertEquals(RoundStatus.ACTIVE, repository.records.value.single().status)
        vm.onRetryPersistence()
        assertTrue(navigated)
        assertEquals(1L, PlayerStatistics.fromRounds(repository.records.value).abandoned)
    }

    @Test fun `database restores input without Android saved game facts`() {
        val handle = SavedStateHandle()
        vm(handle).apply { "527".forEach(::onDigit); onEnter(); onDigit('8') }
        val restored = vm()
        assertEquals("8", restored.uiState.value.currentInput)
        assertEquals(listOf("527"), restored.uiState.value.guesses.map { it.guess })
        assertFalse(handle.contains("game_secret"))
        assertFalse(handle.contains("game_guesses"))
        assertTrue(handle.contains("progress_round_id"))
    }

    @Test fun `win followed by quit counts only the win and replay gets a new id`() {
        val vm = vm()
        val originalId = vm.uiState.value.roundId
        "573".forEach(vm::onDigit)
        vm.onEnter(); vm.onConfirmQuit(); vm.onEnter()
        assertEquals(1L, PlayerStatistics.fromRounds(repository.records.value).wins)
        assertEquals(0L, PlayerStatistics.fromRounds(repository.records.value).giveUps)
        vm.onPlayAgain()
        assertNotEquals(originalId, vm.uiState.value.roundId)
        assertEquals(2, repository.records.value.size)
    }

    @Test fun `replay of restored single round ignores stale friend setup`() {
        vm()
        val restored = GameViewModel(SavedStateHandle(), "908", Difficulty.EASY, repository, now = { 10 }) { "573" }
        "573".forEach(restored::onDigit)
        restored.onEnter()
        restored.onPlayAgain()
        assertEquals(io.keval.apps.numbcrunch.gameplay.home.PlayerMode.SINGLE, restored.uiState.value.playerMode)
        assertEquals(Difficulty.HARD, restored.uiState.value.difficulty)
        "573".forEach(restored::onDigit)
        restored.onEnter()
        assertTrue(restored.uiState.value.isWon)
    }

    @Test fun `legacy dismissed win stays dismissed after import`() {
        val handle = SavedStateHandle(mapOf(
            "game_secret" to "573", "game_guesses" to arrayListOf("573"), "game_win_dialog" to false,
        ))
        val restored = vm(handle)
        assertTrue(restored.uiState.value.isWon)
        assertFalse(restored.uiState.value.showWinDialog)
        assertFalse(handle.contains("game_secret"))
        assertFalse(vm(handle).uiState.value.showWinDialog)
    }
}
