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
    fun current(context: HardWinsContext): Boolean {
        val session = sessions.value
        return !session.busy && session.status == ConnectionStatus.CONNECTED &&
            session.generation == context.generation && session.selectedProfile?.id == context.localProfileId &&
            session.connectedAccountId == context.localProfileId && session.player?.id == context.accountPlayerId &&
            session.accountProfile?.pgsPlayerId == context.accountPlayerId
    }
    fun reconciled(context: HardWinsContext, stamp: String?): Boolean {
        val backup = backups.value
        return current(context) && stamp != null && backup.accountPlayerId == context.accountPlayerId &&
            backup.generation == context.generation && backup.reconciliationReady && !backup.invalidData &&
            backup.choice == null && backup.reconciledStamp == stamp
    }
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
                    val stamp = it.cloudProjectionStamp()
                    val reconciled = playerId != null && !session.busy && backup.accountPlayerId == playerId &&
                        backup.generation == session.generation && backup.reconciliationReady && !backup.invalidData && backup.choice == null &&
                        backup.reconciledStamp == stamp
                    HardWinsContext(it.profile.id, session.generation,
                        LifetimeHardWins.scoreFacts(it.ownerIds, it.completedFacts), playerId, reconciled) to stamp
                }.getOrNull() }
            }
        }.collectLatest { projection ->
            val context = projection?.first
            controller.setContext(context)
            controller.reconcile(gateway) { reconciled(it, projection?.second) }
        }
    }
    CompositionLocalProvider(LocalHardWins provides HardWinsBinding(
        controller.state,
        load = { window -> scope.launch { controller.load(gateway, window, ::current) } },
        retrySubmission = { retry++ },
    ), content = content)
}
