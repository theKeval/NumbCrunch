package io.keval.apps.numbcrunch.domain.engagement

import io.keval.apps.numbcrunch.domain.cloud.CloudBackupState
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AchievementDeliveryStore {
    suspend fun acknowledgedSteps(playerId: String): Map<AchievementKey, Int>
    suspend fun acknowledge(playerId: String, key: AchievementKey, steps: Int)
}

interface AchievementGateway {
    suspend fun unlock(playerId: String, key: AchievementKey, stillCurrent: () -> Boolean)
    suspend fun setSteps(playerId: String, key: AchievementKey, steps: Int, stillCurrent: () -> Boolean)
}

class AchievementConfigurationException : IllegalStateException("Achievement catalog is not configured")
class StaleAchievementAccountException : IllegalStateException("Achievement account changed")

data class AchievementScope(val playerId: String, val profileId: String, val generation: Long)

fun AchievementScope.isCurrent(session: PlayerSession): Boolean = !session.busy &&
    session.generation == generation && session.player?.id == playerId &&
    session.connectedAccountId == profileId && session.accountProfile?.pgsPlayerId == playerId

fun AchievementScope.isReconciled(session: PlayerSession, backup: CloudBackupState, projectionStamp: String?): Boolean =
    isCurrent(session) && backup.accountPlayerId == playerId && backup.generation == generation &&
        backup.reconciliationReady && backup.choice == null && !backup.invalidData &&
        projectionStamp != null && backup.reconciledStamp == projectionStamp

enum class AchievementSyncStatus { WAITING, SYNCING, SYNCED, RETRY, CONFIGURATION_ERROR }
data class AchievementSyncState(
    val scope: AchievementScope? = null,
    val status: AchievementSyncStatus = AchievementSyncStatus.WAITING,
    val pending: Int = 0,
)

/** Facts persist desired progress; acknowledgements only suppress redundant absolute SDK updates. */
class AchievementSyncController(private val delivery: AchievementDeliveryStore) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AchievementSyncState())
    val state = mutableState.asStateFlow()

    fun waitForReconciliation(scope: AchievementScope?) {
        mutableState.value = AchievementSyncState(scope = scope)
    }

    suspend fun reconcile(
        scope: AchievementScope,
        progress: List<AchievementProgress>,
        gateway: AchievementGateway,
        stillCurrent: () -> Boolean,
    ) = mutex.withLock {
        if (!stillCurrent()) return@withLock
        require(scope.playerId.isNotBlank() && scope.profileId.isNotBlank()) { "Missing achievement owner" }
        require(progress.map { it.definition.key }.distinct().size == progress.size) { "Duplicate achievement projection" }
        progress.forEach {
            require(it.definition in AchievementCatalog.definitions && it.steps in 0..it.definition.target) { "Invalid achievement projection" }
        }
        try {
            val acknowledged = delivery.acknowledgedSteps(scope.playerId)
            val pending = progress.filter { it.steps > (acknowledged[it.definition.key] ?: 0) }
            if (!stillCurrent()) return@withLock
            mutableState.value = AchievementSyncState(scope, AchievementSyncStatus.SYNCING, pending.size)
            pending.forEachIndexed { index, item ->
                if (!stillCurrent()) return@withLock
                if (item.definition.incremental) gateway.setSteps(scope.playerId, item.definition.key, item.steps, stillCurrent)
                else gateway.unlock(scope.playerId, item.definition.key, stillCurrent)
                if (!stillCurrent()) return@withLock
                delivery.acknowledge(scope.playerId, item.definition.key, item.steps)
                if (!stillCurrent()) return@withLock
                mutableState.value = AchievementSyncState(scope, AchievementSyncStatus.SYNCING, pending.size - index - 1)
            }
            if (stillCurrent()) mutableState.value = AchievementSyncState(scope, AchievementSyncStatus.SYNCED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: StaleAchievementAccountException) { /* The next authenticated generation will reconcile. */ }
        catch (_: AchievementConfigurationException) {
            if (stillCurrent()) mutableState.value = AchievementSyncState(scope, AchievementSyncStatus.CONFIGURATION_ERROR)
        } catch (_: Exception) {
            if (stillCurrent()) mutableState.value = mutableState.value.copy(scope = scope, status = AchievementSyncStatus.RETRY)
        }
    }
}
