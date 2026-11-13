package io.keval.apps.numbcrunch.domain.connection

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayGamesIdentityTest {
    @Test fun stableCurrentPlayerIdAcceptsUpdatedDisplayName() = runBlocking {
        val reads = ArrayDeque(listOf(PlayGamesPlayer("same-player", "Old name"), PlayGamesPlayer("same-player", "New name")))
        assertEquals(ConnectionResult.Connected(PlayGamesPlayer("same-player", "New name")),
            readStablePlayGamesPlayer { reads.removeFirst() })
    }

    @Test fun accountChangeBetweenCurrentPlayerReadsIsRejected() = runBlocking {
        val reads = ArrayDeque(listOf(PlayGamesPlayer("first", "Name"), PlayGamesPlayer("second", "Name")))
        assertEquals(ConnectionResult.Failed, readStablePlayGamesPlayer { reads.removeFirst() })
    }

    @Test fun missingPlayerIdentityIsRejected() = runBlocking {
        assertEquals(ConnectionResult.Failed, readStablePlayGamesPlayer { PlayGamesPlayer("", "Name") })
    }
}
