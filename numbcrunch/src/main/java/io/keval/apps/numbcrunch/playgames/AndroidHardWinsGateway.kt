package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.tasks.Task
import io.keval.apps.numbcrunch.domain.engagement.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only all-time public ranks are requested; the SDK picker would also expose misleading period totals. */
class AndroidHardWinsGateway(private val activity: Activity) : HardWinsGateway {
    override suspend fun submit(accountPlayerId: String, score: Long): Long = withTimeout(20_000) {
        require(score > 0)
        verifyPlayer(accountPlayerId)
        val result = PlayGames.getLeaderboardsClient(activity).submitScoreImmediate(BOARD_ID, score).awaitLeaderboard()
        verifyPlayer(accountPlayerId)
        require(result.leaderboardId == BOARD_ID) { "Unexpected leaderboard receipt" }
        requireNotNull(result.getScoreResult(LeaderboardVariant.TIME_SPAN_ALL_TIME)).rawScore
    }

    override suspend fun load(accountPlayerId: String, window: RankingWindow): RankingPage = withTimeout(20_000) {
        verifyPlayer(accountPlayerId)
        val client = PlayGames.getLeaderboardsClient(activity)
        val result = if (window == RankingWindow.TOP) {
            client.loadTopScores(BOARD_ID, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC, 25, true)
        } else {
            client.loadPlayerCenteredScores(BOARD_ID, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC, 25, true)
        }.awaitLeaderboard()
        val buffer = requireNotNull(result.get())
        val rows = try { (0 until buffer.scores.count).map { buffer.scores[it].row() } } finally { buffer.release() }
        val current = client.loadCurrentPlayerLeaderboardScore(BOARD_ID, LeaderboardVariant.TIME_SPAN_ALL_TIME,
            LeaderboardVariant.COLLECTION_PUBLIC).awaitLeaderboard()
        val playerRow = current.get()?.row()
        verifyPlayer(accountPlayerId)
        RankingPage(rows, playerRow, result.isStale || current.isStale)
    }

    private suspend fun verifyPlayer(expected: String) {
        check(PlayGames.getGamesSignInClient(activity).isAuthenticated.awaitLeaderboard().isAuthenticated)
        check(PlayGames.getPlayersClient(activity).currentPlayer.awaitLeaderboard().playerId == expected)
    }

    private fun LeaderboardScore.row() = RankingRow(
        displayRank.takeIf { rank != LeaderboardScore.LEADERBOARD_RANK_UNKNOWN.toLong() && rank > 0 },
        scoreHolderDisplayName.orEmpty(), displayScore.orEmpty(),
    )

    companion object { const val BOARD_ID = "CgkIlrDZkIsQEAIQDA" }
}

private suspend fun <T> Task<T>.awaitLeaderboard(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Leaderboard request unavailable"))
        }
    }
}
