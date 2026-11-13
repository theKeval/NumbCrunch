package io.keval.apps.numbcrunch.domain.connection

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlayerConnectionControllerTest {
    @Test fun failedRevalidationKeepsSelectedProfileAndFreezesDelivery() = runBlocking {
        val repository = FakeProfiles()
        val controller = PlayerConnectionController(repository)
        controller.refresh(connected("a"))
        val account = controller.session.value.selectedProfile
        val oldGeneration = controller.session.value.generation
        controller.refresh(PlayGamesGateway { ConnectionResult.Failed })
        assertEquals(account, controller.session.value.selectedProfile)
        assertEquals(ConnectionStatus.ERROR, controller.session.value.status)
        assertNull(controller.session.value.connectedAccountId)
        assertTrue(controller.session.value.generation > oldGeneration)
    }

    @Test fun lateOldAccountCallbackCannotSelectOrPublishOldProfile() = runBlocking {
        val repository = FakeProfiles()
        val controller = PlayerConnectionController(repository)
        val oldResult = CompletableDeferred<ConnectionResult>()
        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) { controller.refresh(PlayGamesGateway { oldResult.await() }) }
        controller.refresh(connected("b"))
        oldResult.complete(ConnectionResult.Connected(PlayGamesPlayer("a", "Player A")))
        oldJob.join()
        assertEquals("b", controller.session.value.player?.id)
        assertEquals("b", repository.selectedProfile().pgsPlayerId)
        assertFalse(repository.accounts.containsKey("a"))
    }

    @Test fun suspendedActivityIgnoresOutstandingResult() = runBlocking {
        val controller = PlayerConnectionController(FakeProfiles())
        val result = CompletableDeferred<ConnectionResult>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { controller.refresh(PlayGamesGateway { result.await() }) }
        controller.suspendConnection()
        result.complete(ConnectionResult.Connected(PlayGamesPlayer("a", "A")))
        job.join()
        assertNull(controller.session.value.connectedAccountId)
        assertEquals(ConnectionStatus.NOT_CONNECTED, controller.session.value.status)
    }

    @Test fun accountsAndDeliberateGuestSelectionRemainIsolated() = runBlocking {
        val repository = FakeProfiles()
        val controller = PlayerConnectionController(repository)
        controller.initialize()
        val guest = repository.selectedProfile()
        controller.refresh(connected("a"))
        val a = repository.selectedProfile()
        controller.selectGuest()
        controller.refresh(connected("a"))
        assertEquals(guest, repository.selectedProfile())
        controller.refresh(connected("b"))
        assertNotEquals(a, repository.selectedProfile())
        controller.refresh(connected("a"))
        assertEquals(a, repository.selectedProfile())
    }

    @Test fun cancellationAndUnavailableNeverForceLoginOrLoseLocalProfile() = runBlocking {
        val repository = FakeProfiles()
        val controller = PlayerConnectionController(repository)
        controller.initialize()
        val guest = controller.session.value.selectedProfile
        for ((result, status) in listOf(ConnectionResult.Cancelled to ConnectionStatus.CANCELLED,
            ConnectionResult.Unavailable to ConnectionStatus.UNAVAILABLE,
            ConnectionResult.NotConnected to ConnectionStatus.NOT_CONNECTED)) {
            controller.refresh(PlayGamesGateway { result })
            assertEquals(status, controller.session.value.status)
            assertEquals(guest, controller.session.value.selectedProfile)
            assertFalse(controller.session.value.busy)
        }
    }

    @Test fun importConfirmationForPreviousAccountCannotClaimForNewAccount() = runBlocking {
        val repository = FakeProfiles()
        val controller = PlayerConnectionController(repository)
        controller.refresh(connected("a"))
        val oldAccount = controller.session.value.accountProfile!!.id
        controller.refresh(connected("b"))
        controller.importGuest(oldAccount)
        assertTrue(repository.claims.isEmpty())
        val b = controller.session.value.accountProfile!!.id
        controller.importGuest(b)
        assertEquals(b, repository.claims.single().accountProfileId)
        controller.importGuest(b)
        assertEquals(1, repository.claims.size)
    }

    @Test fun profileWriteFailureKeepsPreviousSelectionAndCanRetry() = runBlocking {
        val repository = FakeProfiles()
        val controller = PlayerConnectionController(repository)
        controller.initialize()
        val guest = controller.session.value.selectedProfile
        repository.failActivation = true
        controller.refresh(connected("a"))
        assertEquals(guest, controller.session.value.selectedProfile)
        assertTrue(controller.session.value.localError)
        assertNull(controller.session.value.connectedAccountId)
        repository.failActivation = false
        controller.refresh(connected("a"))
        assertEquals("a", controller.session.value.selectedProfile?.pgsPlayerId)
    }

    private fun connected(id: String) = PlayGamesGateway { ConnectionResult.Connected(PlayGamesPlayer(id, "Player $id")) }
}

private class FakeProfiles : ProgressRepository {
    val accounts = mutableMapOf<String, PlayerProfile>()
    val claims = mutableListOf<GuestProgressClaim>()
    var failActivation = false
    private var guest = PlayerProfile("guest", null, 1)
    private var selected = guest
    private var lastAccount: String? = null
    override suspend fun guestProfile() = guest
    override suspend fun profileForPlayer(pgsPlayerId: String) = accounts.getOrPut(pgsPlayerId) { PlayerProfile(pgsPlayerId, pgsPlayerId, 1) }
    override suspend fun activatePlayerProfile(pgsPlayerId: String): PlayerProfile {
        check(!failActivation)
        return profileForPlayer(pgsPlayerId).also { if (lastAccount != it.id) { selected = it; lastAccount = it.id } }
    }
    override suspend fun selectedProfile() = selected
    override suspend fun selectProfile(profileId: String): PlayerProfile {
        selected = (accounts.values + guest).single { it.id == profileId }
        return selected
    }
    override fun observeSelectedProfile() = flowOf(selected)
    override suspend fun claimGuestProgress(guestProfileId: String, accountProfileId: String): GuestProgressClaim {
        val claim = GuestProgressClaim(guestProfileId, accountProfileId, 2)
        claims += claim
        guest = PlayerProfile("new-guest", null, 2)
        return claim
    }
    override suspend fun exportProgress(ownerId: String): ProgressSnapshot {
        val owner = (accounts.values + guest).single { it.id == ownerId }
        val rounds = if (ownerId == "guest") listOf(RoundRecord("round", ownerId, RoundMode.SINGLE, Difficulty.HARD, "123", 1)) else emptyList()
        return ProgressSnapshot(profile = owner, rounds = rounds)
    }
    override suspend fun recordTutorialCompletion(completion: io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion) = Unit
    override fun observeRounds(ownerId: String): Flow<List<RoundRecord>> = flowOf(emptyList())
    override suspend fun findRound(ownerId: String, roundId: String): RoundRecord? = null
    override suspend fun unfinishedRound(ownerId: String): RoundRecord? = null
    override suspend fun unfinishedRounds(ownerId: String) = emptyList<RoundRecord>()
    override suspend fun createRound(round: RoundRecord): RoundRecord = error("Not used")
    override suspend fun updateRound(round: RoundRecord, expectedRevision: Long): RoundRecord = error("Not used")
    override suspend fun importRounds(ownerId: String, rounds: List<RoundRecord>) = Unit
    override suspend fun importLegacyRound(round: RoundRecord): RoundRecord? = error("Not used")
}
