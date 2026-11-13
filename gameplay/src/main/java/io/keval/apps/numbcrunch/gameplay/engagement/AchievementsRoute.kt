package io.keval.apps.numbcrunch.gameplay.engagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchChocolateDark
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchOrangeTop
import io.keval.apps.numbcrunch.domain.engagement.AchievementSyncStatus
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.connection.LocalPlayerConnection
import io.keval.apps.numbcrunch.gameplay.cloud.LocalCloudBackup
import kotlinx.coroutines.awaitCancellation

/** Only resumed Home/Statistics surfaces permit achievement delivery; gameplay never mounts this. */
@Composable
fun AchievementsRoute(modifier: Modifier = Modifier) {
    val binding = LocalAchievements.current ?: return
    val connection = LocalPlayerConnection.current ?: return
    val session by connection.session.collectAsStateWithLifecycle()
    val sync by binding.sync.collectAsStateWithLifecycle()
    val screen by binding.screen.collectAsStateWithLifecycle()
    val token = remember { Any() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(binding, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            binding.surfaceActive(token, true)
            try { awaitCancellation() } finally { binding.surfaceActive(token, false) }
        }
    }
    val connected = session.connectedAccountId != null
    val backup = LocalCloudBackup.current?.state?.collectAsStateWithLifecycle()?.value
    val ready = backup?.let { it.reconciliationReady && it.choice == null && !it.invalidData &&
        it.generation == session.generation && it.accountPlayerId == session.player?.id } == true
    val currentSync = sync.takeIf { it.scope?.generation == session.generation && it.scope?.playerId == session.player?.id }
    val message = when {
        !connected -> R.string.achievements_connect_explanation
        screen == AchievementScreenStatus.CONFIGURATION_ERROR || currentSync?.status == AchievementSyncStatus.CONFIGURATION_ERROR -> R.string.achievements_configuration_error
        screen == AchievementScreenStatus.UNAVAILABLE -> R.string.achievements_unavailable
        screen == AchievementScreenStatus.OPENING -> R.string.achievements_opening
        !ready -> R.string.achievements_waiting
        currentSync?.status == AchievementSyncStatus.RETRY -> R.string.achievements_pending
        currentSync?.status == AchievementSyncStatus.SYNCING -> R.string.achievements_syncing
        currentSync?.status == AchievementSyncStatus.SYNCED -> R.string.achievements_synced
        else -> R.string.achievements_waiting
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(message), color = NumbCrunchGoldCream,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Button(
            onClick = if (connected) binding.open else connection.connect,
            enabled = !session.busy && screen != AchievementScreenStatus.OPENING,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NumbCrunchOrangeTop, contentColor = NumbCrunchChocolateDark),
        ) { Text(stringResource(if (connected) R.string.achievements_view else R.string.achievements_connect)) }
        if (connected && currentSync?.status == AchievementSyncStatus.RETRY) {
            TextButton(onClick = binding.retry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.achievements_retry), color = NumbCrunchGoldCream)
            }
        }
    }
}
