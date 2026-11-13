package io.keval.apps.numbcrunch.domain.engagement

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.RoundRecord
import io.keval.apps.numbcrunch.domain.progress.OutcomeFact

enum class AchievementKey(val logicalKey: String) {
    LEARNING_THE_ROPES("learning_the_ropes"),
    FIRST_SOLO_SOLVE("first_solo_solve"),
    GETTING_THE_HANG_OF_IT("getting_the_hang_of_it"),
    DOUBLE_DIGITS("double_digits"),
    PERSISTENT_PUZZLER("persistent_puzzler"),
    CENTURY_SOLVER("century_solver"),
    MEDIUM_MASTERED("medium_mastered"),
    HARD_EARNED("hard_earned"),
    HARD_REGULAR("hard_regular"),
    UNAIDED_DEDUCTION("unaided_deduction"),
}

data class AchievementDefinition(val key: AchievementKey, val target: Int, val points: Int) {
    val incremental: Boolean get() = target > 1
}

data class AchievementProgress(val definition: AchievementDefinition, val steps: Int) {
    val unlocked: Boolean get() = steps >= definition.target
}

object AchievementCatalog {
    const val VERSION = 1
    val definitions = listOf(
        AchievementDefinition(AchievementKey.LEARNING_THE_ROPES, 1, 5),
        AchievementDefinition(AchievementKey.FIRST_SOLO_SOLVE, 1, 10),
        AchievementDefinition(AchievementKey.GETTING_THE_HANG_OF_IT, 3, 15),
        AchievementDefinition(AchievementKey.DOUBLE_DIGITS, 10, 30),
        AchievementDefinition(AchievementKey.PERSISTENT_PUZZLER, 25, 50),
        AchievementDefinition(AchievementKey.CENTURY_SOLVER, 100, 150),
        AchievementDefinition(AchievementKey.MEDIUM_MASTERED, 1, 20),
        AchievementDefinition(AchievementKey.HARD_EARNED, 1, 30),
        AchievementDefinition(AchievementKey.HARD_REGULAR, 10, 65),
        AchievementDefinition(AchievementKey.UNAIDED_DEDUCTION, 10, 75),
    )

    /** Same validated scope as LifetimeHardWins. Incremental steps use setSteps, never increment. */
    fun progress(
        allowedOwnerIds: Set<String>,
        rounds: List<RoundRecord>,
        tutorialCompletions: List<TutorialCompletion> = emptyList(),
    ): List<AchievementProgress> = progressFacts(allowedOwnerIds,
        validatedRounds(allowedOwnerIds, rounds).filter { it.status.isTerminal }.map(OutcomeFact::fromRound), tutorialCompletions)

    fun progressFacts(
        allowedOwnerIds: Set<String>,
        outcomes: List<OutcomeFact>,
        tutorialCompletions: List<TutorialCompletion> = emptyList(),
    ): List<AchievementProgress> {
        val wins = validatedOutcomes(allowedOwnerIds, outcomes).filter(OutcomeFact::isRecordedSoloWin)
        tutorialCompletions.forEach {
            it.validate()
            require(it.ownerId in allowedOwnerIds) { "Tutorial outside claimed progress scope" }
        }
        val solo = wins.size
        val medium = wins.count { it.difficulty == Difficulty.MEDIUM }
        val hard = wins.count { it.difficulty == Difficulty.HARD }
        val unaided = wins.count { it.difficulty == Difficulty.MEDIUM && !it.hintUsed }
        return definitions.map { definition ->
            val count = when (definition.key) {
                AchievementKey.LEARNING_THE_ROPES -> if (tutorialCompletions.isEmpty()) 0 else 1
                AchievementKey.FIRST_SOLO_SOLVE, AchievementKey.GETTING_THE_HANG_OF_IT,
                AchievementKey.DOUBLE_DIGITS, AchievementKey.PERSISTENT_PUZZLER,
                AchievementKey.CENTURY_SOLVER -> solo
                AchievementKey.MEDIUM_MASTERED -> medium
                AchievementKey.HARD_EARNED, AchievementKey.HARD_REGULAR -> hard
                AchievementKey.UNAIDED_DEDUCTION -> unaided
            }
            AchievementProgress(definition, count.coerceAtMost(definition.target))
        }
    }
}
