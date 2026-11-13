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
            withContext(Dispatchers.Default) {
                runCatching { hardWinsProjection(snapshot, session, backup) }.getOrNull()
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

internal fun hardWinsProjection(
    snapshot: ProgressSnapshot?, session: PlayerSession, backup: CloudBackupState,
): Pair<HardWinsContext, String?>? {
    val progress = snapshot?.takeIf { it.profile.id == session.selectedProfile?.id } ?: return null
    val wins = LifetimeHardWins.scoreFacts(progress.ownerIds, progress.completedFacts)
    val playerId = session.player?.id?.takeIf { id ->
        session.status == ConnectionStatus.CONNECTED &&
            session.accountProfile?.id == progress.profile.id && progress.profile.pgsPlayerId == id
    }
    // Offline/local totals do not depend on whether this profile can form a cloud payload.
    val stamp = if (playerId == null) null else runCatching { progress.cloudProjectionStamp() }.getOrNull()
    val reconciled = playerId != null && stamp != null && !session.busy && backup.accountPlayerId == playerId &&
        backup.generation == session.generation && backup.reconciliationReady && !backup.invalidData &&
        backup.choice == null && backup.reconciledStamp == stamp
    return HardWinsContext(progress.profile.id, session.generation, wins, playerId, reconciled) to stamp
}
