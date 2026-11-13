package io.keval.apps.numbcrunch.domain.progress

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion

/** Database errors propagate to the caller; UI must keep the prior committed round and offer retry. */
interface ProgressRepository {
    suspend fun guestProfile(): PlayerProfile
    suspend fun profileForPlayer(pgsPlayerId: String): PlayerProfile
    /** Switch only when the authenticated account changes; preserve deliberate local selection on reconnect. */
    suspend fun activatePlayerProfile(pgsPlayerId: String): PlayerProfile
    suspend fun selectedProfile(): PlayerProfile
    suspend fun selectProfile(profileId: String): PlayerProfile
    fun observeSelectedProfile(): Flow<PlayerProfile>
    suspend fun claimGuestProgress(guestProfileId: String, accountProfileId: String): GuestProgressClaim
    fun observeRounds(ownerId: String): Flow<List<RoundRecord>>
    fun observeProgress(ownerId: String): Flow<ProgressSnapshot> = observeRounds(ownerId).map { exportProgress(ownerId) }
    suspend fun recordTutorialCompletion(completion: TutorialCompletion)
    suspend fun findRound(ownerId: String, roundId: String): RoundRecord?
    suspend fun unfinishedRound(ownerId: String): RoundRecord?
    suspend fun unfinishedRounds(ownerId: String): List<RoundRecord>
    suspend fun createRound(round: RoundRecord): RoundRecord
    /** Compare the complete previous record atomically: cloud forks can share a numeric revision. */
    suspend fun updateRound(round: RoundRecord, expected: RoundRecord): RoundRecord

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
    val guestClaims: List<GuestProgressClaim> = emptyList(),
    val associatedProfiles: List<PlayerProfile> = emptyList(),
    val outcomes: List<OutcomeFact> = emptyList(),
    val tutorialCompletions: List<TutorialCompletion> = emptyList(),
) {
    val ownerIds: Set<String> get() {
        require(associatedProfiles.all { it.pgsPlayerId != null && it.pgsPlayerId == profile.pgsPlayerId }) { "Wrong associated account" }
        val profiles = setOf(profile.id) + associatedProfiles.map { it.id }
        return profiles + guestClaims.map {
            require(it.accountProfileId in profiles) { "Wrong claim account" }
            it.guestProfileId
        }
    }
    val completedFacts: List<OutcomeFact> get() {
        val owners = ownerIds
        return (outcomes + rounds.filter { it.status.isTerminal }.map(OutcomeFact::fromRound))
            .groupBy { it.id }.values.map { copies ->
                require(copies.all { it == copies.first() }) { "Conflicting completed outcome" }
                copies.first().also { it.validate(); require(it.ownerId in owners) { "Wrong outcome owner" } }
            }
    }
    val statistics: PlayerStatistics get() = statisticsFor()
    fun statisticsFor(filter: StatisticsFilter = StatisticsFilter()): PlayerStatistics {
        val owners = ownerIds
        require(rounds.all { it.ownerId in owners } && outcomes.all { it.ownerId in owners }) { "Wrong progress owner" }
        return PlayerStatistics.fromFacts(completedFacts,
            rounds.filterNot { it.status.isTerminal }, filter)
    }

}

/** Consent links an immutable guest namespace to exactly one account; it never authorizes event replay. */
data class GuestProgressClaim(val guestProfileId: String, val accountProfileId: String, val claimedAt: Long)

class RoundConflictException : IllegalStateException("Round changed; reload before retrying")
class UnfinishedRoundException : IllegalStateException("Resume or finish the existing round first")
