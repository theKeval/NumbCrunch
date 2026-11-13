package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.android.gms.common.ConnectionResult as GoogleConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import io.keval.apps.numbcrunch.domain.connection.ConnectionResult
import io.keval.apps.numbcrunch.domain.connection.PlayGamesGateway
import io.keval.apps.numbcrunch.domain.connection.PlayGamesPlayer
import io.keval.apps.numbcrunch.domain.connection.readStablePlayGamesPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An Activity-scoped request. Never retain this gateway in a repository or ViewModel. */
class AndroidPlayGamesGateway(private val activity: Activity) : PlayGamesGateway {
    override suspend fun authenticate(interactive: Boolean): ConnectionResult {
        var stage = "availability"
        return try {
        withTimeout(if (interactive) 120_000L else 20_000L) {
            val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
            diagnostic(stage, "checked", availability)
            if (availability != GoogleConnectionResult.SUCCESS) return@withTimeout ConnectionResult.Unavailable
            val signIn = PlayGames.getGamesSignInClient(activity)
            stage = if (interactive) "manual_sign_in" else "authentication_check"
            val result = (if (interactive) signIn.signIn() else signIn.isAuthenticated()).awaitResult()
            diagnostic(stage, if (result.isAuthenticated) "authenticated" else "not_authenticated")
            if (!result.isAuthenticated) return@withTimeout ConnectionResult.NotConnected
            val players = PlayGames.getPlayersClient(activity)
            stage = "current_player"
            // Use Player.playerId consistently for identity and profile data. Do not compare it to
            // the separate getCurrentPlayerId representation, which can differ on real devices.
            val identity = readStablePlayGamesPlayer {
                val player = players.currentPlayer.awaitResult()
                PlayGamesPlayer(player.playerId, player.displayName.orEmpty())
            }
            stage = "player_identity_check"
            diagnostic(stage, if (identity is ConnectionResult.Connected) "connected" else "changed_or_missing")
            identity
        }
    } catch (_: TimeoutCancellationException) {
        diagnostic(stage, "timeout")
        ConnectionResult.Failed
    } catch (cancelled: CancellationException) {
        diagnostic(stage, "lifecycle_cancelled")
        throw cancelled
    } catch (error: ApiException) {
        diagnostic(stage, "api_failure", error.statusCode)
        if (error.statusCode == CommonStatusCodes.CANCELED) ConnectionResult.Cancelled else ConnectionResult.Failed
    } catch (_: Exception) {
        diagnostic(stage, "failure")
        ConnectionResult.Failed
    }
    }

    private fun diagnostic(stage: String, result: String, statusCode: Int? = null) {
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("NumbCrunchPlayGames", "stage=$stage result=$result status=$statusCode")
        }
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
