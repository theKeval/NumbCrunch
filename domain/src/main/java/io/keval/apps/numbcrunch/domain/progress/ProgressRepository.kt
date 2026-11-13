package io.keval.apps.numbcrunch.domain.progress

import kotlinx.coroutines.flow.Flow

/** Database errors propagate to the caller; UI must keep the prior committed round and offer retry. */
interface ProgressRepository {
    suspend fun guestProfile(): PlayerProfile
    suspend fun profileForPlayer(pgsPlayerId: String): PlayerProfile
    fun observeRounds(ownerId: String): Flow<List<RoundRecord>>
    suspend fun findRound(ownerId: String, roundId: String): RoundRecord?
    suspend fun unfinishedRound(ownerId: String): RoundRecord?
    suspend fun createRound(round: RoundRecord): RoundRecord
    suspend fun updateRound(round: RoundRecord, expectedRevision: Long): RoundRecord

    /** Import is atomic and idempotent. Conflicting copies require explicit resolution by the sync layer. */
    suspend fun importRounds(ownerId: String, rounds: List<RoundRecord>)

    /** A legacy SavedStateHandle may be imported once per installation; returns null if already consumed. */
    suspend fun importLegacyRound(round: RoundRecord): RoundRecord?

    /** Complete facts are retained. No compaction is allowed before an acknowledged checkpoint protocol exists. */
    suspend fun exportProgress(ownerId: String): ProgressSnapshot
}

data class ProgressSnapshot(
    val schemaVersion: Int = 1,
    val profile: PlayerProfile,
    val rounds: List<RoundRecord>,
) {
    val statistics: PlayerStatistics get() = PlayerStatistics.fromRounds(rounds)
}

class RoundConflictException : IllegalStateException("Round changed; reload before retrying")
class UnfinishedRoundException : IllegalStateException("Resume or finish the existing round first")
