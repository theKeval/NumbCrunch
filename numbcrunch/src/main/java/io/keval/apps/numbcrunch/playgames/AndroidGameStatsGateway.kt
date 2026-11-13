package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.playergameevent.PlayerGameEvent
import io.keval.apps.numbcrunch.domain.connection.ConnectionResult
import io.keval.apps.numbcrunch.domain.gamestats.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Activity-scoped SDK adapter. No per-player identifiers or event contents are logged. */
class AndroidGameStatsGateway(private val activity: Activity, private val stillCurrent: () -> Boolean) : GameStatsGateway {
    override suspend fun currentAccountPlayerId(): String? {
        if (!stillCurrent()) return null
        val account = (AndroidPlayGamesGateway(activity).authenticate(false) as? ConnectionResult.Connected)?.player?.id
        return account.takeIf { stillCurrent() }
    }

    override suspend fun record(expectedAccountPlayerId: String, event: RoundCompletedEvent): GameStatsRecordResult {
        return try {
            event.validate()
            if (!isCurrentGameStatsAccount(expectedAccountPlayerId, stillCurrent, ::currentAccountPlayerId))
                return GameStatsRecordResult.UNKNOWN
            val client = PlayGames.getGameStatsClient(activity)
            val payload = PlayerGameEvent.Builder(RoundCompletedEvent.NAME)
                .addProperty("playerMode", event.playerMode.name)
                .addProperty("difficulty", event.difficulty.name)
                .addProperty("outcome", event.outcome.name)
                .addProperty("guessCount", event.guessCount)
                .addProperty("hintUsed", event.hintUsed)
                .addProperty("hardSoloWin", event.hardSoloWin)
                .addProperty("unaidedMediumSoloWin", event.unaidedMediumSoloWin)
                .build()
            if (!stillCurrent()) return GameStatsRecordResult.UNKNOWN
            withTimeout(20_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    if (!stillCurrent()) continuation.resumeWithException(IllegalStateException("Game Stats scope changed"))
                    else client.recordEventImmediate(payload).addOnCompleteListener { task ->
                        if (continuation.isActive) {
                            if (task.isSuccessful) continuation.resume(Unit)
                            else continuation.resumeWithException(task.exception ?: IllegalStateException("Game Stats buffering interrupted"))
                        }
                    }
                }
            }
            // The SDK owns retry after local acceptance. This void request cannot confirm server ingestion.
            if (stillCurrent()) runCatching { client.requestEventsUpload() }
            diagnostic("buffered")
            GameStatsRecordResult.BUFFERED
        } catch (_: TimeoutCancellationException) {
            diagnostic("handoff_unknown"); GameStatsRecordResult.UNKNOWN
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiException) {
            diagnostic("api_failure", error.statusCode)
            if (error.statusCode == CommonStatusCodes.DEVELOPER_ERROR) GameStatsRecordResult.REJECTED else GameStatsRecordResult.UNKNOWN
        } catch (_: IllegalArgumentException) {
            diagnostic("schema_rejected"); GameStatsRecordResult.REJECTED
        } catch (_: Exception) {
            diagnostic("handoff_unknown"); GameStatsRecordResult.UNKNOWN
        }
    }

    private fun diagnostic(result: String, status: Int? = null) {
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            Log.d("NumbCrunchGameStats", "result=$result status=$status")
    }
}
