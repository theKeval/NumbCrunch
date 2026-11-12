package io.keval.apps.numbcrunch.gameplay.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.isCodeDigit
import io.keval.apps.numbcrunch.domain.usecase.GetWelcomeMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    getWelcomeMessageUseCase: GetWelcomeMessageUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            difficulty = Difficulty.fromName(savedStateHandle.get<Any>(KEY_DIFFICULTY) as? String)
                ?: Difficulty.EASY,
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        getWelcomeMessageUseCase()
    }

    fun onPlayerModeSelected(playerMode: PlayerMode) {
        _uiState.value = _uiState.value.copy(
            playerMode = playerMode,
            friendSecretNumber = if (playerMode == PlayerMode.SINGLE) "" else _uiState.value.friendSecretNumber,
            secretValidationMessage = null,
        )
    }

    fun onDifficultySelected(difficulty: Difficulty) {
        savedStateHandle[KEY_DIFFICULTY] = difficulty.name
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }

    fun onFriendSecretNumberChanged(value: String) {
        val sanitized = value.filter(::isCodeDigit).take(3)
        _uiState.value = _uiState.value.copy(
            friendSecretNumber = sanitized,
            secretValidationMessage = sanitized
                .takeIf(String::isNotEmpty)
                ?.let(::validateSecret),
        )
    }

    /** A friend's secret is single use: clear it once its game starts so Home is ready for the next one. */
    fun onGameStarted() {
        _uiState.value = _uiState.value.copy(friendSecretNumber = "", secretValidationMessage = null)
    }

    /** Adds [digit] to the current secret; used or extra digits are ignored. */
    fun onFriendSecretDigit(digit: Char) {
        val secret = _uiState.value.friendSecretNumber
        if (canAddSecretDigit(secret, digit)) onFriendSecretNumberChanged(secret + digit)
    }

    fun onFriendSecretDelete() {
        onFriendSecretNumberChanged(_uiState.value.friendSecretNumber.dropLast(1))
    }

    fun canStartGame(): Boolean {
        val currentState = _uiState.value
        if (currentState.playerMode == PlayerMode.SINGLE) {
            _uiState.value = currentState.copy(secretValidationMessage = null)
            return true
        }

        val validationMessage = validateSecret(currentState.friendSecretNumber)
        _uiState.value = currentState.copy(secretValidationMessage = validationMessage)
        return validationMessage == null
    }

    private fun validateSecret(secret: String): String? {
        return when {
            secret.toSet().size != secret.length -> "Digits must be unique"
            secret.length < 3 -> "Enter exactly 3 digits"
            else -> null
        }
    }

    companion object {
        private const val KEY_DIFFICULTY = "home_difficulty"

        fun factory(
            getWelcomeMessageUseCase: GetWelcomeMessageUseCase,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                        "Unknown ViewModel class: ${modelClass.name}"
                    }
                    return HomeViewModel(getWelcomeMessageUseCase, extras.createSavedStateHandle()) as T
                }
            }
        }
    }
}
