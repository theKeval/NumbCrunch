package io.keval.apps.numbcrunch.domain.progress

import io.keval.apps.numbcrunch.domain.game.Difficulty

/** Permanent outcome receipt: enough for lifetime statistics, never a secret or a guess sequence. */
data class OutcomeFact(
    val id: String,
    val ownerId: String,
    val mode: RoundMode,
    val difficulty: Difficulty,
    val origin: RoundOrigin,
    val rulesVersion: Int,
    val revision: Long,
    val status: RoundStatus,
    val guessCount: Long,
    val hintUsed: Boolean,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long,
) {
    fun validate() {
        require(id.isNotBlank() && ownerId.isNotBlank()) { "Missing outcome identity" }
        require(rulesVersion == 1 && revision >= 0 && status.isTerminal) { "Invalid outcome version or status" }
        require(guessCount >= 0 && (guessCount == 0L) == (startedAt == null)) { "Invalid outcome guess count" }
        require(status != RoundStatus.WON || guessCount > 0) { "Unplayed win" }
        require(!hintUsed || (difficulty == Difficulty.MEDIUM && (guessCount > 0 || origin == RoundOrigin.LEGACY))) { "Invalid outcome hint" }
        require(createdAt >= 0 && completedAt >= (startedAt ?: createdAt) && (startedAt == null || startedAt >= createdAt)) {
            "Invalid outcome time"
        }
    }

    companion object {
        fun fromRound(round: RoundRecord): OutcomeFact {
            round.validate()
            require(round.status.isTerminal) { "Only completed rounds have outcome receipts" }
            return OutcomeFact(round.id, round.ownerId, round.mode, round.difficulty, round.origin,
                round.rulesVersion, round.revision, round.status, round.guesses.size.toLong(),
                round.hintedGuessIndex != null, round.createdAt, round.startedAt, requireNotNull(round.completedAt))
                .also(OutcomeFact::validate)
        }
    }
}
