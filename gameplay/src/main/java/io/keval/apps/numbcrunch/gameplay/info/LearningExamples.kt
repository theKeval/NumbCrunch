package io.keval.apps.numbcrunch.gameplay.info

import io.keval.apps.numbcrunch.domain.game.GuessRules
import io.keval.apps.numbcrunch.domain.tutorial.TutorialEvidence

internal object LearningExamples {
    const val SECRET = TutorialEvidence.PRACTICE_SECRET
    val guesses = listOf("325", "542", "316", "245")
    val evaluations = guesses.map { GuessRules.evaluate(SECRET, it) }
}
