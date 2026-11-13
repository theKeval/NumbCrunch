package io.keval.apps.numbcrunch.domain.progress

import io.keval.apps.numbcrunch.domain.game.GuessRules

/** Produces a candidate only; display it after ProgressRepository.updateRound commits. */
fun RoundRecord.editInput(input: String, now: Long): RoundRecord {
    require(status == RoundStatus.ACTIVE) { "Resume the round before playing" }
    return change(now) { copy(currentInput = input) }
}

fun RoundRecord.submitGuess(now: Long): RoundRecord {
    require(status == RoundStatus.ACTIVE) { "Resume the round before playing" }
    require(GuessRules.isValidCode(currentInput)) { "Invalid guess" }
    return change(now) {
        val won = currentInput == secret
        copy(
            guesses = guesses + currentInput,
            currentInput = "",
            startedAt = startedAt ?: updatedAt,
            status = if (won) RoundStatus.WON else RoundStatus.ACTIVE,
            completedAt = if (won) updatedAt else null,
        )
    }
}

fun RoundRecord.useHint(index: Int, now: Long): RoundRecord {
    require(status == RoundStatus.ACTIVE) { "Resume the round before playing" }
    require(hintedGuessIndex == null) { "Hint already used" }
    return change(now) { copy(hintedGuessIndex = index) }
}

fun RoundRecord.finish(status: RoundStatus, now: Long): RoundRecord {
    require(status == RoundStatus.GIVEN_UP || status == RoundStatus.ABANDONED) { "Invalid requested outcome" }
    return change(now) { copy(status = status, currentInput = "", completedAt = updatedAt) }
}

fun RoundRecord.pause(now: Long): RoundRecord = change(now) { copy(status = RoundStatus.PAUSED) }
fun RoundRecord.resume(now: Long): RoundRecord = change(now) { copy(status = RoundStatus.ACTIVE) }

private fun RoundRecord.change(now: Long, action: RoundRecord.() -> RoundRecord): RoundRecord {
    val next = copy(revision = Math.addExact(revision, 1), updatedAt = maxOf(now, updatedAt)).action()
    validateSuccessor(next)
    return next
}
