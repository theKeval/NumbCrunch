package io.keval.apps.numbcrunch.gameplay.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchChocolateDark
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.domain.cloud.*
import io.keval.apps.numbcrunch.domain.progress.RoundMode
import io.keval.apps.numbcrunch.domain.progress.RoundStatus
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.common.labelRes
import io.keval.apps.numbcrunch.gameplay.game.WoodPanel
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudBackupRoute(modifier: Modifier = Modifier) {
    val binding = LocalCloudBackup.current ?: return
    val state by binding.state.collectAsStateWithLifecycle()
    CloudBackupCard(state, binding.retry, binding.chooseVersion, binding.keepSavedRounds, modifier)
}

@Composable
fun CloudBackupCard(
    state: CloudBackupState,
    onRetry: () -> Unit,
    onChooseVersion: (String, Long, String, String) -> Unit,
    onKeepRounds: (String, Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogGeneration by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(state.accountPlayerId, state.generation, state.choice) {
        if (dialogAccount != state.accountPlayerId || dialogGeneration != state.generation || state.choice == null) dialogAccount = null
    }
    WoodPanel(R.drawable.game_keypad_wood, NumbCrunchChocolateDark.copy(alpha = 0.88f), modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cloud_title), color = NumbCrunchGoldCream, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(when (state.status) {
                BackupStatus.DEVICE_ONLY -> R.string.cloud_device_only
                BackupStatus.SAVING -> R.string.cloud_saving
                BackupStatus.SAVED -> R.string.cloud_saved
                BackupStatus.PENDING -> R.string.cloud_pending
                BackupStatus.NEEDS_ATTENTION -> R.string.cloud_attention
            }), color = NumbCrunchGoldCream, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(when {
                state.choice != null -> R.string.cloud_choice_explanation
                state.invalidData -> R.string.cloud_invalid_explanation
                state.status == BackupStatus.DEVICE_ONLY -> R.string.cloud_device_explanation
                state.status == BackupStatus.SAVED -> R.string.cloud_saved_explanation
                else -> R.string.cloud_pending_explanation
            }), color = NumbCrunchGoldCream, style = MaterialTheme.typography.bodyMedium)
            state.lastSuccessfulBackup?.let {
                Text(stringResource(R.string.cloud_last_backup, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))),
                    color = NumbCrunchGoldCream, style = MaterialTheme.typography.bodySmall)
            }
            if (state.choice != null) TextButton(onClick = {
                dialogAccount = state.accountPlayerId; dialogGeneration = state.generation
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cloud_review), color = NumbCrunchGoldCream) }
            if (state.accountPlayerId != null && state.status != BackupStatus.SAVING) TextButton(onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cloud_retry), color = NumbCrunchGoldCream) }
        }
    }
    val choice = state.choice
    val account = dialogAccount
    if (choice != null && account != null) AlertDialog(
        onDismissRequest = { dialogAccount = null },
        title = { Text(stringResource(R.string.cloud_review)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(R.string.cloud_choice_explanation)) }
                items(choice.roundConflicts, key = { it.roundId }) { conflict ->
                    Column {
                        Text(stringResource(R.string.cloud_different_versions), fontWeight = FontWeight.Bold)
                        conflict.candidates.forEachIndexed { index, candidate ->
                            val allowed = candidate.terminal || conflict.candidates.none { it.terminal }
                            TextButton(onClick = { onChooseVersion(account, dialogGeneration, conflict.roundId, candidate.versionId) },
                                enabled = allowed, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.cloud_choose_version, index + 1, candidateDescription(candidate)))
                            }
                        }
                    }
                }
                if (choice.roundConflicts.isEmpty()) {
                    if (choice.unfinishedRoundIds.size > 1) {
                        item { Text(stringResource(R.string.cloud_choose_first)) }
                        items(choice.progress.versions.filterNot { it.terminal }, key = { it.roundId }) { candidate ->
                            TextButton(onClick = {
                                dialogAccount = null; onKeepRounds(account, dialogGeneration, candidate.roundId)
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(candidateDescription(candidate)) }
                        }
                    } else item {
                        TextButton(onClick = {
                            dialogAccount = null; onKeepRounds(account, dialogGeneration, choice.unfinishedRoundIds.singleOrNull())
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cloud_keep_progress)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { dialogAccount = null }) { Text(stringResource(R.string.cloud_later)) } },
    )
}

@Composable
private fun candidateDescription(version: CloudRoundVersion): String {
    val active = version.unfinished
    val outcome = version.outcome
    val mode = active?.mode ?: requireNotNull(outcome).mode
    val difficulty = active?.difficulty ?: requireNotNull(outcome).difficulty
    val status = active?.status ?: requireNotNull(outcome).status
    val count = active?.guesses?.size?.toLong() ?: requireNotNull(outcome).guessCount
    return stringResource(R.string.cloud_round_description,
        stringResource(if (mode == RoundMode.SINGLE) R.string.cloud_single else R.string.cloud_double),
        stringResource(difficulty.labelRes), count, stringResource(when (status) {
            RoundStatus.ACTIVE, RoundStatus.PAUSED -> R.string.cloud_unfinished
            RoundStatus.WON -> R.string.cloud_won
            RoundStatus.GIVEN_UP -> R.string.cloud_given_up
            RoundStatus.ABANDONED -> R.string.cloud_abandoned
        }))
}
