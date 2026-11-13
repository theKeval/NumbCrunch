package io.keval.apps.numbcrunch.gameplay.game

import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.CompletableDeferred

internal class TestProgressRepository : ProgressRepository {
    private val profiles = mutableMapOf("guest" to PlayerProfile("guest", null, 1))
    private val selected = MutableStateFlow(profiles.getValue("guest"))
    private val claims = MutableStateFlow<List<GuestProgressClaim>>(emptyList())
    val tutorials = MutableStateFlow<List<TutorialCompletion>>(emptyList())
    val outcomes = MutableStateFlow<List<OutcomeFact>>(emptyList())
    val associated = MutableStateFlow<List<PlayerProfile>>(emptyList())
    val records = MutableStateFlow<List<RoundRecord>>(emptyList())
    var failNextWrite = false
    var failAfterNextCreate = false
    var failReads = false
    var updateGate: CompletableDeferred<Unit>? = null
    private var importedLegacy = false
    override suspend fun guestProfile(): PlayerProfile = profiles.values.firstOrNull { profile ->
        profile.pgsPlayerId == null && claims.value.none { it.guestProfileId == profile.id }
    } ?: PlayerProfile("guest-${profiles.size}", null, 1).also { profiles[it.id] = it }
    override suspend fun profileForPlayer(pgsPlayerId: String) =
        profiles.getOrPut("account-$pgsPlayerId") { PlayerProfile("account-$pgsPlayerId", pgsPlayerId, 1) }
    override suspend fun activatePlayerProfile(pgsPlayerId: String) = selectProfile(profileForPlayer(pgsPlayerId).id)
    override suspend fun selectedProfile() = selected.value
    override suspend fun selectProfile(profileId: String) = profiles.getValue(profileId).also { selected.value = it }
    override fun observeSelectedProfile() = selected
    override suspend fun claimGuestProgress(guestProfileId: String, accountProfileId: String): GuestProgressClaim {
        val claim = GuestProgressClaim(guestProfileId, accountProfileId, 1)
        claims.value = claims.value + claim
        selectProfile(accountProfileId)
        return claim
    }
    private fun owners(ownerId: String) = setOf(ownerId) + claims.value.filter { it.accountProfileId == ownerId }.map { it.guestProfileId }
    override fun observeRounds(ownerId: String) = flow {
        if (failReads) error("Injected read failure")
        emitAll(combine(records, claims) { rounds, _ -> rounds.filter { it.ownerId in owners(ownerId) } })
    }
    override fun observeProgress(ownerId: String) = flow {
        if (failReads) error("Injected read failure")
        emitAll(combine(records, claims, tutorials, outcomes, associated) { _, _, _, _, _ -> exportProgress(ownerId) })
    }
    override suspend fun recordTutorialCompletion(completion: TutorialCompletion) {
        writeCheck()
        completion.validate()
        val previous = tutorials.value.find { it.ownerId == completion.ownerId && it.tutorialVersion == completion.tutorialVersion }
        tutorials.value = tutorials.value.filterNot { it == previous } + listOfNotNull(
            if (previous == null || completion.completedAt < previous.completedAt) completion else previous,
        )
    }
    override suspend fun findRound(ownerId: String, roundId: String) = records.value.find { it.id == roundId && it.ownerId in owners(ownerId) }
    override suspend fun unfinishedRounds(ownerId: String) = records.value.filter { it.ownerId in owners(ownerId) && !it.status.isTerminal }
    override suspend fun unfinishedRound(ownerId: String) = unfinishedRounds(ownerId).let {
        if (it.size > 1) throw UnfinishedRoundException()
        it.singleOrNull()
    }
    override suspend fun createRound(round: RoundRecord): RoundRecord {
        writeCheck()
        round.validate()
        records.value.find { it.id == round.id }?.let {
            if (it != round) throw RoundConflictException()
            return it
        }
        if (unfinishedRounds(round.ownerId).isNotEmpty()) throw UnfinishedRoundException()
        records.value += round
        if (failAfterNextCreate) {
            failAfterNextCreate = false
            error("Injected lost create acknowledgement")
        }
        return round
    }
    override suspend fun updateRound(round: RoundRecord, expected: RoundRecord): RoundRecord {
        updateGate?.await()
        writeCheck()
        val previous = records.value.singleOrNull { it.id == round.id } ?: throw RoundConflictException()
        if (previous == round) return previous
        if (previous != expected) throw RoundConflictException()
        previous.validateSuccessor(round)
        records.value = records.value.map { if (it.id == round.id) round else it }
        return round
    }
    override suspend fun importRounds(ownerId: String, rounds: List<RoundRecord>) {
        writeCheck()
        rounds.forEach { it.validate() }
        records.value = (records.value + rounds).distinctBy { it.id }
    }
    override suspend fun importLegacyRound(round: RoundRecord): RoundRecord? {
        if (importedLegacy || records.value.isNotEmpty()) return null
        writeCheck()
        round.validate()
        importedLegacy = true
        records.value += round
        return round
    }
    override suspend fun exportProgress(ownerId: String) = ProgressSnapshot(
        profile = profiles.getValue(ownerId), rounds = records.value.filter { it.ownerId in owners(ownerId) },
        guestClaims = claims.value.filter { it.accountProfileId == ownerId },
        associatedProfiles = associated.value.filter { it.pgsPlayerId == profiles.getValue(ownerId).pgsPlayerId },
        outcomes = outcomes.value.filter { it.ownerId in owners(ownerId) || associated.value.any { p -> p.id == it.ownerId && p.pgsPlayerId == profiles.getValue(ownerId).pgsPlayerId } },
        tutorialCompletions = tutorials.value.filter { it.ownerId in owners(ownerId) },
    )
    private fun writeCheck() {
        if (failNextWrite) {
            failNextWrite = false
            error("Injected write failure")
        }
    }
}
