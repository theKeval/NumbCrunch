package io.keval.apps.numbcrunch.domain.gamestats

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Exactly the seven Console properties. Local IDs and clocks are deliberately absent. */
data class RoundCompletedEvent(
    val playerMode: RoundMode,
    val difficulty: Difficulty,
    val outcome: RoundStatus,
    val guessCount: Long,
    val hintUsed: Boolean,
) {
    val hardSoloWin: Boolean get() = playerMode == RoundMode.SINGLE && difficulty == Difficulty.HARD && outcome == RoundStatus.WON
    val unaidedMediumSoloWin: Boolean get() = playerMode == RoundMode.SINGLE && difficulty == Difficulty.MEDIUM &&
        outcome == RoundStatus.WON && !hintUsed

    fun validate() {
        require(outcome.isTerminal && guessCount > 0) { "Game Stats require a resolved started round" }
        require(!hintUsed || difficulty == Difficulty.MEDIUM) { "Invalid hint usage" }
    }

    companion object {
        const val NAME = "roundCompleted_v1"
        /** Call only in the transaction of a fresh local terminal transition, never on import. */
        fun fromLocalRound(round: RoundRecord): RoundCompletedEvent? {
            round.validate()
            if (round.origin != RoundOrigin.LOCAL || !round.status.isTerminal || !round.isPlayed) return null
            return RoundCompletedEvent(round.mode, round.difficulty, round.status, round.guesses.size.toLong(),
                round.hintedGuessIndex != null).also(RoundCompletedEvent::validate)
        }
    }
}

enum class GameStatsDeliveryState { READY, HANDOFF, BUFFERED, UNKNOWN, REJECTED }
data class GameStatsDelivery(val roundId: String, val accountPlayerId: String, val event: RoundCompletedEvent)

/** Installation-local only: never export/import these commands through Saved Games. */
interface GameStatsOutbox {
    fun observePending(accountPlayerId: String): Flow<List<GameStatsDelivery>>
    /** Atomic READY -> HANDOFF before invoking the SDK. Existing HANDOFF/UNKNOWN entries never replay. */
    suspend fun claim(roundId: String, accountPlayerId: String): GameStatsDelivery?
    suspend fun finish(roundId: String, accountPlayerId: String, state: GameStatsDeliveryState)
}

enum class GameStatsRecordResult { BUFFERED, UNKNOWN, REJECTED }
interface GameStatsGateway {
    /** A read-only stable SDK identity check; unavailable/missing identity returns null. */
    suspend fun currentAccountPlayerId(): String?
    /** Recheck identity immediately before handoff. BUFFERED means local SDK cache, never server acceptance. */
    suspend fun record(expectedAccountPlayerId: String, event: RoundCompletedEvent): GameStatsRecordResult
}

/** One serialized delivery owner. Cancellation after claim leaves HANDOFF as an ambiguous tombstone. */
class GameStatsDeliveryController(private val outbox: GameStatsOutbox) {
    private val mutex = Mutex()

    suspend fun deliver(pending: List<GameStatsDelivery>, accountPlayerId: String, gateway: GameStatsGateway) = mutex.withLock {
        if (accountPlayerId.isBlank()) return@withLock
        for (entry in pending) {
            if (entry.accountPlayerId != accountPlayerId) continue
            val current = try { gateway.currentAccountPlayerId() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            if (current != accountPlayerId) return@withLock // Still READY: retry safely when this account returns.
            entry.event.validate()
            val claimed = outbox.claim(entry.roundId, accountPlayerId) ?: continue
            val result = try { gateway.record(accountPlayerId, claimed.event) } catch (cancelled: CancellationException) {
                throw cancelled // HANDOFF remains durable, never replay after ambiguous cancellation/crash.
            } catch (_: Exception) { GameStatsRecordResult.UNKNOWN }
            outbox.finish(claimed.roundId, accountPlayerId, when (result) {
                GameStatsRecordResult.BUFFERED -> GameStatsDeliveryState.BUFFERED
                GameStatsRecordResult.REJECTED -> GameStatsDeliveryState.REJECTED
                GameStatsRecordResult.UNKNOWN -> GameStatsDeliveryState.UNKNOWN
            })
        }
    }
}
