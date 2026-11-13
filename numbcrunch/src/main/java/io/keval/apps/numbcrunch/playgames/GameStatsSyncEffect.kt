package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.gamestats.GameStatsDeliveryController
import io.keval.apps.numbcrunch.domain.gamestats.GameStatsOutbox
import io.keval.apps.numbcrunch.domain.gamestats.GameStatsScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow

/** One foreground collector; gameplay never waits for a Game Stats operation. */
@Composable
fun GameStatsSyncEffect(activity: Activity, session: StateFlow<PlayerSession>, outbox: GameStatsOutbox?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(outbox) { outbox?.let(::GameStatsDeliveryController) }
    val player by session.collectAsStateWithLifecycle()
    val scope = player.player?.id?.let { id -> player.accountProfile?.id?.let { profile ->
        GameStatsScope(id, profile, player.generation).takeIf { it.isCurrent(player) }
    } }
    LaunchedEffect(lifecycle, scope, controller) {
        if (scope == null || controller == null || outbox == null) return@LaunchedEffect
        val stillCurrent = { scope.isCurrent(session.value) && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                // collectLatest would cancel the SDK call when claim() removes its own READY entry.
                outbox.observePending(scope.playerId).collect { pending ->
                    controller.deliver(pending, scope.playerId, AndroidGameStatsGateway(activity, stillCurrent), stillCurrent)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep durable READY/HANDOFF state. A later lifecycle/account refresh may resume READY work.
            }
        }
    }
}
