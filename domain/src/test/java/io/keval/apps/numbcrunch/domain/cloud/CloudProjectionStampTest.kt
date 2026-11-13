package io.keval.apps.numbcrunch.domain.cloud

import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import org.junit.Assert.*
import org.junit.Test

class CloudProjectionStampTest {
    private val profile = PlayerProfile("local", "pgs", 1)
    private val won = RoundRecord("won", profile.id, RoundMode.SINGLE, Difficulty.HARD, "123", 1)
        .editInput("123", 2).submitGuess(3)

    @Test fun locallyEarnedAndImportedReceiptHaveSameCoverage() {
        val local = ProgressSnapshot(profile = profile, rounds = listOf(won))
        val imported = ProgressSnapshot(profile = profile, rounds = emptyList(), outcomes = listOf(OutcomeFact.fromRound(won)))
        assertEquals(local.cloudProjectionStamp(), imported.cloudProjectionStamp())
    }

    @Test fun newWinOrTutorialImmediatelyInvalidatesPreviousCoverage() {
        val before = ProgressSnapshot(profile = profile, rounds = emptyList())
        val after = before.copy(rounds = listOf(won))
        assertNotEquals(before.cloudProjectionStamp(), after.cloudProjectionStamp())
        assertNotEquals(after.cloudProjectionStamp(), after.copy(tutorialCompletions = listOf(TutorialCompletion(profile.id, 4))).cloudProjectionStamp())
    }

    @Test fun claimingAnotherNamespaceChangesCoverage() {
        val before = ProgressSnapshot(profile = profile, rounds = emptyList())
        assertNotEquals(before.cloudProjectionStamp(), before.copy(guestClaims = listOf(GuestProgressClaim("guest", profile.id, 2))).cloudProjectionStamp())
    }
}
