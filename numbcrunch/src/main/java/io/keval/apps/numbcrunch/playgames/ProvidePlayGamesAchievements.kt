package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.keval.apps.numbcrunch.data.repository.PreferencesAchievementDeliveryStore
import io.keval.apps.numbcrunch.domain.cloud.CloudBackupState
import io.keval.apps.numbcrunch.domain.cloud.cloudProjectionStamp
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.engagement.*
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import io.keval.apps.numbcrunch.gameplay.engagement.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Activity-scoped SDK adapter; only Home/Statistics may activate its delivery surface. */
@Composable
fun ProvidePlayGamesAchievements(
    progress: ProgressRepository,
    session: StateFlow<PlayerSession>,
    cloud: StateFlow<CloudBackupState>,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    if (activity == null) { content(); return }
    val coroutineScope = rememberCoroutineScope()
    val gateway = remember(activity) { AndroidAchievementGateway(activity) }
    val controller = remember(activity) { AchievementSyncController(PreferencesAchievementDeliveryStore(activity)) }
    val screen = remember { MutableStateFlow(AchievementScreenStatus.IDLE) }
    val surfaces = remember { MutableStateFlow<Set<Any>>(emptySet()) }
    val activeSurfaces by surfaces.collectAsStateWithLifecycle()
    val player by session.collectAsStateWithLifecycle()
    val backup by cloud.collectAsStateWithLifecycle()
    var retry by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Cancelling the native panel is a normal return; it never changes game outcomes.
        screen.value = AchievementScreenStatus.IDLE
        retry++
    }
    fun connected(scope: AchievementScope): Boolean {
        return surfaces.value.isNotEmpty() && scope.isCurrent(session.value)
    }
    fun reconciled(scope: AchievementScope, stamp: String?): Boolean {
        return surfaces.value.isNotEmpty() && scope.isReconciled(session.value, cloud.value, stamp)
    }
    val account = player.accountProfile
    val candidate = player.player?.id?.let { id -> account?.id?.let { AchievementScope(id, it, player.generation) } }
    val eligible = candidate?.takeIf { activeSurfaces.isNotEmpty() && !player.busy &&
        player.connectedAccountId == it.profileId && player.selectedProfile?.id == it.profileId && account?.pgsPlayerId == it.playerId &&
        backup.accountPlayerId == it.playerId && backup.generation == it.generation &&
        backup.reconciliationReady && backup.choice == null && !backup.invalidData }
    LaunchedEffect(eligible, backup.reconciledStamp, retry) {
        controller.waitForReconciliation(eligible)
        val scope = eligible ?: return@LaunchedEffect
        if (!gateway.isConfigured) {
            screen.value = AchievementScreenStatus.CONFIGURATION_ERROR
            return@LaunchedEffect
        }
        try {
            progress.observeProgress(scope.profileId).collectLatest { snapshot ->
                if (snapshot.profile.pgsPlayerId != scope.playerId) return@collectLatest
                val (stamp, desired) = withContext(Dispatchers.Default) {
                    snapshot.cloudProjectionStamp() to AchievementCatalog.progressFacts(
                        snapshot.ownerIds, snapshot.completedFacts, snapshot.tutorialCompletions,
                    )
                }
                if (!reconciled(scope, stamp)) {
                    controller.waitForReconciliation(scope)
                    return@collectLatest
                }
                if (screen.value == AchievementScreenStatus.UNAVAILABLE) screen.value = AchievementScreenStatus.IDLE
                controller.reconcile(scope, desired, gateway) { reconciled(scope, stamp) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (connected(scope)) screen.value = AchievementScreenStatus.UNAVAILABLE }
    }
    LaunchedEffect(player.generation, player.selectedProfile?.id) { screen.value = AchievementScreenStatus.IDLE }
    val binding = remember(session, cloud, launcher) {
        AchievementsBinding(
            sync = controller.state,
            screen = screen,
            retry = { retry++ },
            surfaceActive = { token, active -> surfaces.value = if (active) surfaces.value + token else surfaces.value - token },
            open = {
                val current = session.value
                val scope = current.player?.id?.let { id -> current.connectedAccountId?.let { AchievementScope(id, it, current.generation) } }
                if (scope != null && connected(scope) && screen.value != AchievementScreenStatus.OPENING) {
                    screen.value = AchievementScreenStatus.OPENING
                    coroutineScope.launch {
                        var launched = false
                        try {
                            val intent = gateway.nativeIntent(scope.playerId) { connected(scope) }
                            if (connected(scope)) { launcher.launch(intent); launched = true }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: StaleAchievementAccountException) { /* The new generation owns the UI. */ }
                        catch (_: AchievementConfigurationException) {
                            if (connected(scope)) screen.value = AchievementScreenStatus.CONFIGURATION_ERROR
                        } catch (_: Exception) {
                            if (connected(scope)) screen.value = AchievementScreenStatus.UNAVAILABLE
                        } finally {
                            if (!launched && screen.value == AchievementScreenStatus.OPENING && session.value.generation == scope.generation) {
                                screen.value = AchievementScreenStatus.IDLE
                            }
                        }
                    }
                }
            },
        )
    }
    CompositionLocalProvider(LocalAchievements provides binding, content = content)
}
