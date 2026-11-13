package io.keval.apps.numbcrunch.domain.engagement

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.RoundMode
import io.keval.apps.numbcrunch.domain.progress.RoundOrigin
import io.keval.apps.numbcrunch.domain.progress.RoundRecord
import io.keval.apps.numbcrunch.domain.progress.RoundStatus
import io.keval.apps.numbcrunch.domain.progress.OutcomeFact

/** Actual tutorial completion, never the completed-or-skipped onboarding preference. */
data class TutorialCompletion(val ownerId: String, val completedAt: Long, val tutorialVersion: Int = 1) {
    fun validate() {
        require(ownerId.isNotBlank() && completedAt >= 0 && tutorialVersion == 1) { "Invalid tutorial completion" }
    }
}

/**
 * Call only after resolving cloud conflicts and validating exclusive guest claims. allowedOwnerIds
 * includes the selected local profile and its claimed guest namespaces; round owners never change.
 * Malformed facts or conflicting copies stop publication rather than yielding a misleading total.
 */
internal fun validatedRounds(allowedOwnerIds: Set<String>, rounds: List<RoundRecord>): List<RoundRecord> {
    require(allowedOwnerIds.isNotEmpty() && allowedOwnerIds.none(String::isBlank)) { "Missing progress scope" }
    return rounds.groupBy { it.id }.values.map { copies ->
        val round = copies.first()
        require(copies.all { it == round }) { "Unresolved round conflict" }
        require(round.ownerId in allowedOwnerIds) { "Round outside claimed progress scope" }
        round.also(RoundRecord::validate)
    }
}

internal fun validatedOutcomes(allowedOwnerIds: Set<String>, outcomes: List<OutcomeFact>): List<OutcomeFact> {
    require(allowedOwnerIds.isNotEmpty() && allowedOwnerIds.none(String::isBlank)) { "Missing progress scope" }
    return outcomes.groupBy { it.id }.values.map { copies ->
        val outcome = copies.first()
        require(copies.all { it == outcome }) { "Unresolved outcome conflict" }
        require(outcome.ownerId in allowedOwnerIds) { "Outcome outside claimed progress scope" }
        outcome.also(OutcomeFact::validate)
    }
}

internal fun OutcomeFact.isRecordedSoloWin(): Boolean =
    origin == RoundOrigin.LOCAL && mode == RoundMode.SINGLE && status == RoundStatus.WON && guessCount > 0

object LifetimeHardWins {
    const val LOGICAL_KEY = "lifetime_single_hard_wins_v1"
    const val SCORING_VERSION = 1

    /** Counts source facts, not counters, increments, timestamps or restored SDK submissions. */
    fun score(allowedOwnerIds: Set<String>, rounds: List<RoundRecord>): Long = scoreFacts(allowedOwnerIds,
        validatedRounds(allowedOwnerIds, rounds).filter { it.status.isTerminal }.map(OutcomeFact::fromRound))

    fun scoreFacts(allowedOwnerIds: Set<String>, outcomes: List<OutcomeFact>): Long =
        validatedOutcomes(allowedOwnerIds, outcomes).fold(0L) { score, round ->
            if (round.isRecordedSoloWin() && round.difficulty == Difficulty.HARD) Math.addExact(score, 1L) else score
        }
}
