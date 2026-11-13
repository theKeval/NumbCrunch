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
            }.filter { it.isPlayed && (filter.mode == null || it.mode == filter.mode) &&
                (filter.difficulty == null || it.difficulty == filter.difficulty) }
            var stats = PlayerStatistics()
            unique.sortedWith(compareBy<RoundRecord> { it.completedAt ?: Long.MAX_VALUE }.thenBy { it.id }).forEach { round ->
                val won = round.status == RoundStatus.WON
                val streak = when {
                    won -> Math.addExact(stats.currentWinStreak, 1)
                    round.status.isTerminal -> 0L
                    else -> stats.currentWinStreak
                }
                val guesses = round.guesses.size.toLong()
                stats = stats.copy(
                    played = Math.addExact(stats.played, 1),
                    wins = Math.addExact(stats.wins, if (won) 1 else 0),
                    giveUps = Math.addExact(stats.giveUps, if (round.status == RoundStatus.GIVEN_UP) 1 else 0),
                    abandoned = Math.addExact(stats.abandoned, if (round.status == RoundStatus.ABANDONED) 1 else 0),
                    totalGuesses = Math.addExact(stats.totalGuesses, guesses),
                    winningGuessSum = Math.addExact(stats.winningGuessSum, if (won) guesses else 0),
                    bestWinningGuesses = if (won) minOf(stats.bestWinningGuesses ?: guesses, guesses) else stats.bestWinningGuesses,
                    hintsUsed = Math.addExact(stats.hintsUsed, if (round.hintedGuessIndex != null) 1 else 0),
                    currentWinStreak = streak,
                    bestWinStreak = maxOf(stats.bestWinStreak, streak),
                )
            }
            return stats
        }
    }
}
