package io.keval.apps.numbcrunch.gameplay.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.GuessRules
import io.keval.apps.numbcrunch.domain.progress.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Safe metadata for Home and dialogs; never contains a secret, guesses or revealed clues. */
data class OngoingRound(
    val id: String,
    val ownerId: String,
    val mode: RoundMode,
    val difficulty: Difficulty,
    val guessCount: Int,
    val paused: Boolean,
)

data class RoundDestination(val ownerId: String, val roundId: String)

data class NewRoundRequest(
    val mode: RoundMode,
    val difficulty: Difficulty,
    val friendSecret: String? = null,
) {
    override fun toString() = "NewRoundRequest(mode=$mode, difficulty=$difficulty, secret=redacted)"
}

data class RoundEntryUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val hasReadError: Boolean = false,
    val hasWriteError: Boolean = false,
    val ongoingRounds: List<OngoingRound> = emptyList(),
    val choices: List<OngoingRound> = emptyList(),
    val abandonTarget: OngoingRound? = null,
    val destination: RoundDestination? = null,
)

/** All real-round entry points share this durable slot decision, including Tutorial and replay. */
class RoundEntryViewModel(
    private val repository: ProgressRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val generateSecret: () -> String = { GuessRules.generateSecret() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoundEntryUiState())
    val uiState = _uiState.asStateFlow()
    private var observing: Job? = null
    private var pendingRequest: NewRoundRequest? = null
    private var pendingOwnerId: String? = null
    private var pendingCandidate: RoundRecord? = null
    private var retry: (suspend () -> Unit)? = null

    init { refresh() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun refresh() {
        observing?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, hasReadError = false)
        observing = viewModelScope.launch {
            repository.observeSelectedProfile().flatMapLatest { profile -> repository.observeRounds(profile.id) }
                .catch { _uiState.value = _uiState.value.copy(isLoading = false, hasReadError = true) }
                .collect { rounds ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, hasReadError = false,
                        ongoingRounds = rounds.filterNot { it.status.isTerminal }.map(::summary),
                    )
                }
        }
    }

    fun requestStart(request: NewRoundRequest) {
        if (blocked() || pendingRequest != null) return
        pendingRequest = request
        runOperation { startOrChoose() }
    }

    private suspend fun startOrChoose() {
        val request = pendingRequest ?: return
        val ownerId = pendingOwnerId ?: repository.selectedProfile().id.also { pendingOwnerId = it }
        pendingCandidate?.let { candidate ->
            repository.findRound(ownerId, candidate.id)?.let { committed ->
                require(!committed.status.isTerminal) { "Round already completed" }
                ready(committed)
                return
            }
        }
        val unfinished = repository.unfinishedRounds(ownerId)
        if (unfinished.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(choices = unfinished.map(::summary))
            return
        }
        val candidate = pendingCandidate ?: RoundRecord(
            id = UUID.randomUUID().toString(), ownerId = ownerId, mode = request.mode,
            difficulty = request.difficulty,
            secret = if (request.mode == RoundMode.DOUBLE) requireNotNull(request.friendSecret) else generateSecret(),
            createdAt = now(),
        ).also { it.validate(); pendingCandidate = it }
        ready(repository.createRound(candidate))
    }

    fun resume(saved: OngoingRound) {
        if (blocked()) return
        runOperation {
            val record = requireNotNull(repository.findRound(saved.ownerId, saved.id)) { "Saved round unavailable" }
            require(!record.status.isTerminal) { "Round already completed" }
            val active = if (record.status == RoundStatus.PAUSED) {
                repository.updateRound(record.resume(now()), record.revision)
            } else record
            ready(active)
        }
    }

    fun requestAbandon(saved: OngoingRound) {
        if (!blocked()) _uiState.value = _uiState.value.copy(abandonTarget = saved)
    }

    fun cancelAbandon() {
        if (!uiState.value.isBusy) _uiState.value = _uiState.value.copy(abandonTarget = null)
    }

    fun confirmAbandon() {
        val target = _uiState.value.abandonTarget ?: return
        if (blocked()) return
        runOperation {
            val record = requireNotNull(repository.findRound(target.ownerId, target.id))
            if (!record.status.isTerminal) repository.updateRound(record.finish(RoundStatus.ABANDONED, now()), record.revision)
            _uiState.value = _uiState.value.copy(abandonTarget = null)
            // A claimed guest can retain another unfinished round. Each needs its own explicit choice.
            if (pendingRequest != null) startOrChoose()
        }
    }

    fun cancelStart() {
        if (_uiState.value.isBusy) return
        pendingRequest = null
        pendingOwnerId = null
        pendingCandidate = null
        retry = null
        _uiState.value = _uiState.value.copy(choices = emptyList(), abandonTarget = null, hasWriteError = false)
    }

    fun retryWrite() {
        val operation = retry ?: return
        runOperation(operation)
    }

    fun consumeDestination(destination: RoundDestination) {
        if (_uiState.value.destination == destination) _uiState.value = _uiState.value.copy(destination = null)
    }

    private fun ready(record: RoundRecord) {
        pendingRequest = null
        pendingOwnerId = null
        pendingCandidate = null
        _uiState.value = _uiState.value.copy(
            choices = emptyList(), abandonTarget = null,
            destination = RoundDestination(record.ownerId, record.id),
        )
    }

    private fun blocked() = _uiState.value.isBusy || _uiState.value.hasWriteError || _uiState.value.destination != null

    private fun runOperation(operation: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, hasWriteError = false)
        retry = null
        viewModelScope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                retry = operation
                _uiState.value = _uiState.value.copy(hasWriteError = true)
            } finally { _uiState.value = _uiState.value.copy(isBusy = false) }
        }
    }

    companion object {
        private fun summary(record: RoundRecord) = OngoingRound(
            record.id, record.ownerId, record.mode, record.difficulty, record.guesses.size, record.status == RoundStatus.PAUSED,
        )
        fun factory(repository: ProgressRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(RoundEntryViewModel::class.java))
                return RoundEntryViewModel(repository) as T
            }
        }
    }
}
