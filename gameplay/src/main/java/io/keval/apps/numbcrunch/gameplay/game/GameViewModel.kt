package io.keval.apps.numbcrunch.gameplay.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.GuessRules
import io.keval.apps.numbcrunch.domain.game.isCodeDigit
import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.gameplay.home.PlayerMode
import io.keval.apps.numbcrunch.gameplay.progress.NewRoundRequest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val friendSecret: String?,
    private val requestedDifficulty: Difficulty,
    private val repository: ProgressRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val initialOwnerId: String? = null,
    private val initialRoundId: String? = null,
    private val generateSecret: () -> String = { GuessRules.generateSecret() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState(difficulty = requestedDifficulty, isLoading = true))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    private val writes = Mutex()
    private var round: RoundRecord? = null
    private var retry: (suspend () -> Unit)? = null
    private var initialRound: RoundRecord? = null

    init { launchWrite { loadRound() } }

    private suspend fun loadRound() {
        val requestedId = initialRoundId ?: savedStateHandle.get<String>(KEY_ROUND_ID)
        val explicitOwner = initialOwnerId ?: savedStateHandle.get<String>(KEY_OWNER_ID)
        val hasLegacyFacts = savedStateHandle.get<String>(KEY_SECRET)?.let(GuessRules::isValidCode) == true
        // Before owner IDs were saved, every real round belonged to the guest namespace.
        val ownerId = explicitOwner ?: if (requestedId != null || hasLegacyFacts) repository.guestProfile().id
            else repository.selectedProfile().id
        val restored = requestedId?.let { id ->
            val direct = repository.findRound(ownerId, id)
            val claimedGuest = if (direct == null && explicitOwner == null) {
                val selected = repository.selectedProfile()
                val snapshot = repository.exportProgress(selected.id)
                snapshot.rounds.firstOrNull { record ->
                    record.id == id && snapshot.guestClaims.any { it.guestProfileId == record.ownerId }
                }
            } else null
            requireNotNull(direct ?: claimedGuest) { "Round unavailable" }
        }
        val existing = restored ?: repository.unfinishedRound(ownerId)
        val loaded = existing ?: legacyRound(ownerId)?.let { legacy ->
            repository.importLegacyRound(legacy)?.also {
                if (it.status == RoundStatus.WON && savedStateHandle.get<Boolean>("game_win_dialog") != true) {
                    savedStateHandle[KEY_DISMISSED_WIN] = it.id
                }
            }
        } ?: run {
            val candidate = initialRound ?: newRound(ownerId, requestedDifficulty).also { initialRound = it }
            repository.createRound(candidate)
        }
        val active = if (loaded.status == RoundStatus.PAUSED) repository.updateRound(loaded.resume(now()), loaded.revision) else loaded
        showCommitted(active)
        // A durable round ID is the only game fact Android's saved-state bundle keeps after migration.
        LEGACY_KEYS.forEach { savedStateHandle.remove<Any>(it) }
    }

    fun onDigit(digit: Char) = mutate { current ->
        current.takeIf { isCodeDigit(digit) && it.status == RoundStatus.ACTIVE && it.currentInput.length < 3 && digit !in it.currentInput }
            ?.editInput(current.currentInput + digit, now())
    }

    fun onDelete() = mutate { current ->
        current.takeIf { it.status == RoundStatus.ACTIVE && it.currentInput.isNotEmpty() }
            ?.editInput(current.currentInput.dropLast(1), now())
    }

    fun onEnter() = mutate { current ->
        current.takeIf { it.status == RoundStatus.ACTIVE && GuessRules.isValidCode(it.currentInput) }?.submitGuess(now())
    }

    fun onHint(guessIndex: Int) = mutate { current ->
        current.takeIf { it.status == RoundStatus.ACTIVE && it.difficulty == Difficulty.MEDIUM &&
            it.hintedGuessIndex == null && guessIndex in it.guesses.indices }?.useHint(guessIndex, now())
    }

    fun onBackRequested(): Boolean {
        val state = _uiState.value
        if (state.hasPersistenceError && round == null && !state.isSaving) return true
        if (state.isSaving || state.isLoading || state.hasPersistenceError) return false
        if (state.isRoundOver) return true
        _uiState.value = state.copy(showExitConfirmation = true)
        return false
    }

    fun onDismissExitConfirmation() { _uiState.value = _uiState.value.copy(showExitConfirmation = false) }

    fun onQuitRequested(): Boolean {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.hasPersistenceError) return false
        if (state.isRoundOver) return true
        _uiState.value = state.copy(showQuitConfirmation = true)
        return false
    }

    fun onDismissQuitConfirmation() { _uiState.value = _uiState.value.copy(showQuitConfirmation = false) }

    fun onConfirmQuit() = mutate { current ->
        current.takeUnless { it.status.isTerminal }?.finish(RoundStatus.GIVEN_UP, now())
    }

    /** Navigation occurs only after the outcome is committed. */
    fun onConfirmExit(onSaved: () -> Unit) = mutate(onSaved) { current ->
        current.takeUnless { it.status.isTerminal }?.finish(RoundStatus.ABANDONED, now())
    }

    fun onSaveAndPause(onSaved: () -> Unit) = mutate(onSaved) { current ->
        current.takeIf { it.status == RoundStatus.ACTIVE }?.pause(now())
    }

    fun onDismissWinDialog() {
        savedStateHandle[KEY_DISMISSED_WIN] = round?.id
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }

    fun replayRequest(): NewRoundRequest? = round?.takeIf {
        it.mode == RoundMode.SINGLE && it.status.isTerminal && !_uiState.value.isSaving
    }?.let { NewRoundRequest(RoundMode.SINGLE, it.difficulty) }

    fun onRetryPersistence() {
        val operation = retry ?: return
        retry = null
        _uiState.value = _uiState.value.copy(hasPersistenceError = false, showExitConfirmation = false)
        launchWrite(operation)
    }

    fun onDismissPersistenceError() {
        if (round == null) return
        retry = null
        _uiState.value = _uiState.value.copy(hasPersistenceError = false, showExitConfirmation = false)
    }

    private fun mutate(onSaved: (() -> Unit)? = null, action: (RoundRecord) -> RoundRecord?) = launchWrite {
        val previous = round ?: return@launchWrite
        val candidate = action(previous) ?: return@launchWrite
        commitOperation {
            val committed = repository.updateRound(candidate, previous.revision)
            showCommitted(committed, revealLastGuess = committed.guesses.size > previous.guesses.size)
            onSaved?.invoke()
        }
    }

    /** The failed operation is kept intact, so retry cannot generate another round or outcome. */
    private suspend fun commitOperation(operation: suspend () -> Unit) {
        try { operation() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            retry = operation
            _uiState.value = _uiState.value.copy(hasPersistenceError = true)
        }
    }

    private fun launchWrite(operation: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                if (retry != null) return@withLock
                _uiState.value = _uiState.value.copy(isSaving = true)
                try { commitOperation(operation) }
                finally { _uiState.value = _uiState.value.copy(isSaving = false) }
            }
        }
    }

    private fun showCommitted(record: RoundRecord, revealLastGuess: Boolean = false) {
        round = record
        savedStateHandle[KEY_ROUND_ID] = record.id
        savedStateHandle[KEY_OWNER_ID] = record.ownerId
        val won = record.status == RoundStatus.WON
        val givenUp = record.status == RoundStatus.GIVEN_UP
        _uiState.value = GameUiState(
            roundId = record.id,
            ownerId = record.ownerId,
            isPaused = record.status == RoundStatus.PAUSED,
            playerMode = PlayerMode.valueOf(record.mode.name),
            difficulty = record.difficulty,
            guesses = record.guesses.map { GuessRules.evaluate(record.secret, it) },
            currentInput = record.currentInput,
            hintedGuessIndex = record.hintedGuessIndex,
            isWon = won,
            isGivenUp = givenUp,
            isAbandoned = record.status == RoundStatus.ABANDONED,
            revealedSecret = record.secret.takeIf { won || givenUp },
            showWinDialog = won && savedStateHandle.get<String>(KEY_DISMISSED_WIN) != record.id,
            lastRevealedIndex = record.guesses.lastIndex.takeIf { revealLastGuess },
        )
    }

    private fun newRound(
        ownerId: String,
        difficulty: Difficulty,
        friend: String? = friendSecret?.takeIf(GuessRules::isValidCode),
    ): RoundRecord {
        return RoundRecord(
            id = UUID.randomUUID().toString(), ownerId = ownerId,
            mode = if (friend != null) RoundMode.DOUBLE else RoundMode.SINGLE,
            difficulty = difficulty, secret = friend ?: generateSecret(), createdAt = now(),
        )
    }

    private fun legacyRound(ownerId: String): RoundRecord? {
        val secret = savedStateHandle.get<String>(KEY_SECRET)?.takeIf(GuessRules::isValidCode) ?: return null
        val difficulty = Difficulty.fromName(savedStateHandle.get<Any>(KEY_DIFFICULTY) as? String) ?: Difficulty.HARD
        val originalGuesses = savedStateHandle.get<ArrayList<String>>(KEY_GUESSES).orEmpty()
        val validGuesses = originalGuesses.filter(GuessRules::isValidCode)
        val winIndex = validGuesses.indexOf(secret)
        val guesses = if (winIndex >= 0) validGuesses.take(winIndex + 1) else validGuesses
        val status = when {
            guesses.lastOrNull() == secret -> RoundStatus.WON
            savedStateHandle.get<Boolean>(KEY_GIVEN_UP) == true -> RoundStatus.GIVEN_UP
            else -> RoundStatus.ACTIVE
        }
        val hint = if (difficulty == Difficulty.MEDIUM) savedStateHandle.get<Any>(KEY_HINTED_GUESS_INDEX)?.let {
            (it as? Int)?.takeIf { originalGuesses.size == guesses.size && it in guesses.indices } ?: -1
        } else null
        val input = savedStateHandle.get<String>(KEY_INPUT).orEmpty().takeIf {
            !status.isTerminal && it.length <= 3 && it.all(::isCodeDigit) && it.toSet().size == it.length
        }.orEmpty()
        val time = now()
        return RoundRecord(
            id = UUID.randomUUID().toString(), ownerId = ownerId,
            mode = RoundMode.entries.firstOrNull { it.name == savedStateHandle.get<String>(KEY_MODE) } ?: RoundMode.SINGLE,
            difficulty = difficulty, secret = secret, createdAt = time, origin = RoundOrigin.LEGACY,
            status = status, guesses = guesses, currentInput = input, hintedGuessIndex = hint,
            startedAt = time.takeIf { guesses.isNotEmpty() }, completedAt = time.takeIf { status.isTerminal },
        ).also { it.validate() }
    }

    companion object {
        private const val KEY_ROUND_ID = "progress_round_id"
        private const val KEY_OWNER_ID = "progress_owner_id"
        private const val KEY_DISMISSED_WIN = "progress_dismissed_win"
        private const val KEY_SECRET = "game_secret"
        private const val KEY_MODE = "game_mode"
        private const val KEY_DIFFICULTY = "game_difficulty"
        private const val KEY_GUESSES = "game_guesses"
        private const val KEY_INPUT = "game_input"
        private const val KEY_GIVEN_UP = "game_given_up"
        private const val KEY_HINTED_GUESS_INDEX = "game_hinted_guess_index"
        private val LEGACY_KEYS = listOf(KEY_SECRET, KEY_MODE, KEY_DIFFICULTY, KEY_GUESSES, KEY_INPUT,
            KEY_GIVEN_UP, KEY_HINTED_GUESS_INDEX, "game_win_dialog")

        fun factory(friendSecret: String?, difficulty: Difficulty, repository: ProgressRepository,
            ownerId: String? = null, roundId: String? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    require(modelClass.isAssignableFrom(GameViewModel::class.java)) { "Unknown ViewModel class" }
                    return GameViewModel(extras.createSavedStateHandle(), friendSecret, difficulty, repository,
                        initialOwnerId = ownerId, initialRoundId = roundId) as T
                }
            }
    }
}
