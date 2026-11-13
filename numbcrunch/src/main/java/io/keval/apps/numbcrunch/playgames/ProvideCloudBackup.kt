package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.keval.apps.numbcrunch.domain.cloud.*
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import io.keval.apps.numbcrunch.gameplay.cloud.CloudBackupBinding
import io.keval.apps.numbcrunch.gameplay.cloud.LocalCloudBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Installs cloud state beneath the connection provider. No Activity escapes the foreground coroutine. */
@Composable
fun ProvideCloudBackup(progress: ProgressRepository, session: StateFlow<PlayerSession>, content: @Composable () -> Unit) {
    val cloud = progress as? CloudSyncRepository
    if (cloud == null) { content(); return }
    val activity = LocalContext.current.activityOrNull()
    if (activity == null) { content(); return }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(cloud) { CloudBackupController(cloud) }
    val requests = remember(cloud) { Channel<Unit>(Channel.CONFLATED) }
    val scope = rememberCoroutineScope()
    fun current(account: String, generation: Long): Boolean = session.value.let {
        it.status == ConnectionStatus.CONNECTED && it.player?.id == account && it.generation == generation &&
            it.selectedProfile?.id == it.accountProfile?.id && it.selectedProfile?.pgsPlayerId == account &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    LaunchedEffect(activity, session, controller) {
        try {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.map {
                    val selected = it.status == ConnectionStatus.CONNECTED && it.selectedProfile?.id == it.accountProfile?.id &&
                        it.selectedProfile?.pgsPlayerId == it.player?.id
                    Triple(it.player?.id.takeIf { _ -> selected }, it.accountProfile?.id, it.generation)
                }
                    .distinctUntilChanged().collectLatest { (account, profileId, generation) ->
                        controller.disconnected()
                        if (account == null || profileId == null) return@collectLatest
                        try {
                            coroutineScope {
                                launch {
                                    progress.observeProgress(profileId).collect {
                                        controller.localProgressChanged()
                                        requests.trySend(Unit)
                                    }
                                }
                                var first = true
                                for (ignored in requests) {
                                    if (!first) delay(1_500)
                                    first = false
                                    val gateway = AndroidSavedGamesGateway(activity, account) { current(account, generation) }
                                    controller.synchronize(account, generation, gateway) { current(account, generation) }
                                    // SDK buffering is durable; retry a bounded number of times, then wait for a user/local/foreground change.
                                    for (wait in listOf(2_000L, 10_000L, 30_000L)) {
                                        if (controller.state.value.status != BackupStatus.PENDING) break
                                        delay(wait)
                                        controller.synchronize(account, generation, gateway) { current(account, generation) }
                                    }
                                }
                            }
                        } finally { controller.disconnected() }
                    }
            }
        } finally { controller.disconnected() }
    }
    CompositionLocalProvider(LocalCloudBackup provides CloudBackupBinding(
        controller.state,
        retry = { requests.trySend(Unit) },
        chooseVersion = { account, generation, roundId, versionId ->
            scope.launch {
                if (current(account, generation)) try {
                    controller.chooseVersion(account, roundId, versionId) { current(account, generation) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (current(account, generation)) controller.choiceFailed() }
            }
        },
        keepSavedRounds = { account, generation, roundId ->
            scope.launch {
                if (current(account, generation)) try {
                    controller.keepSavedRounds(account, roundId) { current(account, generation) }
                    requests.trySend(Unit)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (current(account, generation)) controller.choiceFailed() }
            }
        },
    )) { content() }
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}
