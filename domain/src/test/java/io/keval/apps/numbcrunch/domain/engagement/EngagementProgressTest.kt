package io.keval.apps.numbcrunch.domain.engagement

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import org.junit.Assert.*
import org.junit.Test

class EngagementProgressTest {
    private val owners = setOf("account")
    private fun win(
        id: String = "round", mode: RoundMode = RoundMode.SINGLE,
        difficulty: Difficulty = Difficulty.HARD, owner: String = "account",
    ) = RoundRecord(
        id = id, ownerId = owner, mode = mode, difficulty = difficulty, secret = "012", createdAt = 1,
        updatedAt = 3, revision = 2, status = RoundStatus.WON, guesses = listOf("012"),
        startedAt = 3, completedAt = 3,
    )

    @Test fun `only Single Hard wins qualify across all six combinations`() {
        RoundMode.entries.forEach { mode -> Difficulty.entries.forEach { difficulty ->
            val expected = if (mode == RoundMode.SINGLE && difficulty == Difficulty.HARD) 1L else 0L
            assertEquals(expected, LifetimeHardWins.score(owners, listOf(win(mode = mode, difficulty = difficulty))))
        } }
    }

    @Test fun `example mix and overlapping restored history count each round once`() {
        val hard = (1..7).map { win("h$it") }
        val other = (1..3).map { win("e$it", difficulty = Difficulty.EASY) } +
            (1..2).map { win("m$it", difficulty = Difficulty.MEDIUM) } +
            (1..4).map { win("d$it", mode = RoundMode.DOUBLE) }
        assertEquals(7L, LifetimeHardWins.score(owners, hard + other + hard.reversed()))
        assertEquals(8L, LifetimeHardWins.score(owners, hard + other + hard + win("new")))
    }

    @Test fun `pause and resume preserve eligible round identity`() {
        val initial = RoundRecord("r", "account", RoundMode.SINGLE, Difficulty.HARD, "012", 1)
        val won = initial.editInput("098", 2).submitGuess(3).pause(4).resume(5)
            .editInput("012", 6).submitGuess(7)
        assertEquals(1L, LifetimeHardWins.score(owners, listOf(won, won)))
    }

    @Test fun `unplayed active paused given up abandoned and legacy do not qualify`() {
        val initial = RoundRecord("r", "account", RoundMode.SINGLE, Difficulty.HARD, "012", 1)
        val started = initial.editInput("098", 2).submitGuess(3)
        listOf(initial, started, started.pause(4), started.finish(RoundStatus.GIVEN_UP, 4),
            started.finish(RoundStatus.ABANDONED, 4), win().copy(origin = RoundOrigin.LEGACY)).forEach {
            assertEquals(0L, LifetimeHardWins.score(owners, listOf(it)))
        }
        assertTrue(AchievementCatalog.progress(owners, listOf(win().copy(origin = RoundOrigin.LEGACY)))
            .none { it.unlocked })
    }

    @Test fun `malformed or conflicting facts fail closed instead of silently scoring`() {
        listOf(win().copy(guesses = emptyList()), win().copy(secret = "999"),
            win().copy(rulesVersion = 2), win().copy(currentInput = "1")).forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) { LifetimeHardWins.score(owners, listOf(malformed)) }
        }
        val modified = win().copy(mode = RoundMode.DOUBLE)
        assertThrows(IllegalArgumentException::class.java) { LifetimeHardWins.score(owners, listOf(win(), modified)) }
        val unfinished = RoundRecord("r", "account", RoundMode.DOUBLE, Difficulty.HARD, "012", 1)
        assertThrows(IllegalArgumentException::class.java) {
            unfinished.validateSuccessor(unfinished.copy(mode = RoundMode.SINGLE, revision = 1))
        }
    }

    @Test fun `validated guest namespace contributes once without rewriting original ownership`() {
        val guestWin = win("guest-round", owner = "guest")
        val scoped = setOf("account", "guest")
        assertEquals(2L, LifetimeHardWins.score(scoped, listOf(win(), guestWin, guestWin)))
        assertEquals("guest", guestWin.ownerId)
        assertThrows(IllegalArgumentException::class.java) { LifetimeHardWins.score(owners, listOf(guestWin)) }
        assertThrows(IllegalArgumentException::class.java) { LifetimeHardWins.score(emptySet(), emptyList()) }
    }

    @Test fun `zero large and tied counts use unscaled lifetime total`() {
        assertEquals(0L, LifetimeHardWins.score(owners, emptyList()))
        val many = (1..10_000).map { win(it.toString()) }
        assertEquals(10_000L, LifetimeHardWins.score(owners, many))
        assertEquals(LifetimeHardWins.score(owners, many), LifetimeHardWins.score(owners, many.reversed()))
    }

    @Test fun `catalog has ten revealed-ready unique keys with legal thresholds and points`() {
        val definitions = AchievementCatalog.definitions
        assertEquals(10, definitions.size)
        assertEquals(10, definitions.map { it.key.logicalKey }.distinct().size)
        assertEquals(450, definitions.sumOf { it.points })
        assertTrue(definitions.all { it.points in 5..200 && it.points % 5 == 0 && it.target in 1..10_000 })
        assertEquals(6, definitions.count { it.incremental })
    }

    @Test fun `achievement tiers use cumulative counts and clamp at their target`() {
        for (count in listOf(0, 1, 2, 3, 9, 10, 24, 25, 99, 100, 101)) {
            val rounds = (1..count).map { win("r$it", difficulty = Difficulty.EASY) }
            val progress = AchievementCatalog.progress(owners, rounds + rounds)
            listOf(AchievementKey.FIRST_SOLO_SOLVE, AchievementKey.GETTING_THE_HANG_OF_IT,
                AchievementKey.DOUBLE_DIGITS, AchievementKey.PERSISTENT_PUZZLER, AchievementKey.CENTURY_SOLVER).forEach { key ->
                val achievement = progress.single { it.definition.key == key }
                assertEquals(minOf(count, achievement.definition.target), achievement.steps)
            }
        }
    }

    @Test fun `Medium unaided excludes used hint Double and other difficulties`() {
        val medium = win(difficulty = Difficulty.MEDIUM)
        val records = listOf(medium, medium.copy(id = "hint", hintedGuessIndex = 0),
            win("double", RoundMode.DOUBLE, Difficulty.MEDIUM), win("hard"), win("easy", difficulty = Difficulty.EASY))
        val progress = AchievementCatalog.progress(owners, records).associate { it.definition.key to it.steps }
        assertEquals(1, progress[AchievementKey.UNAIDED_DEDUCTION])
        assertEquals(1, progress[AchievementKey.MEDIUM_MASTERED])
        assertEquals(1, progress[AchievementKey.HARD_EARNED])
    }

    @Test fun `onboarding or missing tutorial evidence awards nothing and true completion is owner scoped`() {
        fun tutorial(completions: List<TutorialCompletion>) = AchievementCatalog.progress(owners, emptyList(), completions)
            .single { it.definition.key == AchievementKey.LEARNING_THE_ROPES }
        assertFalse(tutorial(emptyList()).unlocked)
        assertTrue(tutorial(listOf(TutorialCompletion("account", 100))).unlocked)
        assertThrows(IllegalArgumentException::class.java) { tutorial(listOf(TutorialCompletion("other", 100))) }
        assertThrows(IllegalArgumentException::class.java) { tutorial(listOf(TutorialCompletion("account", 100, 2))) }
        assertTrue(AchievementCatalog.progress(setOf("account", "guest"), emptyList(),
            listOf(TutorialCompletion("guest", 100))).first().unlocked)
    }

    @Test fun `first hour route earns four without needing Hard or simultaneous first launch awards`() {
        val tutorial = listOf(TutorialCompletion("account", 100))
        val rounds = listOf(win("easy1", difficulty = Difficulty.EASY), win("easy2", difficulty = Difficulty.EASY),
            win("medium", difficulty = Difficulty.MEDIUM))
        assertEquals(0, AchievementCatalog.progress(owners, emptyList()).count { it.unlocked })
        assertEquals(1, AchievementCatalog.progress(owners, emptyList(), tutorial).count { it.unlocked })
        assertEquals(4, AchievementCatalog.progress(owners, rounds, tutorial).count { it.unlocked })
    }
}
