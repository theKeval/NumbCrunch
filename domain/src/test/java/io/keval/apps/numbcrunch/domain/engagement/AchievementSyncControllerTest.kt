package io.keval.apps.numbcrunch.domain.engagement

import io.keval.apps.numbcrunch.domain.cloud.CloudBackupState
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.connection.PlayGamesPlayer
import io.keval.apps.numbcrunch.domain.progress.PlayerProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AchievementSyncControllerTest {
    private class Store : AchievementDeliveryStore {
        val values = mutableMapOf<String, MutableMap<AchievementKey, Int>>()
        var failWrite = false
        override suspend fun acknowledgedSteps(playerId: String) = values[playerId].orEmpty()
        override suspend fun acknowledge(playerId: String, key: AchievementKey, steps: Int) {
            if (failWrite) error("Injected storage failure")
            val player = values.getOrPut(playerId) { mutableMapOf() }
            player[key] = maxOf(player[key] ?: 0, steps)
        }
    }
    private class Gateway : AchievementGateway {
        val calls = mutableListOf<Triple<String, AchievementKey, Int>>()
        var action: () -> Unit = {}
        override suspend fun unlock(playerId: String, key: AchievementKey, stillCurrent: () -> Boolean) {
            calls += Triple(playerId, key, 1); action()
        }
        override suspend fun setSteps(playerId: String, key: AchievementKey, steps: Int, stillCurrent: () -> Boolean) {
            calls += Triple(playerId, key, steps); action()
        }
    }
    private val scope = AchievementScope("player-A", "profile-A", 1)
    private fun progress(key: AchievementKey, steps: Int) = AchievementProgress(AchievementCatalog.definitions.single { it.key == key }, steps)

    @Test fun `absolute cumulative delivery retries safely and never lowers acknowledgement`() = runBlocking {
        val store = Store(); val gateway = Gateway(); val controller = AchievementSyncController(store)
        val key = AchievementKey.CENTURY_SOLVER
        gateway.action = { error("Offline") }
        controller.reconcile(scope, listOf(progress(key, 4)), gateway) { true }
        assertEquals(AchievementSyncStatus.RETRY, controller.state.value.status)
        assertTrue(store.values.isEmpty())
        gateway.action = {}
        controller.reconcile(scope, listOf(progress(key, 7)), gateway) { true }
        controller.reconcile(scope, listOf(progress(key, 7)), gateway) { true }
        controller.reconcile(scope, listOf(progress(key, 4)), gateway) { true }
        assertEquals(listOf(4, 7), gateway.calls.map { it.third })
        assertEquals(7, store.values[scope.playerId]?.get(key))
    }

    @Test fun `standard achievements unlock once and acknowledgement loss repeats only absolute action`() = runBlocking {
        val store = Store(); val gateway = Gateway(); val controller = AchievementSyncController(store)
        val desired = listOf(progress(AchievementKey.FIRST_SOLO_SOLVE, 1))
        store.failWrite = true
        controller.reconcile(scope, desired, gateway) { true }
        store.failWrite = false
        controller.reconcile(scope, desired, gateway) { true }
        controller.reconcile(scope, desired, gateway) { true }
        assertEquals(2, gateway.calls.size)
        assertEquals(AchievementSyncStatus.SYNCED, controller.state.value.status)
    }

    @Test fun `partly delivered catalog retries only unacknowledged awards`() = runBlocking {
        val store = Store(); val gateway = Gateway(); val controller = AchievementSyncController(store)
        val desired = listOf(progress(AchievementKey.FIRST_SOLO_SOLVE, 1), progress(AchievementKey.CENTURY_SOLVER, 4))
        gateway.action = { if (gateway.calls.size == 2) error("Offline") }
        controller.reconcile(scope, desired, gateway) { true }
        assertEquals(1, store.values[scope.playerId]?.size)
        gateway.action = {}
        controller.reconcile(scope, desired, gateway) { true }
        assertEquals(listOf(AchievementKey.FIRST_SOLO_SOLVE, AchievementKey.CENTURY_SOLVER, AchievementKey.CENTURY_SOLVER), gateway.calls.map { it.second })
    }

    @Test fun `late account A response cannot acknowledge A or submit remaining awards for B`() = runBlocking {
        val store = Store(); val gateway = Gateway(); val controller = AchievementSyncController(store)
        var generation = 1L
        val desired = listOf(progress(AchievementKey.FIRST_SOLO_SOLVE, 1), progress(AchievementKey.CENTURY_SOLVER, 3))
        gateway.action = { generation = 2 }
        controller.reconcile(scope, desired, gateway) { generation == scope.generation }
        assertTrue(store.values.isEmpty())
        assertEquals(1, gateway.calls.size)
        gateway.action = {}
        val b = AchievementScope("player-B", "profile-B", 2)
        controller.reconcile(b, desired, gateway) { generation == b.generation }
        assertEquals(listOf("player-A", "player-B", "player-B"), gateway.calls.map { it.first })
        assertNull(store.values["player-A"])
        assertEquals(2, store.values["player-B"]?.size)
    }

    @Test fun `not reconciled account does no work and configuration failure is visible`() = runBlocking {
        val store = Store(); val gateway = Gateway(); val controller = AchievementSyncController(store)
        val desired = listOf(progress(AchievementKey.FIRST_SOLO_SOLVE, 1))
        controller.reconcile(scope, desired, gateway) { false }
        assertTrue(gateway.calls.isEmpty())
        gateway.action = { throw AchievementConfigurationException() }
        controller.reconcile(scope, desired, gateway) { true }
        assertEquals(AchievementSyncStatus.CONFIGURATION_ERROR, controller.state.value.status)
        assertTrue(store.values.isEmpty())
    }

    @Test fun `cloud ready must cover the exact facts and authenticated generation`() {
        val session = PlayerSession(status = ConnectionStatus.CONNECTED, player = PlayGamesPlayer("player-A", "Player"),
            accountProfile = PlayerProfile("profile-A", "player-A", 1), selectedProfile = PlayerProfile("profile-A", "player-A", 1), generation = 1)
        val ready = CloudBackupState(accountPlayerId = "player-A", generation = 1, reconciliationReady = true, reconciledStamp = "old-facts")
        assertTrue(scope.isReconciled(session, ready, "old-facts"))
        assertFalse(scope.isReconciled(session, ready, "new-win"))
        assertFalse(scope.isReconciled(session, ready, null))
        assertFalse(scope.isReconciled(session, ready.copy(reconciliationReady = false), "old-facts"))
        assertFalse(scope.isReconciled(session, ready.copy(accountPlayerId = "player-B"), "old-facts"))
        assertFalse(scope.isReconciled(session.copy(generation = 2), ready, "old-facts"))
        assertFalse(scope.isReconciled(session.copy(importing = true), ready, "old-facts"))
        assertFalse(scope.isReconciled(session.copy(selectedProfile = PlayerProfile("guest", null, 1)), ready, "old-facts"))
    }
}
