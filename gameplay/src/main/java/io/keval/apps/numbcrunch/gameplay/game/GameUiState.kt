package io.keval.apps.numbcrunch.gameplay.game

import io.keval.apps.numbcrunch.domain.game.CODE_LENGTH
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.GuessEvaluation
import io.keval.apps.numbcrunch.gameplay.home.PlayerMode

data class GameUiState(
    val roundId: String? = null,
    val ownerId: String? = null,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasPersistenceError: Boolean = false,
    val playerMode: PlayerMode = PlayerMode.SINGLE,
    val difficulty: Difficulty = Difficulty.HARD,
    val guesses: List<GuessEvaluation> = emptyList(),
    val currentInput: String = "",
    val isWon: Boolean = false,
    /** The player quit the round and the secret was revealed to them. */
    val isGivenUp: Boolean = false,
    val isAbandoned: Boolean = false,
    /** Revealed only once the round is won or given up. */
    val revealedSecret: String? = null,
    val showExitConfirmation: Boolean = false,
    val showQuitConfirmation: Boolean = false,
    val showWinDialog: Boolean = false,
    /** Index of the guess evaluated during this session, used to animate its reveal once. */
    val lastRevealedIndex: Int? = null,
    /** A nonnull index records a spent hint, even if its restored row is unavailable. */
    val hintedGuessIndex: Int? = null,
) {
    val isRoundOver: Boolean get() = isWon || isGivenUp || isAbandoned
    private val canInteract: Boolean get() = !isLoading && !hasPersistenceError && !isRoundOver && !isPaused
    val canEnter: Boolean get() = canInteract && currentInput.length == CODE_LENGTH
    val canDelete: Boolean get() = canInteract && currentInput.isNotEmpty()

    val hintsRemaining: Int
        get() = if (difficulty == Difficulty.MEDIUM && canInteract && hintedGuessIndex == null) 1 else 0

    internal fun canUseHint(guessIndex: Int): Boolean = hintsRemaining > 0 && guessIndex in guesses.indices

    internal fun showsDigitFeedback(guessIndex: Int): Boolean =
        guessIndex in guesses.indices && when (difficulty) {
            Difficulty.EASY -> true
            Difficulty.MEDIUM -> hintedGuessIndex == guessIndex
            Difficulty.HARD -> false
        }

    fun isDigitEnabled(digit: Char): Boolean {
        return canInteract && currentInput.length < CODE_LENGTH && digit !in currentInput
    }
}
