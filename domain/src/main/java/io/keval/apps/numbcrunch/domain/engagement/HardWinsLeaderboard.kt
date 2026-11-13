package io.keval.apps.numbcrunch.domain.engagement

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RankingWindow { TOP, NEAR_ME }
enum class RankingStatus { DISCONNECTED, IDLE, LOADING, READY, ERROR }
enum class ScoreDelivery { LOCAL, WAITING_FOR_BACKUP, PENDING, ACKNOWLEDGED }

data class RankingRow(val rank: String?, val playerName: String, val score: String)
data class RankingPage(val rows: List<RankingRow>, val currentPlayer: RankingRow?, val stale: Boolean = false)

/** A validated projection of one selected player, never an SDK score used as synthetic local history. */
data class HardWinsContext(
    val localProfileId: String,
    val generation: Long,
    val localWins: Long,
    val accountPlayerId: String? = null,
    val reconciled: Boolean = false,
) {
    init { require(localProfileId.isNotBlank() && localWins >= 0) }
}

data class HardWinsLeaderboardState(
    val context: HardWinsContext? = null,
    val window: RankingWindow = RankingWindow.TOP,
    val status: RankingStatus = RankingStatus.DISCONNECTED,
    val delivery: ScoreDelivery = ScoreDelivery.LOCAL,
    val page: RankingPage? = null,
)

/** Implementations check the SDK's current player before and after every asynchronous operation. */
interface HardWinsGateway {
    suspend fun submit(accountPlayerId: String, score: Long): Long
    suspend fun load(accountPlayerId: String, window: RankingWindow): RankingPage
}

class HardWinsLeaderboard {
    private val mutableState = MutableStateFlow(HardWinsLeaderboardState())
    val state = mutableState.asStateFlow()
    private val submissions = Mutex()
    private val acknowledged = mutableMapOf<String, Long>()
    private var contextRevision = 0L
    private var loadRevision = 0L

    fun setContext(context: HardWinsContext?) {
        if (context == mutableState.value.context) return
        val previous = mutableState.value
        contextRevision++
        loadRevision++
        val samePlayer = previous.context?.localProfileId == context?.localProfileId &&
            previous.context?.accountPlayerId == context?.accountPlayerId &&
            previous.context?.generation == context?.generation
        mutableState.value = previous.copy(
            context = context,
            status = if (context?.accountPlayerId == null) RankingStatus.DISCONNECTED else RankingStatus.IDLE,
            delivery = delivery(context),
            page = previous.page.takeIf { samePlayer && context?.accountPlayerId != null },
        )
    }

    private fun delivery(context: HardWinsContext?): ScoreDelivery = when {
        context?.accountPlayerId == null || context.localWins == 0L -> ScoreDelivery.LOCAL
        !context.reconciled -> ScoreDelivery.WAITING_FOR_BACKUP
        (acknowledged[context.accountPlayerId] ?: -1L) >= context.localWins -> ScoreDelivery.ACKNOWLEDGED
        else -> ScoreDelivery.PENDING
    }

    /** Repeated totals are safe after a process restart; PGS keeps the best score. */
    suspend fun reconcile(gateway: HardWinsGateway) = submissions.withLock {
        val context = mutableState.value.context ?: return@withLock
        val account = context.accountPlayerId ?: return@withLock
        if (!context.reconciled || context.localWins == 0L || delivery(context) == ScoreDelivery.ACKNOWLEDGED) return@withLock
        val request = contextRevision
        try {
            val accepted = gateway.submit(account, context.localWins)
            require(accepted >= context.localWins) { "Score acknowledgement is below submitted total" }
            if (request != contextRevision) return@withLock
            acknowledged[account] = maxOf(acknowledged[account] ?: 0L, accepted)
            mutableState.value = mutableState.value.copy(delivery = ScoreDelivery.ACKNOWLEDGED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (request == contextRevision) mutableState.value = mutableState.value.copy(delivery = ScoreDelivery.PENDING)
        }
    }

    suspend fun load(gateway: HardWinsGateway, window: RankingWindow = mutableState.value.window) {
        val context = mutableState.value.context ?: return
        val account = context.accountPlayerId ?: return
        val request = ++loadRevision
        val ownerRevision = contextRevision
        mutableState.value = mutableState.value.copy(window = window, status = RankingStatus.LOADING, page = null)
        try {
            val page = gateway.load(account, window)
            if (request == loadRevision && ownerRevision == contextRevision) {
                mutableState.value = mutableState.value.copy(status = RankingStatus.READY, page = page)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (request == loadRevision && ownerRevision == contextRevision) {
                mutableState.value = mutableState.value.copy(status = RankingStatus.ERROR, page = null)
            }
        }
    }
}
