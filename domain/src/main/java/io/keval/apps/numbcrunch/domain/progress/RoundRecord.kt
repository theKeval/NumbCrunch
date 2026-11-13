package io.keval.apps.numbcrunch.domain.progress

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.GuessRules
import io.keval.apps.numbcrunch.domain.game.isCodeDigit

enum class RoundMode { SINGLE, DOUBLE }
enum class RoundStatus {
    ACTIVE, PAUSED, WON, GIVEN_UP, ABANDONED;
    val isTerminal: Boolean get() = this == WON || this == GIVEN_UP || this == ABANDONED
}

/** Legacy restoration cannot establish leaderboard eligibility retrospectively. */
enum class RoundOrigin { LOCAL, LEGACY }

data class PlayerProfile(val id: String, val pgsPlayerId: String?, val trackingStartedAt: Long)

/** All game facts, including ownership, survive navigation and process death. Never log this record. */
data class RoundRecord(
    val id: String,
    val ownerId: String,
    val mode: RoundMode,
    val difficulty: Difficulty,
    val secret: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val rulesVersion: Int = 1,
    val origin: RoundOrigin = RoundOrigin.LOCAL,
    val revision: Long = 0,
    val status: RoundStatus = RoundStatus.ACTIVE,
    val guesses: List<String> = emptyList(),
    val currentInput: String = "",
    val hintedGuessIndex: Int? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
) {
    val isPlayed: Boolean get() = guesses.isNotEmpty()

    fun validate() {
        require(id.isNotBlank() && ownerId.isNotBlank()) { "Missing round identity" }
        require(rulesVersion == 1 && revision >= 0) { "Unsupported round version" }
        require(createdAt >= 0 && updatedAt >= createdAt) { "Invalid round time" }
        require(GuessRules.isValidCode(secret)) { "Invalid round secret" }
        require(guesses.all(GuessRules::isValidCode)) { "Invalid round guesses" }
        require(guesses.dropLast(1).none { it == secret }) { "Guesses follow a win" }
        require(currentInput.length <= 3 && currentInput.all(::isCodeDigit) &&
            currentInput.toSet().size == currentInput.length) { "Invalid round input" }
        require((guesses.isEmpty()) == (startedAt == null)) { "Invalid started marker" }
        require(startedAt == null || startedAt in createdAt..updatedAt) { "Invalid start time" }
        require(status.isTerminal == (completedAt != null)) { "Invalid completion marker" }
        require(completedAt == null || completedAt in (startedAt ?: createdAt)..updatedAt) {
            "Invalid completion time"
        }
        require((status == RoundStatus.WON) == (guesses.lastOrNull() == secret)) { "Invalid win" }
        require(!status.isTerminal || currentInput.isEmpty()) { "Terminal round has input" }
        require(hintedGuessIndex == null || (difficulty == Difficulty.MEDIUM &&
            (hintedGuessIndex in guesses.indices || (origin == RoundOrigin.LEGACY && hintedGuessIndex == -1)))) {
            "Invalid hint state"
        }
    }

    /** Validate optimistic updates before any durable data is replaced. */
    fun validateSuccessor(next: RoundRecord) {
        next.validate()
        require(!status.isTerminal) { "Round already completed" }
        require(next.id == id && next.ownerId == ownerId && next.mode == mode &&
            next.difficulty == difficulty && next.secret == secret && next.rulesVersion == rulesVersion &&
            next.origin == origin && next.createdAt == createdAt) { "Immutable round facts changed" }
        require(next.revision == Math.addExact(revision, 1)) { "Invalid revision" }
        require(next.updatedAt >= updatedAt) { "Round time moved backwards" }
        require(next.guesses.size >= guesses.size && next.guesses.take(guesses.size) == guesses) {
            "Round guesses were replaced"
        }
        require(startedAt == null || next.startedAt == startedAt) { "Start time changed" }
        require(hintedGuessIndex == null || next.hintedGuessIndex == hintedGuessIndex) { "Hint was reset" }
    }

    override fun toString(): String = "RoundRecord(id=$id, status=$status, revision=$revision, gameData=redacted)"
}
