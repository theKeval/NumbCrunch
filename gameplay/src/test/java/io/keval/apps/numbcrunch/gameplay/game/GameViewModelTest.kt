package io.keval.apps.numbcrunch.gameplay.game

import androidx.lifecycle.SavedStateHandle
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.DigitFeedback
import io.keval.apps.numbcrunch.gameplay.home.PlayerMode
import io.keval.apps.numbcrunch.gameplay.progress.RoundEntryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import java.util.IdentityHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val repositories = IdentityHashMap<SavedStateHandle, TestProgressRepository>()
    @Before fun setUpDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        friendSecret: String? = null,
        difficulty: Difficulty = Difficulty.HARD,
        secrets: MutableList<String> = mutableListOf("573", "012"),
    ) = GameViewModel(handle, friendSecret, difficulty, repositories.getOrPut(handle) { TestProgressRepository() }) { secrets.removeAt(0) }

    private fun GameViewModel.type(code: String) = code.forEach(::onDigit)

    private fun GameViewModel.replay(): GameViewModel {
        val repository = repositories.values.first { it.records.value.any { record -> record.id == uiState.value.roundId } }
        val request = requireNotNull(replayRequest())
        val entry = RoundEntryViewModel(repository, generateSecret = { "012" })
        entry.requestStart(request)
        val destination = requireNotNull(entry.uiState.value.destination)
        return GameViewModel(SavedStateHandle(), null, request.difficulty, repository,
            initialOwnerId = destination.ownerId, initialRoundId = destination.roundId)
    }

    @Test
    fun `Easy reveals submitted digit feedback and keeps the V1 remark`() {
        val vm = viewModel(difficulty = Difficulty.EASY)
        vm.type("527")

        assertEquals("527", vm.uiState.value.currentInput)
        assertTrue(vm.uiState.value.guesses.isEmpty())
        assertFalse(vm.uiState.value.showsDigitFeedback(0))

        vm.onEnter()

        val guess = vm.uiState.value.guesses.single()
        assertEquals(listOf(DigitFeedback.EXACT, DigitFeedback.ABSENT, DigitFeedback.CLOSE), guess.digits)
        assertEquals("1R, 1W", guess.remark)
        assertEquals("", vm.uiState.value.currentInput)
        assertEquals(0, vm.uiState.value.lastRevealedIndex)
        assertTrue(vm.uiState.value.showsDigitFeedback(0))
        assertFalse(vm.uiState.value.showsDigitFeedback(1))

        vm.type("481")
        vm.onEnter()
        assertTrue(vm.uiState.value.showsDigitFeedback(0))
        assertTrue(vm.uiState.value.showsDigitFeedback(1))
    }

    @Test
    fun `difficulty and hint eligibility apply independently of player mode`() {
        for (playerMode in PlayerMode.entries) {
            val friendSecret = "573".takeIf { playerMode == PlayerMode.DOUBLE }
            for (difficulty in Difficulty.entries) {
                val vm = viewModel(friendSecret = friendSecret, difficulty = difficulty)
                vm.type("527")
                vm.onEnter()

                val state = vm.uiState.value
                assertEquals(playerMode, state.playerMode)
                assertEquals("1R, 1W", state.guesses.single().remark)
                assertEquals(difficulty == Difficulty.EASY, state.showsDigitFeedback(0))
                assertEquals(difficulty == Difficulty.MEDIUM, state.canUseHint(0))

                vm.onHint(0)

                assertEquals(0.takeIf { difficulty == Difficulty.MEDIUM }, vm.uiState.value.hintedGuessIndex)
                assertEquals(difficulty != Difficulty.HARD, vm.uiState.value.showsDigitFeedback(0))
            }
        }
    }

    @Test
    fun `Medium spends its hint only on the selected submitted occurrence`() {
        val vm = viewModel(difficulty = Difficulty.MEDIUM)
        vm.type("527")
        vm.onHint(0)
        vm.onEnter()
        vm.type("527")
        vm.onEnter()
        vm.type("48")
        vm.onHint(-1)
        vm.onHint(2)

        assertEquals(1, vm.uiState.value.hintsRemaining)

        vm.onHint(1)
        vm.onHint(0)
        vm.onHint(1)

        val state = vm.uiState.value
        assertEquals(0, state.hintsRemaining)
        assertEquals(1, state.hintedGuessIndex)
        assertEquals(listOf("527", "527"), state.guesses.map { it.guess })
        assertEquals("48", state.currentInput)
        assertFalse(state.showsDigitFeedback(0))
        assertTrue(state.showsDigitFeedback(1))
        assertFalse(state.showsDigitFeedback(2))
        assertFalse(state.canUseHint(0))
    }

    @Test
    fun `Medium cannot spend a hint after winning or quitting`() {
        val won = viewModel(difficulty = Difficulty.MEDIUM)
        won.type("573")
        won.onEnter()
        won.onHint(0)

        assertTrue(won.uiState.value.isWon)
        assertNull(won.uiState.value.hintedGuessIndex)
        assertFalse(won.uiState.value.canUseHint(0))
        assertFalse(won.uiState.value.showsDigitFeedback(0))

        val quit = viewModel(difficulty = Difficulty.MEDIUM)
        quit.type("527")
        quit.onEnter()
        quit.onConfirmQuit()
        quit.onHint(0)

        assertTrue(quit.uiState.value.isGivenUp)
        assertNull(quit.uiState.value.hintedGuessIndex)
        assertFalse(quit.uiState.value.canUseHint(0))
        assertFalse(quit.uiState.value.showsDigitFeedback(0))
    }

    @Test
    fun `Medium keeps a spent hint through restoration and resets it for replay`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle, difficulty = Difficulty.MEDIUM)
        vm.type("527")
        vm.onEnter()
        vm.onHint(0)
        vm.type("573")
        vm.onEnter()

        val restored = viewModel(handle)
        assertTrue(restored.uiState.value.isWon)
        assertTrue(restored.uiState.value.showsDigitFeedback(0))
        assertFalse(restored.uiState.value.showsDigitFeedback(1))
        assertEquals(0, restored.uiState.value.hintsRemaining)

        val replay = restored.replay().uiState.value
        assertEquals(Difficulty.MEDIUM, replay.difficulty)
        assertEquals(1, replay.hintsRemaining)
        assertNull(replay.hintedGuessIndex)
        assertTrue(replay.guesses.isEmpty())
    }

    @Test
    fun `missing Medium hint data leaves one hint and a new round drops stale hint data`() {
        val unspentHandle = SavedStateHandle(
            mapOf("game_secret" to "573", "game_difficulty" to Difficulty.MEDIUM.name),
        )
        assertEquals(1, viewModel(unspentHandle).uiState.value.hintsRemaining)

        val staleHandle = SavedStateHandle(mapOf("game_hinted_guess_index" to 0))
        val fresh = viewModel(staleHandle, difficulty = Difficulty.MEDIUM)
        fresh.type("527")
        fresh.onEnter()
        val restored = viewModel(staleHandle).uiState.value

        assertEquals(1, restored.hintsRemaining)
        assertFalse(restored.showsDigitFeedback(0))
    }

    @Test
    fun `malformed restored hint stays spent and never reveals another row`() {
        for ((savedIndex, savedGuesses) in listOf(
            "0" to arrayListOf("527"),
            1 to arrayListOf("527"),
            0 to arrayListOf("999", "527"),
        )) {
            val handle = SavedStateHandle(
                mapOf(
                    "game_secret" to "573",
                    "game_difficulty" to Difficulty.MEDIUM.name,
                    "game_guesses" to savedGuesses,
                    "game_hinted_guess_index" to savedIndex,
                ),
            )
            val vm = viewModel(handle)

            assertEquals(0, vm.uiState.value.hintsRemaining)
            assertFalse(vm.uiState.value.showsDigitFeedback(0))

            vm.onHint(0)
            vm.type("481")
            vm.onEnter()

            val restored = viewModel(handle).uiState.value
            assertEquals(0, restored.hintsRemaining)
            assertFalse(restored.showsDigitFeedback(0))
            assertFalse(restored.showsDigitFeedback(1))
        }
    }

    @Test
    fun `duplicate digits and a fourth digit are ignored and enter needs three digits`() {
        val vm = viewModel()
        vm.type("55")
        assertEquals("5", vm.uiState.value.currentInput)

        vm.onEnter()
        assertTrue(vm.uiState.value.guesses.isEmpty())

        vm.type("7890")
        assertEquals("578", vm.uiState.value.currentInput)

        vm.onDelete()
        assertEquals("57", vm.uiState.value.currentInput)
    }

    @Test
    fun `winning locks input, reveals the secret, and allows leaving without confirmation`() {
        val vm = viewModel()
        vm.type("573")
        vm.onEnter()

        val state = vm.uiState.value
        assertTrue(state.isWon)
        assertTrue(state.showWinDialog)
        assertEquals("573", state.revealedSecret)
        assertEquals("Winner", state.guesses.last().remark)

        vm.onDigit('1')
        assertEquals("", vm.uiState.value.currentInput)
        assertTrue(vm.onBackRequested())
    }

    @Test
    fun `back during a round asks for confirmation`() {
        val vm = viewModel()
        assertFalse(vm.onBackRequested())
        assertTrue(vm.uiState.value.showExitConfirmation)

        vm.onDismissExitConfirmation()
        assertFalse(vm.uiState.value.showExitConfirmation)
    }

    @Test
    fun `state is restored from the saved state handle`() {
        val handle = SavedStateHandle()
        viewModel(handle, difficulty = Difficulty.EASY).apply {
            type("481")
            onEnter()
            type("21")
        }

        val restored = viewModel(handle, secrets = mutableListOf("999")).uiState.value
        assertEquals(Difficulty.EASY, restored.difficulty)
        assertEquals(listOf("481"), restored.guesses.map { it.guess })
        assertEquals("21", restored.currentInput)
        assertNull(restored.lastRevealedIndex)
    }

    @Test
    fun `older and malformed saved round difficulties restore as Hard`() {
        for (savedDifficulty in listOf(null, "UNKNOWN", 2)) {
            val handle = SavedStateHandle(mapOf("game_secret" to "573", "game_difficulty" to savedDifficulty))
            val restored = viewModel(handle, difficulty = Difficulty.EASY).uiState.value
            assertEquals(Difficulty.HARD, restored.difficulty)
        }
    }

    @Test
    fun `new rounds use the requested difficulty even if a stale difficulty is saved`() {
        val handle = SavedStateHandle(mapOf("game_difficulty" to Difficulty.HARD.name))
        val state = viewModel(handle, difficulty = Difficulty.MEDIUM).uiState.value

        assertEquals(Difficulty.MEDIUM, state.difficulty)
    }

    @Test
    fun `double player mode is restored even when the in-memory friend secret is gone`() {
        val handle = SavedStateHandle()
        viewModel(handle, friendSecret = "908", difficulty = Difficulty.MEDIUM).apply {
            type("901")
            onEnter()
        }

        val restored = viewModel(handle, friendSecret = null).uiState.value
        assertEquals(PlayerMode.DOUBLE, restored.playerMode)
        assertEquals(Difficulty.MEDIUM, restored.difficulty)
        assertEquals("2R", restored.guesses.single().remark)
    }

    @Test
    fun `valid friend secret starts double player and play again is single player only`() {
        val vm = viewModel(friendSecret = "908")
        assertEquals(PlayerMode.DOUBLE, vm.uiState.value.playerMode)
        vm.type("908")
        vm.onEnter()
        assertNull(vm.replayRequest())
        assertTrue(vm.uiState.value.isWon)

        val single = viewModel(difficulty = Difficulty.EASY)
        single.type("573")
        single.onEnter()
        val replay = single.replay()
        assertEquals(Difficulty.EASY, replay.uiState.value.difficulty)
        assertFalse(replay.uiState.value.isWon)
        assertTrue(replay.uiState.value.guesses.isEmpty())
        replay.type("012")
        replay.onEnter()
        assertTrue(replay.uiState.value.isWon)
    }

    @Test
    fun `quit asks first, then reveals the secret and locks the round`() {
        val vm = viewModel()
        vm.type("48")

        assertFalse(vm.onQuitRequested())
        assertTrue(vm.uiState.value.showQuitConfirmation)
        assertNull(vm.uiState.value.revealedSecret)

        vm.onDismissQuitConfirmation()
        assertFalse(vm.uiState.value.showQuitConfirmation)
        assertFalse(vm.uiState.value.isGivenUp)

        vm.onQuitRequested()
        vm.onConfirmQuit()
        val state = vm.uiState.value
        assertTrue(state.isGivenUp)
        assertFalse(state.isWon)
        assertFalse(state.showQuitConfirmation)
        assertEquals("573", state.revealedSecret)
        assertEquals("", state.currentInput)

        vm.onDigit('1')
        assertEquals("", vm.uiState.value.currentInput)
        assertTrue(vm.onBackRequested())
        assertTrue(vm.onQuitRequested())
    }

    @Test
    fun `a given up round is restored with its secret revealed`() {
        val handle = SavedStateHandle()
        viewModel(handle).apply {
            type("481")
            onEnter()
            onConfirmQuit()
        }

        val restored = viewModel(handle, secrets = mutableListOf("999")).uiState.value
        assertTrue(restored.isGivenUp)
        assertEquals("573", restored.revealedSecret)
        assertEquals(listOf("481"), restored.guesses.map { it.guess })
    }

    @Test
    fun `play again clears a given up round`() {
        val vm = viewModel()
        vm.onConfirmQuit()
        val replay = vm.replay()

        assertFalse(replay.uiState.value.isGivenUp)
        assertNull(replay.uiState.value.revealedSecret)
    }
}
