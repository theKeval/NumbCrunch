package io.keval.apps.numbcrunch.domain.progress

import io.keval.apps.numbcrunch.domain.game.Difficulty

data class StatisticsFilter(val mode: RoundMode? = null, val difficulty: Difficulty? = null)

data class PlayerStatistics(
    val played: Long = 0,
    val wins: Long = 0,
    val giveUps: Long = 0,
    val abandoned: Long = 0,
    val totalGuesses: Long = 0,
    val winningGuessSum: Long = 0,
    val bestWinningGuesses: Long? = null,
    val hintsUsed: Long = 0,
    val currentWinStreak: Long = 0,
    val bestWinStreak: Long = 0,
) {
    val resolved: Long get() = Math.addExact(Math.addExact(wins, giveUps), abandoned)
    val winRate: Double? get() = resolved.takeIf { it > 0 }?.let { wins.toDouble() / it }
    val averageWinningGuesses: Double? get() = wins.takeIf { it > 0 }?.let { winningGuessSum.toDouble() / it }

    companion object {
        /** Completion timestamp then stable round ID makes recomputation independent of arrival order. */
        fun fromRounds(rounds: List<RoundRecord>, filter: StatisticsFilter = StatisticsFilter()): PlayerStatistics {
            val unique = rounds.groupBy { it.id }.map { (_, duplicates) ->
                require(duplicates.all { it == duplicates.first() }) { "Conflicting round identity" }
                duplicates.first().also { it.validate() }
            }
            return fromFacts(unique.filter { it.status.isTerminal }.map(OutcomeFact::fromRound),
                unique.filterNot { it.status.isTerminal }, filter)
        }

        /** Cloud outcomes are receipts, never invented full rounds containing fake secrets. */
        fun fromFacts(outcomes: List<OutcomeFact>, unfinished: List<RoundRecord> = emptyList(),
            filter: StatisticsFilter = StatisticsFilter()): PlayerStatistics {
            val terminal = outcomes.groupBy { it.id }.values.map { copies ->
                require(copies.all { it == copies.first() }) { "Conflicting outcome identity" }
                copies.first().also(OutcomeFact::validate)
            }
            val active = unfinished.groupBy { it.id }.values.map { copies ->
                require(copies.all { it == copies.first() }) { "Conflicting round identity" }
                copies.first().also { it.validate(); require(!it.status.isTerminal) { "Expected unfinished round" } }
            }
            require(terminal.map { it.id }.toSet().intersect(active.map { it.id }.toSet()).isEmpty()) { "Unresolved round outcome" }
            val unique = (terminal.map { StatsFact(it.id, it.mode, it.difficulty, it.status, it.guessCount, it.hintUsed, it.completedAt) } +
                active.map { StatsFact(it.id, it.mode, it.difficulty, it.status, it.guesses.size.toLong(), it.hintedGuessIndex != null, null) })
                .filter { it.guessCount > 0 && (filter.mode == null || it.mode == filter.mode) &&
                (filter.difficulty == null || it.difficulty == filter.difficulty) }
            var stats = PlayerStatistics()
            unique.sortedWith(compareBy<StatsFact> { it.completedAt ?: Long.MAX_VALUE }.thenBy { it.id }).forEach { round ->
                val won = round.status == RoundStatus.WON
                val streak = when {
                    won -> Math.addExact(stats.currentWinStreak, 1)
                    round.status.isTerminal -> 0L
                    else -> stats.currentWinStreak
                }
                val guesses = round.guessCount
                stats = stats.copy(
                    played = Math.addExact(stats.played, 1),
                    wins = Math.addExact(stats.wins, if (won) 1 else 0),
                    giveUps = Math.addExact(stats.giveUps, if (round.status == RoundStatus.GIVEN_UP) 1 else 0),
                    abandoned = Math.addExact(stats.abandoned, if (round.status == RoundStatus.ABANDONED) 1 else 0),
                    totalGuesses = Math.addExact(stats.totalGuesses, guesses),
                    winningGuessSum = Math.addExact(stats.winningGuessSum, if (won) guesses else 0),
                    bestWinningGuesses = if (won) minOf(stats.bestWinningGuesses ?: guesses, guesses) else stats.bestWinningGuesses,
                    hintsUsed = Math.addExact(stats.hintsUsed, if (round.hintUsed) 1 else 0),
                    currentWinStreak = streak,
                    bestWinStreak = maxOf(stats.bestWinStreak, streak),
                )
            }
            return stats
        }
    }
}

private data class StatsFact(val id: String, val mode: RoundMode, val difficulty: Difficulty,
    val status: RoundStatus, val guessCount: Long, val hintUsed: Boolean, val completedAt: Long?)
