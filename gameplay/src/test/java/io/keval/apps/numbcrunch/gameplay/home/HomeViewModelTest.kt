package io.keval.apps.numbcrunch.gameplay.home

import androidx.lifecycle.SavedStateHandle
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.repository.AppInfoRepository
import io.keval.apps.numbcrunch.domain.usecase.GetWelcomeMessageUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {

    private val viewModel = viewModel()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = HomeViewModel(
        GetWelcomeMessageUseCase(
            object : AppInfoRepository {
                override fun getWelcomeMessage(): String = "Welcome"
            },
        ),
        handle,
    )

    @Test
    fun `difficulty defaults to Easy when saved selection is missing or malformed`() {
        assertEquals(Difficulty.EASY, viewModel.uiState.value.difficulty)
        for (invalid in listOf("UNKNOWN", 2)) {
            val handle = SavedStateHandle(mapOf("home_difficulty" to invalid))
            assertEquals(Difficulty.EASY, viewModel(handle).uiState.value.difficulty)
        }
    }

    @Test
    fun `start keeps the same validation message shown while typing`() {
        viewModel.onPlayerModeSelected(PlayerMode.DOUBLE)
        viewModel.onFriendSecretNumberChanged("11")

        val messageWhileTyping = viewModel.uiState.value.secretValidationMessage
        val canStart = viewModel.canStartGame()

        assertFalse(canStart)
        assertEquals("Digits must be unique", messageWhileTyping)
        assertEquals(messageWhileTyping, viewModel.uiState.value.secretValidationMessage)
    }

    @Test
    fun `empty secret is quiet while typing but required on start`() {
        viewModel.onPlayerModeSelected(PlayerMode.DOUBLE)
        viewModel.onFriendSecretNumberChanged("")

        assertEquals(null, viewModel.uiState.value.secretValidationMessage)
        assertFalse(viewModel.canStartGame())
        assertEquals("Enter exactly 3 digits", viewModel.uiState.value.secretValidationMessage)
    }

    @Test
    fun `three unique digits can start double player game`() {
        viewModel.onPlayerModeSelected(PlayerMode.DOUBLE)
        viewModel.onFriendSecretNumberChanged("258")

        assertTrue(viewModel.canStartGame())
        assertEquals(null, viewModel.uiState.value.secretValidationMessage)
    }

    @Test
    fun `starting a double player game clears the friend secret and keeps mode and difficulty`() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.onDifficultySelected(Difficulty.MEDIUM)
        viewModel.onPlayerModeSelected(PlayerMode.DOUBLE)
        viewModel.onFriendSecretNumberChanged("258")
        assertTrue(viewModel.canStartGame())

        viewModel.onGameStarted()

        assertEquals(PlayerMode.DOUBLE, viewModel.uiState.value.playerMode)
        assertEquals(Difficulty.MEDIUM, viewModel.uiState.value.difficulty)
        assertEquals("", viewModel.uiState.value.friendSecretNumber)
        assertEquals(null, viewModel.uiState.value.secretValidationMessage)
        assertEquals(Difficulty.MEDIUM, viewModel(handle).uiState.value.difficulty)
    }

    @Test
    fun `keypad digits always apply to the latest secret`() {
        viewModel.onPlayerModeSelected(PlayerMode.DOUBLE)
        // Back-to-back taps with no recomposition in between must all land.
        viewModel.onFriendSecretDigit('5')
        viewModel.onFriendSecretDigit('7')
        viewModel.onFriendSecretDigit('5')
        viewModel.onFriendSecretDigit('3')
        viewModel.onFriendSecretDigit('1')

        assertEquals("573", viewModel.uiState.value.friendSecretNumber)
        assertEquals(null, viewModel.uiState.value.secretValidationMessage)

        viewModel.onFriendSecretDelete()
        viewModel.onFriendSecretDelete()
        assertEquals("5", viewModel.uiState.value.friendSecretNumber)
        assertEquals("Enter exactly 3 digits", viewModel.uiState.value.secretValidationMessage)

        viewModel.onFriendSecretDelete()
        viewModel.onFriendSecretDelete()
        assertEquals("", viewModel.uiState.value.friendSecretNumber)
        assertEquals(null, viewModel.uiState.value.secretValidationMessage)
    }
}
