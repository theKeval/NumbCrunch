package io.keval.apps.numbcrunch.domain.progress

import io.keval.apps.numbcrunch.domain.game.Difficulty
import org.junit.Assert.*
import org.junit.Test

class PlayerProgressTest {
    private fun round(id: String = "round", mode: RoundMode = RoundMode.SINGLE, difficulty: Difficulty = Difficulty.HARD) =
        RoundRecord(id, "owner", mode, difficulty, "573", 1)

    private fun win(id: String, guesses: Int): RoundRecord = round(id).let { start ->
        var result = start
        repeat(guesses - 1) { result = result.editInput("527", 2).submitGuess(2) }
        result.editInput("573", 3).submitGuess(3)
    }

    @Test fun `winning average uses cumulative winning guesses`() {
        val stats = PlayerStatistics.fromRounds(listOf(win("a", 4), win("b", 7), win("c", 10)))
        assertEquals(3L, stats.wins)
        assertEquals(21L, stats.winningGuessSum)
        assertEquals(7.0, stats.averageWinningGuesses!!, 0.0)
        assertEquals(4L, stats.bestWinningGuesses)
        assertEquals(3L, stats.currentWinStreak)
    }

    @Test fun `paused and zero guess rounds never become losses`() {
        val started = round("b").editInput("527", 2).submitGuess(2)
        val rounds = listOf(win("a", 1), started.finish(RoundStatus.GIVEN_UP, 3),
            started.copy(id = "c").finish(RoundStatus.ABANDONED, 4),
            started.copy(id = "d").pause(5), round("e").pause(6),
            round("f").finish(RoundStatus.GIVEN_UP, 6))
        val stats = PlayerStatistics.fromRounds(rounds)
        assertEquals(4L, stats.played)
        assertEquals(3L, stats.resolved)
        assertEquals(1.0 / 3, stats.winRate!!, 0.0)
        assertEquals(0L, stats.currentWinStreak)
        assertEquals(1L, stats.bestWinStreak)
    }

    @Test fun `all six slices are independent and duplicate imports do not inflate totals`() {
        val rounds = RoundMode.entries.flatMap { mode -> Difficulty.entries.map { difficulty ->
            round("$mode-$difficulty", mode, difficulty).editInput("573", 2).submitGuess(2)
        } }
        assertEquals(6L, PlayerStatistics.fromRounds(rounds + rounds).wins)
        RoundMode.entries.forEach { mode -> Difficulty.entries.forEach { difficulty ->
            val stats = PlayerStatistics.fromRounds(rounds, StatisticsFilter(mode, difficulty))
            assertEquals(1L, stats.played)
            assertEquals(1L, stats.wins)
        } }
    }

    @Test fun `completion order and stable id decide streak regardless of arrival order`() {
        val rounds = listOf(win("a", 1),
            round("b").editInput("527", 2).submitGuess(2).finish(RoundStatus.ABANDONED, 3),
            win("c", 1), win("d", 1))
        assertEquals(PlayerStatistics.fromRounds(rounds), PlayerStatistics.fromRounds(rounds.reversed()))
        assertEquals(2L, PlayerStatistics.fromRounds(rounds).currentWinStreak)
        assertNull(PlayerStatistics().winRate)
        assertNull(PlayerStatistics().averageWinningGuesses)
    }

    @Test fun `invalid guesses immutable facts and spent hints cannot be rewritten`() {
        val record = round(difficulty = Difficulty.MEDIUM)
        assertThrows(IllegalArgumentException::class.java) { record.editInput("999", 2) }
        assertThrows(IllegalArgumentException::class.java) { record.editInput("12", 2).submitGuess(2) }
        assertThrows(IllegalArgumentException::class.java) { record.validateSuccessor(record.copy(revision = 1, difficulty = Difficulty.HARD)) }
        val hinted = record.editInput("527", 2).submitGuess(2).useHint(0, 2)
        assertThrows(IllegalArgumentException::class.java) { hinted.validateSuccessor(hinted.copy(revision = hinted.revision + 1, hintedGuessIndex = null)) }
        assertEquals(1L, PlayerStatistics.fromRounds(listOf(hinted)).hintsUsed)
        assertFalse(hinted.toString().contains("527"))
        assertFalse(hinted.toString().contains("573"))
    }

    @Test fun `paused round requires explicit resume and terminal round rejects late quit`() {
        val paused = round().pause(2)
        assertThrows(IllegalArgumentException::class.java) { paused.editInput("527", 3) }
        assertEquals(RoundStatus.ACTIVE, paused.resume(3).status)
        val won = win("win", 1)
        assertThrows(IllegalArgumentException::class.java) { won.finish(RoundStatus.GIVEN_UP, 4) }
    }

    @Test fun `counters use longs and fail rather than wrap at overflow`() {
        assertEquals(4_000_000_000L, PlayerStatistics(wins = 3_000_000_000L, abandoned = 1_000_000_000L).resolved)
        assertThrows(ArithmeticException::class.java) { PlayerStatistics(wins = Long.MAX_VALUE, giveUps = 1).resolved }
        assertThrows(ArithmeticException::class.java) { round().copy(revision = Long.MAX_VALUE).editInput("5", 2) }
    }
}
