package io.keval.apps.numbcrunch.domain.cloud

import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion
import io.keval.apps.numbcrunch.domain.progress.*

/** Account binding is supplied by the authenticated platform adapter, never by an arbitrary file. */
data class CloudProgress(
    val accountPlayerId: String,
    val profiles: List<PlayerProfile>,
    val guestClaims: List<GuestProgressClaim> = emptyList(),
    val versions: List<CloudRoundVersion> = emptyList(),
    val tutorialCompletions: List<TutorialCompletion> = emptyList(),
) {
    val ownerIds: Set<String> get() = profiles.map { it.id }.toSet() + guestClaims.map { it.guestProfileId }

    fun validate(expectedAccountPlayerId: String = accountPlayerId) {
        require(accountPlayerId.isNotBlank() && accountPlayerId == expectedAccountPlayerId) { "Wrong cloud account" }
        require(profiles.isNotEmpty() && profiles.map { it.id }.distinct().size == profiles.size) { "Invalid profile namespaces" }
        profiles.forEach {
            require(it.id.isNotBlank() && it.pgsPlayerId == accountPlayerId && it.trackingStartedAt >= 0) { "Unbound profile" }
        }
        val profileIds = profiles.map { it.id }.toSet()
        require(guestClaims.map { it.guestProfileId }.distinct().size == guestClaims.size) { "Conflicting guest claims" }
        guestClaims.forEach {
            require(it.guestProfileId.isNotBlank() && it.guestProfileId !in profileIds &&
                it.accountProfileId in profileIds && it.claimedAt >= 0) { "Invalid guest claim" }
        }
        versions.forEach { it.validate(); require(it.ownerId in ownerIds) { "Unclaimed round namespace" } }
        require(versions.map { it.versionId }.distinct().size == versions.size) { "Duplicate cloud version" }
        versions.groupBy { it.roundId }.values.forEach { candidates ->
            require(candidates.map { it.immutableFacts() }.distinct().size == 1) { "Immutable round identity changed" }
            require(candidates.mapNotNull { it.unfinished?.secret }.distinct().size <= 1) { "Round secret changed" }
        }
        val byVersion = versions.associateBy { it.versionId }
        versions.forEach { version ->
            require(version.ancestorIds.mapNotNull(byVersion::get).all { it.roundId == version.roundId }) { "Ancestor from another round" }
        }
        tutorialCompletions.forEach { it.validate(); require(it.ownerId in ownerIds) { "Unclaimed tutorial namespace" } }
    }

    companion object {
        /** Initial projection only. Later saves must advance the observed version to retain ancestry. */
        fun fromSnapshot(snapshot: ProgressSnapshot, tutorials: List<TutorialCompletion> = snapshot.tutorialCompletions): CloudProgress {
            require(snapshot.schemaVersion == 1) { "Unsupported local progress schema" }
            val account = requireNotNull(snapshot.profile.pgsPlayerId) { "Guest-only progress cannot upload" }
            val rounds = snapshot.rounds.groupBy { it.id }.values.map { copies ->
                require(copies.all { it == copies.first() }) { "Unresolved local round" }
                copies.first()
            }
            val versions = (rounds.map(CloudRoundVersion::initial) + snapshot.outcomes.map(CloudRoundVersion::initial))
                .groupBy { it.roundId }.values.map { copies ->
                    require(copies.all { it == copies.first() }) { "Unresolved local outcome" }; copies.first()
                }
            return CloudProgress(account, listOf(snapshot.profile) + snapshot.associatedProfiles, snapshot.guestClaims,
                versions, tutorials).also { it.validate(account) }
        }
    }
}

/** Full ancestry is bounded, never truncated; exceeding the bound pauses backup without deleting data. */
data class CloudRoundVersion(
    val versionId: String,
    val ancestorIds: Set<String>,
    val unfinished: RoundRecord? = null,
    val outcome: OutcomeFact? = null,
) {
    val roundId: String get() = unfinished?.id ?: requireNotNull(outcome).id
    val ownerId: String get() = unfinished?.ownerId ?: requireNotNull(outcome).ownerId
    val terminal: Boolean get() = outcome != null

    internal fun immutableFacts(): List<Any> = unfinished?.let {
        listOf(it.id, it.ownerId, it.mode, it.difficulty, it.origin, it.rulesVersion, it.createdAt)
    } ?: requireNotNull(outcome).let {
        listOf(it.id, it.ownerId, it.mode, it.difficulty, it.origin, it.rulesVersion, it.createdAt)
    }

    fun validate() {
        require((unfinished == null) != (outcome == null)) { "Expected exactly one round payload" }
        unfinished?.let { it.validate(); require(!it.status.isTerminal) { "Completed secrets must not upload" } }
        outcome?.validate()
        require(versionId.matches(Regex("[a-f0-9]{64}")) && versionId !in ancestorIds) { "Invalid cloud revision" }
        require(ancestorIds.size <= CloudCodec.MAX_ANCESTORS && ancestorIds.all { it.matches(Regex("[a-f0-9]{64}")) }) {
            "Cloud ancestry limit or invalid ancestor"
        }
        require(versionId == CloudCodec.versionId(unfinished, outcome, ancestorIds)) { "Cloud version checksum mismatch" }
    }

    /** Invoke only for locally observed committed successors, never to guess ancestry after reconnect. */
    fun advance(record: RoundRecord): CloudRoundVersion {
        val previous = requireNotNull(unfinished) { "Terminal outcomes cannot advance or resume" }
        record.validate()
        if (previous == record) return this
        require(record.revision > previous.revision) { "Round revision did not advance" }
        previous.validateSuccessor(record.copy(revision = Math.addExact(previous.revision, 1)))
        return create(record.takeUnless { it.status.isTerminal },
            record.takeIf { it.status.isTerminal }?.let(OutcomeFact::fromRound), ancestorIds + versionId)
    }

    companion object {
        fun initial(outcome: OutcomeFact): CloudRoundVersion {
            outcome.validate()
            return create(null, outcome, emptySet())
        }

        fun initial(record: RoundRecord): CloudRoundVersion {
            record.validate()
            return create(record.takeUnless { it.status.isTerminal },
                record.takeIf { it.status.isTerminal }?.let(OutcomeFact::fromRound), emptySet())
        }

        internal fun create(unfinished: RoundRecord?, outcome: OutcomeFact?, ancestors: Set<String>): CloudRoundVersion =
            CloudRoundVersion(CloudCodec.versionId(unfinished, outcome, ancestors), ancestors, unfinished, outcome)
                .also(CloudRoundVersion::validate)
    }
}

data class CloudRoundConflict(val roundId: String, val candidates: List<CloudRoundVersion>)
data class CloudMergeResult(val progress: CloudProgress, val roundConflicts: List<CloudRoundConflict>, val unfinishedRoundIds: Set<String>) {
    val requiresChoice: Boolean get() = roundConflicts.isNotEmpty() || unfinishedRoundIds.size > 1
}

object CloudMerge {
    fun merge(local: CloudProgress, remote: CloudProgress, expectedAccountPlayerId: String): CloudMergeResult {
        local.validate(expectedAccountPlayerId); remote.validate(expectedAccountPlayerId)
        val profiles = (local.profiles + remote.profiles).groupBy { it.id }.values.map { copies ->
            require(copies.all { it == copies.first() }) { "Profile identity changed" }; copies.first()
        }.sortedBy { it.id }
        val claims = (local.guestClaims + remote.guestClaims).groupBy { it.guestProfileId }.values.map { copies ->
            require(copies.map { it.accountProfileId }.distinct().size == 1) { "Guest claimed by different profiles" }
            copies.minBy { it.claimedAt }
        }.sortedBy { it.guestProfileId }
        val versions = (local.versions + remote.versions).groupBy { it.versionId }.values.map { copies ->
            require(copies.all { it == copies.first() }) { "Cloud revision changed" }; copies.first()
        }
        val frontier = versions.groupBy { it.roundId }.values.flatMap { candidates ->
            require(candidates.map { it.immutableFacts() }.distinct().size == 1) { "Immutable round identity changed" }
            val secrets = candidates.mapNotNull { it.unfinished?.secret }.distinct()
            require(secrets.size <= 1) { "Round secret changed" }
            candidates.filter { ancestor -> candidates.none { descendant ->
                ancestor.versionId in descendant.ancestorIds && (!ancestor.terminal || descendant.terminal)
            } }
        }.sortedWith(compareBy<CloudRoundVersion> { it.roundId }.thenBy { it.versionId })
        val tutorials = (local.tutorialCompletions + remote.tutorialCompletions)
            .groupBy { it.ownerId to it.tutorialVersion }.values.map { it.minBy { completion -> completion.completedAt } }
            .sortedWith(compareBy<TutorialCompletion> { it.ownerId }.thenBy { it.tutorialVersion })
        return inspect(CloudProgress(expectedAccountPlayerId, profiles, claims, frontier, tutorials))
    }

    fun inspect(progress: CloudProgress): CloudMergeResult {
        progress.validate()
        val conflicts = progress.versions.groupBy { it.roundId }.filterValues { it.size > 1 }
            .map { (id, candidates) -> CloudRoundConflict(id, candidates.sortedBy { it.versionId }) }.sortedBy { it.roundId }
        return CloudMergeResult(progress, conflicts, progress.versions.filterNot { it.terminal }.map { it.roundId }.toSet())
    }

    /** Both candidates remain in the input until this explicit decision is committed atomically. */
    fun resolve(progress: CloudProgress, roundId: String, chosenVersionId: String): CloudMergeResult {
        progress.validate()
        val candidates = progress.versions.filter { it.roundId == roundId }
        require(candidates.size > 1) { "No round conflict" }
        val chosen = candidates.single { it.versionId == chosenVersionId }
        require(chosen.terminal || candidates.none { it.terminal }) { "A terminal round cannot be resurrected" }
        val ancestors = candidates.flatMap { it.ancestorIds + it.versionId }.toSet()
        val resolved = CloudRoundVersion.create(chosen.unfinished, chosen.outcome, ancestors)
        return inspect(progress.copy(versions = (progress.versions.filterNot { it.roundId == roundId } + resolved)
            .sortedWith(compareBy<CloudRoundVersion> { it.roundId }.thenBy { it.versionId })))
    }

}

/** GUESSER-16 implements transactions; no cloud receipt can become a Game Stats delivery command. */
interface CloudProgressRepository {
    suspend fun exportCloudProgress(accountPlayerId: String): CloudProgress
    /** Keep pending candidates durable before asking the player; the active round remains untouched. */
    suspend fun retainPendingMerge(accountPlayerId: String, progress: CloudProgress)
    /** Atomic namespace/claim/outcome/round/tutorial import; selectedRoundId selects, never deletes alternatives. */
    suspend fun importResolvedProgress(accountPlayerId: String, progress: CloudProgress, selectedRoundId: String?)
}
