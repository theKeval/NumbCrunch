package io.keval.apps.numbcrunch.gameplay.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val profile: PlayerProfile? = null,
    val recordedSince: Long? = null,
    val filter: StatisticsFilter = StatisticsFilter(),
    val statistics: PlayerStatistics = PlayerStatistics(),
    val ongoing: Long = 0,
)

class StatisticsViewModel(
    private val repository: ProgressRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState(filter = StatisticsFilter(
        mode = RoundMode.entries.firstOrNull { it.name == savedStateHandle.get<String>(MODE_KEY) },
        difficulty = Difficulty.entries.firstOrNull { it.name == savedStateHandle.get<String>(DIFFICULTY_KEY) },
    )))
    val uiState = _uiState.asStateFlow()
    private var snapshot: ProgressSnapshot? = null
    private var observing: Job? = null

    init { refresh() }

    fun refresh() {
        observing?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, hasError = false)
        observing = viewModelScope.launch {
            try {
                repository.observeSelectedProfile().collectLatest { profile ->
                    snapshot = null
                    _uiState.value = StatisticsUiState(profile = profile, filter = _uiState.value.filter)
                    repository.observeProgress(profile.id).collect { records ->
                        snapshot = records
                        project(profile)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, hasError = true) }
        }
    }

    fun selectMode(mode: RoundMode?) {
        savedStateHandle[MODE_KEY] = mode?.name
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(mode = mode))
        if (!_uiState.value.isLoading && !_uiState.value.hasError) _uiState.value.profile?.let(::project)
    }

    fun selectDifficulty(difficulty: Difficulty?) {
        savedStateHandle[DIFFICULTY_KEY] = difficulty?.name
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(difficulty = difficulty))
        if (!_uiState.value.isLoading && !_uiState.value.hasError) _uiState.value.profile?.let(::project)
    }

    private fun project(profile: PlayerProfile) {
        val filter = _uiState.value.filter
        val saved = snapshot ?: return
        val visible = saved.rounds.filter { (filter.mode == null || it.mode == filter.mode) &&
            (filter.difficulty == null || it.difficulty == filter.difficulty) }
        _uiState.value = _uiState.value.copy(
            isLoading = false, hasError = false,
            recordedSince = (listOf(profile.trackingStartedAt) + saved.associatedProfiles.map { it.trackingStartedAt } +
                saved.rounds.map { it.createdAt } + saved.completedFacts.map { it.createdAt }).minOrNull(),
            statistics = saved.statisticsFor(filter),
            ongoing = visible.count { !it.status.isTerminal }.toLong(),
        )
    }

    companion object {
        private const val MODE_KEY = "statistics_mode"
        private const val DIFFICULTY_KEY = "statistics_difficulty"
        fun factory(repository: ProgressRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(StatisticsViewModel::class.java))
                return StatisticsViewModel(repository, extras.createSavedStateHandle()) as T
            }
        }
    }
}
