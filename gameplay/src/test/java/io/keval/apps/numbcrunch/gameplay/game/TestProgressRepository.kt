package io.keval.apps.numbcrunch.gameplay.game

import io.keval.apps.numbcrunch.domain.progress.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class TestProgressRepository : ProgressRepository {
    private val profile = PlayerProfile("guest", null, 1)
    val records = MutableStateFlow<List<RoundRecord>>(emptyList())
    var failNextWrite = false
    private var importedLegacy = false
    override suspend fun guestProfile() = profile
    override suspend fun profileForPlayer(pgsPlayerId: String) = PlayerProfile("associated", pgsPlayerId, 1)
    override fun observeRounds(ownerId: String) = records.map { rounds -> rounds.filter { it.ownerId == ownerId } }
    override suspend fun findRound(ownerId: String, roundId: String) = records.value.find { it.id == roundId && it.ownerId == ownerId }
    override suspend fun unfinishedRound(ownerId: String) = records.value.find { it.ownerId == ownerId && !it.status.isTerminal }
    override suspend fun createRound(round: RoundRecord): RoundRecord {
        writeCheck()
        round.validate()
        if (unfinishedRound(round.ownerId) != null) throw UnfinishedRoundException()
        records.value += round
        return round
    }
    override suspend fun updateRound(round: RoundRecord, expectedRevision: Long): RoundRecord {
        writeCheck()
        val previous = records.value.single { it.id == round.id }
        if (previous == round) return previous
        if (previous.revision != expectedRevision) throw RoundConflictException()
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
    override suspend fun exportProgress(ownerId: String) = ProgressSnapshot(profile = profile, rounds = records.value)
    private fun writeCheck() {
        if (failNextWrite) {
            failNextWrite = false
            error("Injected write failure")
        }
    }
}
