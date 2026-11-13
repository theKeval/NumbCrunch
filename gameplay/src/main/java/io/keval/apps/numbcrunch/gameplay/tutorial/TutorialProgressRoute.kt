package io.keval.apps.numbcrunch.gameplay.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository

@Composable
fun TutorialProgressRoute(
    progress: ProgressRepository,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstRun: Boolean = false,
    onCompleteFirstRun: () -> Unit = onBack,
    isCompletingFirstRun: Boolean = false,
    firstRunCompletionError: Boolean = false,
) {
    val model: TutorialCompletionViewModel = viewModel(factory = TutorialCompletionViewModel.factory(progress))
    val completion by model.state.collectAsStateWithLifecycle()
    TutorialScreen(
        onBack, onPlay, modifier, isFirstRun, onCompleteFirstRun,
        isCompletingFirstRun || completion.loading || completion.saving,
        firstRunCompletionError || completion.error,
        onFinishTutorial = { evidence -> model.finish(evidence) {
            if (isFirstRun) onCompleteFirstRun() else onPlay()
        } },
    )
}
