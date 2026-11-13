package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.keval.apps.numbcrunch.domain.cloud.CloudBackupState
import io.keval.apps.numbcrunch.domain.cloud.cloudProjectionStamp
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.engagement.HardWinsContext
import io.keval.apps.numbcrunch.domain.engagement.HardWinsLeaderboard
import io.keval.apps.numbcrunch.domain.engagement.LifetimeHardWins
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import io.keval.apps.numbcrunch.domain.progress.ProgressSnapshot
import io.keval.apps.numbcrunch.gameplay.leaderboard.HardWinsBinding
import io.keval.apps.numbcrunch.gameplay.leaderboard.LocalHardWins
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun ProvideHardWinsLeaderboard(
    activity: Activity,
    progress: ProgressRepository,
    sessions: StateFlow<PlayerSession>,
    backups: StateFlow<CloudBackupState>,
    content: @Composable () -> Unit,
) {
    val controller = remember { HardWinsLeaderboard() }
    val gateway = remember(activity) { AndroidHardWinsGateway(activity) }
    val scope = rememberCoroutineScope()
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(progress, sessions, backups, retry) {
        val snapshots = progress.observeSelectedProfile().flatMapLatest { progress.observeProgress(it.id) }
            .map<ProgressSnapshot, ProgressSnapshot?> { it }.catch { emit(null) }
        combine(snapshots, sessions, backups) { snapshot, session, backup ->
            snapshot?.takeIf { it.profile.id == session.selectedProfile?.id }?.let {
                withContext(Dispatchers.Default) { runCatching {
                    val playerId = session.player?.id?.takeIf { id ->
                        session.status == ConnectionStatus.CONNECTED &&
                            session.accountProfile?.id == it.profile.id && it.profile.pgsPlayerId == id
                    }
                    val reconciled = playerId != null && backup.accountPlayerId == playerId &&
                        backup.generation == session.generation && backup.reconciliationReady && backup.choice == null &&
                        backup.reconciledStamp == it.cloudProjectionStamp()
                    HardWinsContext(it.profile.id, session.generation,
                        LifetimeHardWins.scoreFacts(it.ownerIds, it.completedFacts), playerId, reconciled)
                }.getOrNull() }
            }
        }.collectLatest { context ->
            controller.setContext(context)
            controller.reconcile(gateway)
        }
    }
    CompositionLocalProvider(LocalHardWins provides HardWinsBinding(
        controller.state,
        load = { window -> scope.launch { controller.load(gateway, window) } },
        retrySubmission = { retry++ },
    ), content = content)
}
