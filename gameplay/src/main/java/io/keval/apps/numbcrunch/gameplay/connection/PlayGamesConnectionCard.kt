package io.keval.apps.numbcrunch.gameplay.connection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchChocolateDark
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchOrangeTop
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.gameplay.engagement.AchievementsRoute
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.game.WoodPanel
import io.keval.apps.numbcrunch.gameplay.cloud.CloudBackupRoute

/** Place on Home or the player screen; the Activity supplies lifecycle-bound Google actions. */
@Composable
fun PlayGamesConnectionRoute(modifier: Modifier = Modifier) {
    val binding = LocalPlayerConnection.current ?: return
    val session by binding.session.collectAsStateWithLifecycle()
    LaunchedEffect(binding.session) { binding.refreshLocal() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayGamesConnectionCard(session, binding.connect, binding.refresh, binding.selectGuest,
            binding.selectAccount, binding.importGuest)
        CloudBackupRoute()
    }
}

@Composable
fun PlayGamesConnectionCard(
    session: PlayerSession,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onSelectGuest: () -> Unit,
    onSelectAccount: () -> Unit,
    onImportGuest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingAccount by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(session.connectedAccountId) {
        if (confirmingAccount != session.connectedAccountId) confirmingAccount = null
    }
    val statusText = when (session.status) {
        ConnectionStatus.CHECKING -> stringResource(R.string.play_games_checking)
        ConnectionStatus.CONNECTED -> session.player?.displayName?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.play_games_connected_as, it) }
            ?: stringResource(R.string.play_games_connected)
        ConnectionStatus.NOT_CONNECTED -> stringResource(R.string.play_games_not_connected)
        ConnectionStatus.UNAVAILABLE -> stringResource(R.string.play_games_unavailable)
        ConnectionStatus.CANCELLED -> stringResource(R.string.play_games_cancelled)
        ConnectionStatus.ERROR -> stringResource(R.string.play_games_failed)
    }
    WoodPanel(R.drawable.game_keypad_wood, NumbCrunchChocolateDark.copy(alpha = 0.88f), modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.play_games_controller), contentDescription = null, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.play_games_title), color = NumbCrunchGoldCream,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (session.busy) CircularProgressIndicator(Modifier.size(20.dp), color = NumbCrunchGoldCream, strokeWidth = 2.dp)
            }
            Text(statusText, color = NumbCrunchGoldCream,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.play_games_progress_note), color = NumbCrunchGoldCream,
                style = MaterialTheme.typography.bodyMedium)
            if (session.selectedProfile != null) {
                Text(stringResource(if (session.selectedProfile?.pgsPlayerId == null)
                    R.string.play_games_using_guest else R.string.play_games_using_account), color = NumbCrunchGoldCream)
            }
            if (session.localError) Text(stringResource(R.string.play_games_local_error), color = NumbCrunchGoldCream)
            if (session.status != ConnectionStatus.CONNECTED) {
                Button(onClick = onConnect, enabled = !session.busy, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NumbCrunchOrangeTop, contentColor = NumbCrunchChocolateDark)) {
                    Text(stringResource(R.string.play_games_connect))
                }
            } else {
                if (session.unclaimedGuest != null) {
                    TextButton(onClick = { confirmingAccount = session.connectedAccountId }, enabled = !session.busy,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.play_games_import_guest), color = NumbCrunchGoldCream)
                    }
                }
                if (session.selectedProfile?.id != session.accountProfile?.id) {
                    TextButton(onClick = onSelectAccount, enabled = !session.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.play_games_view_account), color = NumbCrunchGoldCream)
                    }
                } else if (session.unclaimedGuest != null) {
                    TextButton(onClick = onSelectGuest, enabled = !session.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.play_games_view_guest), color = NumbCrunchGoldCream)
                    }
                }
                Text(stringResource(R.string.play_games_manage_account), color = NumbCrunchGoldCream,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRefresh, enabled = !session.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.play_games_refresh), color = NumbCrunchGoldCream)
                }
            }
            AchievementsRoute()
        }
    }
    confirmingAccount?.let { accountId ->
        AlertDialog(
            onDismissRequest = { confirmingAccount = null },
            title = { Text(stringResource(R.string.play_games_import_guest)) },
            text = { Text(stringResource(R.string.play_games_import_explanation,
                session.player?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.play_games_title))) },
            confirmButton = { TextButton(onClick = { confirmingAccount = null; onImportGuest(accountId) }) {
                Text(stringResource(R.string.play_games_import_confirm))
            } },
            dismissButton = { TextButton(onClick = { confirmingAccount = null }) {
                Text(stringResource(R.string.play_games_not_now))
            } },
        )
    }
}
