package io.keval.apps.numbcrunch.domain.tutorial

import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion

/** A final action awards completion only after every page was displayed and practice was solved. */
data class TutorialEvidence(val visitedPages: Int = 0, val practiceSolved: Boolean = false) {
    fun visit(page: Int): TutorialEvidence {
        require(page in 0..2) { "Invalid tutorial page" }
        return copy(visitedPages = visitedPages or (1 shl page))
    }

    fun submit(guess: String): TutorialEvidence = copy(practiceSolved = practiceSolved || guess == PRACTICE_SECRET)

    fun completion(ownerId: String, completedAt: Long): TutorialCompletion? =
        if (visitedPages == ALL_PAGES && practiceSolved) TutorialCompletion(ownerId, completedAt).also { it.validate() } else null

    companion object {
        const val PRACTICE_SECRET = "245"
        private const val ALL_PAGES = 7
    }
}
