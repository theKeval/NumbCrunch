package io.keval.apps.numbcrunch.gameplay.tutorial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import io.keval.apps.numbcrunch.domain.tutorial.TutorialEvidence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TutorialCompletionState(val loading: Boolean = true, val saving: Boolean = false, val error: Boolean = false)

/** First-run dismissal is separate from this owner-pinned, genuinely completed tutorial receipt. */
class TutorialCompletionViewModel(
    private val progress: ProgressRepository,
    private val savedState: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TutorialCompletionState())
    val state = mutableState.asStateFlow()

    init { loadOwner() }

    private fun loadOwner() = viewModelScope.launch {
        try {
            if (savedState.get<String>(OWNER) == null) savedState[OWNER] = progress.selectedProfile().id
            mutableState.value = TutorialCompletionState(loading = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = TutorialCompletionState(loading = false, error = true) }
    }

    fun finish(evidence: TutorialEvidence, onSaved: () -> Unit) {
        if (state.value.loading || state.value.saving) return
        val owner = savedState.get<String>(OWNER)
        if (owner == null) { loadOwner(); return }
        val time = savedState.get<Long>(COMPLETED_AT) ?: now().also { savedState[COMPLETED_AT] = it }
        val receipt = evidence.completion(owner, time)
        if (receipt == null) { onSaved(); return }
        mutableState.value = TutorialCompletionState(loading = false, saving = true)
        viewModelScope.launch {
            try {
                progress.recordTutorialCompletion(receipt)
                mutableState.value = TutorialCompletionState(loading = false)
                onSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = TutorialCompletionState(loading = false, error = true) }
        }
    }

    companion object {
        private const val OWNER = "tutorial_owner"
        private const val COMPLETED_AT = "tutorial_completed_at"
        fun factory(progress: ProgressRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(TutorialCompletionViewModel::class.java))
                return TutorialCompletionViewModel(progress, extras.createSavedStateHandle()) as T
            }
        }
    }
}
