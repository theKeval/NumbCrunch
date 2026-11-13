package io.keval.apps.numbcrunch.domain.engagement

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HardWinsLeaderboardTest {
    private val account = HardWinsContext("local-a", 1, 7, "account-a", true)
    private class Gateway : HardWinsGateway {
        val submissions = mutableListOf<Pair<String, Long>>()
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun submit(accountPlayerId: String, score: Long): Long {
            submissions += accountPlayerId to score
            gate?.await()
            check(!fail)
            return score
        }
        override suspend fun load(accountPlayerId: String, window: RankingWindow): RankingPage {
            gate?.await()
            check(!fail)
            return RankingPage(listOf(RankingRow("1", "Player", "7")), null)
        }
    }

    @Test fun onlyReconciledConnectedPositiveTotalsSubmitAndRepeatIsIdempotent() = runBlocking {
        val board = HardWinsLeaderboard(); val gateway = Gateway()
        listOf(account.copy(accountPlayerId = null), account.copy(reconciled = false), account.copy(localWins = 0)).forEach {
            board.setContext(it); board.reconcile(gateway)
        }
        assertTrue(gateway.submissions.isEmpty())
        board.setContext(account); board.reconcile(gateway); board.reconcile(gateway)
        assertEquals(listOf("account-a" to 7L), gateway.submissions)
        board.setContext(account.copy(localWins = 8)); board.reconcile(gateway)
        assertEquals(8L, gateway.submissions.last().second)
        assertEquals(ScoreDelivery.ACKNOWLEDGED, board.state.value.delivery)
    }

    @Test fun failureKeepsExactLocalCountAndRetrySendsTheTotal() = runBlocking {
        val board = HardWinsLeaderboard(); val gateway = Gateway().apply { fail = true }
        board.setContext(account); board.reconcile(gateway)
        assertEquals(7L, board.state.value.context?.localWins)
        assertEquals(ScoreDelivery.PENDING, board.state.value.delivery)
        gateway.fail = false; board.reconcile(gateway)
        assertEquals(listOf(7L, 7L), gateway.submissions.map { it.second })
    }

    @Test fun switchingAccountsDiscardsInFlightScoreAndRankingResults() = runBlocking {
        val board = HardWinsLeaderboard(); val gate = CompletableDeferred<Unit>(); val gateway = Gateway().apply { this.gate = gate }
        board.setContext(account)
        val submission = launch { board.reconcile(gateway) }; val loading = launch { board.load(gateway) }; yield()
        board.setContext(account.copy(localProfileId = "local-b", accountPlayerId = "account-b", generation = 2, localWins = 2))
        gate.complete(Unit); submission.join(); loading.join()
        assertEquals(ScoreDelivery.PENDING, board.state.value.delivery)
        assertNull(board.state.value.page)
        board.reconcile(gateway)
        assertEquals("account-b" to 2L, gateway.submissions.last())
    }

    @Test fun offlineOrGuestNeverLoadsRankingsAndReconnectCannotReuseAnotherPlayersRows() = runBlocking {
        val board = HardWinsLeaderboard(); val gateway = Gateway()
        board.setContext(account); board.load(gateway, RankingWindow.NEAR_ME)
        assertEquals(RankingStatus.READY, board.state.value.status)
        board.setContext(account.copy(accountPlayerId = null, reconciled = false)); board.load(gateway)
        assertNull(board.state.value.page)
        assertEquals(RankingStatus.DISCONNECTED, board.state.value.status)
    }

    @Test fun lowerRecomputedScoreDoesNotLowerAcknowledgedHighScoreOrCreateSyntheticWins() = runBlocking {
        val board = HardWinsLeaderboard(); val gateway = Gateway()
        board.setContext(account); board.reconcile(gateway)
        board.setContext(account.copy(localWins = 3)); board.reconcile(gateway)
        assertEquals(1, gateway.submissions.size)
        assertEquals(3L, board.state.value.context?.localWins)
    }
}
