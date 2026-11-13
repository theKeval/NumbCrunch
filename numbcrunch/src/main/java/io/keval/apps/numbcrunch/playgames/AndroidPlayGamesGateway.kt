package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import com.google.android.gms.common.ConnectionResult as GoogleConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import io.keval.apps.numbcrunch.domain.connection.ConnectionResult
import io.keval.apps.numbcrunch.domain.connection.PlayGamesGateway
import io.keval.apps.numbcrunch.domain.connection.PlayGamesPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An Activity-scoped request. Never retain this gateway in a repository or ViewModel. */
class AndroidPlayGamesGateway(private val activity: Activity) : PlayGamesGateway {
    override suspend fun authenticate(interactive: Boolean): ConnectionResult = try {
        withTimeout(if (interactive) 120_000L else 20_000L) {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) != GoogleConnectionResult.SUCCESS) {
                return@withTimeout ConnectionResult.Unavailable
            }
            val signIn = PlayGames.getGamesSignInClient(activity)
            val result = (if (interactive) signIn.signIn() else signIn.isAuthenticated()).awaitResult()
            if (!result.isAuthenticated) return@withTimeout ConnectionResult.NotConnected
            val players = PlayGames.getPlayersClient(activity)
            val player = players.currentPlayer.awaitResult()
            // Account settings may change while the profile request is in flight.
            if (players.currentPlayerId.awaitResult() != player.playerId) return@withTimeout ConnectionResult.Failed
            ConnectionResult.Connected(PlayGamesPlayer(player.playerId, player.displayName.orEmpty()))
        }
    } catch (_: TimeoutCancellationException) {
        ConnectionResult.Failed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ApiException) {
        if (error.statusCode == CommonStatusCodes.CANCELED) ConnectionResult.Cancelled else ConnectionResult.Failed
    } catch (_: Exception) {
        ConnectionResult.Failed
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            when {
                task.isCanceled -> continuation.resumeWithException(ApiException(com.google.android.gms.common.api.Status(CommonStatusCodes.CANCELED)))
                task.isSuccessful -> continuation.resume(task.result)
                else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Play Games request failed"))
            }
        }
    }
}
