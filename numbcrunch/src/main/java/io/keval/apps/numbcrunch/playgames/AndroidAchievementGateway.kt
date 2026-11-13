package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import android.content.Intent
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.tasks.Task
import io.keval.apps.numbcrunch.R
import io.keval.apps.numbcrunch.domain.engagement.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Never retained outside the Activity lifecycle; every SDK operation validates the current PGS player. */
class AndroidAchievementGateway(private val activity: Activity) : AchievementGateway {
    private val ids = mapOf(
        AchievementKey.LEARNING_THE_ROPES to activity.getString(R.string.achievement_learning_the_ropes),
        AchievementKey.FIRST_SOLO_SOLVE to activity.getString(R.string.achievement_first_solo_solve),
        AchievementKey.GETTING_THE_HANG_OF_IT to activity.getString(R.string.achievement_getting_the_hang_of_it),
        AchievementKey.DOUBLE_DIGITS to activity.getString(R.string.achievement_double_digits),
        AchievementKey.PERSISTENT_PUZZLER to activity.getString(R.string.achievement_persistent_puzzler),
        AchievementKey.CENTURY_SOLVER to activity.getString(R.string.achievement_century_solver),
        AchievementKey.MEDIUM_MASTERED to activity.getString(R.string.achievement_medium_mastered),
        AchievementKey.HARD_EARNED to activity.getString(R.string.achievement_hard_earned),
        AchievementKey.HARD_REGULAR to activity.getString(R.string.achievement_hard_regular),
        AchievementKey.UNAIDED_DEDUCTION to activity.getString(R.string.achievement_unaided_deduction),
    )
    val isConfigured: Boolean get() = ids.size == AchievementKey.entries.size &&
        ids.values.all { it.matches(Regex("Cgk[A-Za-z0-9_-]+")) } && ids.values.distinct().size == ids.size

    override suspend fun unlock(playerId: String, key: AchievementKey, stillCurrent: () -> Boolean) = withTimeout(20_000L) {
        validatePlayer(playerId, stillCurrent)
        PlayGames.getAchievementsClient(activity).unlockImmediate(id(key)).awaitAchievementResult()
        validatePlayer(playerId, stillCurrent)
    }

    override suspend fun setSteps(playerId: String, key: AchievementKey, steps: Int, stillCurrent: () -> Boolean) = withTimeout(20_000L) {
        require(steps > 0)
        validatePlayer(playerId, stillCurrent)
        PlayGames.getAchievementsClient(activity).setStepsImmediate(id(key), steps).awaitAchievementResult()
        validatePlayer(playerId, stillCurrent)
    }

    suspend fun nativeIntent(playerId: String, stillCurrent: () -> Boolean): Intent = withTimeout(20_000L) {
        if (!isConfigured) throw AchievementConfigurationException()
        validatePlayer(playerId, stillCurrent)
        val intent = PlayGames.getAchievementsClient(activity).achievementsIntent.awaitAchievementResult()
        validatePlayer(playerId, stillCurrent)
        intent
    }

    private fun id(key: AchievementKey): String {
        if (!isConfigured) throw AchievementConfigurationException()
        return ids[key] ?: throw AchievementConfigurationException()
    }

    private suspend fun validatePlayer(expected: String, stillCurrent: () -> Boolean) {
        if (!stillCurrent()) throw StaleAchievementAccountException()
        val player = PlayGames.getPlayersClient(activity).currentPlayer.awaitAchievementResult()
        // Use the same player-ID source as authentication; the separate currentPlayerId API can differ.
        if (!stillCurrent() || player.playerId != expected) throw StaleAchievementAccountException()
    }
}

private suspend fun <T> Task<T>.awaitAchievementResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else {
                val error = task.exception
                if (error is ApiException && error.statusCode in setOf(CommonStatusCodes.DEVELOPER_ERROR, GamesClientStatusCodes.APP_MISCONFIGURED, GamesClientStatusCodes.GAME_NOT_FOUND, GamesClientStatusCodes.ACHIEVEMENT_UNKNOWN, GamesClientStatusCodes.ACHIEVEMENT_NOT_INCREMENTAL, GamesClientStatusCodes.ACHIEVEMENT_UNLOCK_FAILURE)) {
                    continuation.resumeWithException(AchievementConfigurationException())
                } else continuation.resumeWithException(error ?: IllegalStateException("Achievement request failed"))
            }
        }
    }
}
