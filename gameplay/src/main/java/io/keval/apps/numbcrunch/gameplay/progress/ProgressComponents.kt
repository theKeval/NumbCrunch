package io.keval.apps.numbcrunch.gameplay.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchChocolateDark
import io.keval.apps.numbcrunch.domain.progress.RoundMode
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.common.GuideAction
import io.keval.apps.numbcrunch.gameplay.common.labelRes
import io.keval.apps.numbcrunch.gameplay.game.NumbCrunchDialogFrame
import io.keval.apps.numbcrunch.gameplay.game.WoodPanel

@Composable
fun OngoingRoundCard(round: OngoingRound, onResume: () -> Unit, modifier: Modifier = Modifier) {
    WoodPanel(textureRes = R.drawable.game_keypad_wood, overlay = NumbCrunchChocolateDark.copy(alpha = 0.7f), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (round.paused) R.string.progress_paused_game else R.string.progress_ongoing_game),
                color = NumbCrunchGoldCream, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() },
            )
            RoundSummary(round)
            val label = stringResource(R.string.progress_resume_description, modeLabel(round), stringResource(round.difficulty.labelRes))
            GuideAction(stringResource(R.string.progress_resume), onResume,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
        }
    }
}

@Composable
private fun modeLabel(round: OngoingRound) = stringResource(
    if (round.mode == RoundMode.SINGLE) R.string.label_single_player else R.string.label_double_player,
)

@Composable
private fun RoundSummary(round: OngoingRound) {
    Text(stringResource(R.string.progress_round_summary, modeLabel(round), stringResource(round.difficulty.labelRes)), color = NumbCrunchGoldCream)
    Text(pluralStringResource(R.plurals.progress_submitted_guesses, round.guessCount, round.guessCount), color = NumbCrunchGoldCream)
}

@Composable
internal fun ProgressDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit = {},
    buttons: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 460.dp)
            .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(120.dp))
            .verticalScroll(rememberScrollState())) {
            NumbCrunchDialogFrame(R.drawable.game_keypad_wood, title, message, content = content, buttons = buttons)
        }
    }
}

@Composable
fun RoundEntryDialogs(state: RoundEntryUiState, viewModel: RoundEntryViewModel) {
    when {
        state.hasWriteError -> ProgressDialog(
            stringResource(R.string.progress_save_failed_title), stringResource(R.string.progress_save_failed_message),
            onDismiss = viewModel::cancelStart,
        ) {
            GuideAction(stringResource(R.string.progress_retry), viewModel::retryWrite, Modifier.fillMaxWidth())
            TextButton(onClick = viewModel::cancelStart, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.progress_cancel), color = NumbCrunchGoldCream)
            }
        }
        state.isBusy -> ProgressDialog(
            stringResource(R.string.progress_saving_title), stringResource(R.string.progress_saving_message),
            onDismiss = {}, buttons = {},
        )
        state.abandonTarget != null -> ProgressDialog(
            stringResource(R.string.progress_abandon_title), stringResource(R.string.progress_abandon_message),
            onDismiss = viewModel::cancelAbandon,
            content = { RoundSummary(state.abandonTarget) },
        ) {
            GuideAction(stringResource(R.string.progress_abandon_confirm), viewModel::confirmAbandon, Modifier.fillMaxWidth())
            TextButton(onClick = viewModel::cancelAbandon, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.progress_cancel), color = NumbCrunchGoldCream)
            }
        }
        state.choices.isNotEmpty() -> ProgressDialog(
            stringResource(R.string.progress_existing_title), stringResource(R.string.progress_existing_message),
            onDismiss = viewModel::cancelStart,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.choices.forEach { round ->
                        RoundSummary(round)
                        GuideAction(stringResource(R.string.progress_resume), { viewModel.resume(round) }, Modifier.fillMaxWidth())
                        TextButton(onClick = { viewModel.requestAbandon(round) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.progress_abandon_this), color = NumbCrunchGoldCream)
                        }
                    }
                }
            },
        ) {
            GuideAction(stringResource(R.string.progress_cancel), viewModel::cancelStart, Modifier.fillMaxWidth())
        }
    }
}
