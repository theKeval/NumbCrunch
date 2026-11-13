package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.gamestats.GameStatsDeliveryController
import io.keval.apps.numbcrunch.domain.gamestats.GameStatsOutbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/** One foreground collector; gameplay never waits for a Game Stats operation. */
@Composable
fun GameStatsSyncEffect(activity: Activity, session: PlayerSession, outbox: GameStatsOutbox?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(outbox) { outbox?.let(::GameStatsDeliveryController) }
    val account = session.player?.id.takeIf {
        session.status == ConnectionStatus.CONNECTED && !session.busy &&
            session.accountProfile?.id == session.selectedProfile?.id
    }
    LaunchedEffect(lifecycle, session.generation, account, controller) {
        if (account == null || controller == null || outbox == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                // collectLatest would cancel the SDK call when claim() removes its own READY entry.
                outbox.observePending(account).collect { pending ->
                    controller.deliver(pending, account, AndroidGameStatsGateway(activity))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep durable READY/HANDOFF state. A later lifecycle/account refresh may resume READY work.
            }
        }
    }
}
