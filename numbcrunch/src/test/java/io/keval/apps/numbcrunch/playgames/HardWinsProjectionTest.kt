package io.keval.apps.numbcrunch.playgames

import io.keval.apps.numbcrunch.domain.cloud.CloudBackupState
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import org.junit.Assert.*
import org.junit.Test

class HardWinsProjectionTest {
    @Test fun guestHardWinsRemainAvailableWithoutCloudEligibility() {
        val guest = PlayerProfile("guest", null, 1)
        val win = RoundRecord("win", guest.id, RoundMode.SINGLE, Difficulty.HARD, "012", 1)
            .editInput("012", 2).submitGuess(3)
        val (context, stamp) = requireNotNull(hardWinsProjection(
            ProgressSnapshot(profile = guest, rounds = listOf(win)),
            PlayerSession(selectedProfile = guest), CloudBackupState(),
        ))
        assertEquals(1L, context.localWins)
        assertNull(context.accountPlayerId)
        assertFalse(context.reconciled)
        assertNull(stamp)
    }

    @Test fun accountChangeCannotDisplayAnotherProfilesLocalTotal() {
        val old = PlayerProfile("old", "player-a", 1)
        assertNull(hardWinsProjection(ProgressSnapshot(profile = old, rounds = emptyList()),
            PlayerSession(selectedProfile = PlayerProfile("new", "player-b", 1)), CloudBackupState()))
    }
}
