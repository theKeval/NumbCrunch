package io.keval.apps.numbcrunch.domain.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes network reconciliation; game writes remain independent SQLite transactions. */
class CloudBackupController(
    private val repository: CloudSyncRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(CloudBackupState())
    val state: StateFlow<CloudBackupState> = mutableState.asStateFlow()
    private var localChangeRevision = 0L

    suspend fun synchronize(account: String, generation: Long, gateway: SavedGamesGateway, isCurrent: () -> Boolean) = mutex.withLock {
        if (!isCurrent()) return
        mutableState.value = CloudBackupState(BackupStatus.SAVING, account, generation)
        try {
            val lastBackup = repository.lastConfirmedBackup(account)
            ensureCurrent(isCurrent)
            mutableState.value = mutableState.value.copy(lastSuccessfulBackup = lastBackup)
            val local = repository.exportCloudProgress(account)
            val pending = repository.pendingCloudMerge(account)
            val initial = pending?.let { withContext(Dispatchers.Default) { CloudMerge.merge(local, it, account).progress } } ?: local
            ensureCurrent(isCurrent)
            val result = gateway.exchange(initial) { candidate ->
                ensureCurrent(isCurrent)
                // Local input may have committed while the network was loading. Re-read before import.
                val fresh = repository.exportCloudProgress(account)
                val merged = withContext(Dispatchers.Default) { CloudMerge.merge(fresh, candidate, account) }
                val selected = repository.preferredCloudRound(account)
                ensureCurrent(isCurrent)
                if (merged.roundConflicts.isNotEmpty() || (merged.unfinishedRoundIds.size > 1 && selected !in merged.unfinishedRoundIds)) {
                    repository.retainPendingMerge(account, merged.progress)
                    throw CloudChoiceRequired(merged.progress)
                }
                val stamp = repository.importAndStamp(account, merged.progress, selected ?: merged.unfinishedRoundIds.singleOrNull())
                ensureCurrent(isCurrent)
                mutableState.value = mutableState.value.copy(reconciliationReady = false, reconciledStamp = stamp)
                repository.exportCloudProgress(account)
            }
            ensureCurrent(isCurrent)
            if (result.serverConfirmed) {
                val at = now()
                repository.recordConfirmedBackup(account, at)
                val observedRevision = localChangeRevision
                val confirmedLocal = repository.exportCloudProgress(account)
                val currentStamp = withContext(Dispatchers.Default) { confirmedLocal.cloudProjectionStamp() }
                ensureCurrent(isCurrent)
                val covered = currentStamp == mutableState.value.reconciledStamp && observedRevision == localChangeRevision
                mutableState.value = mutableState.value.copy(status = if (covered) BackupStatus.SAVED else BackupStatus.PENDING,
                    lastSuccessfulBackup = at, reconciliationReady = covered)
            } else mutableState.value = mutableState.value.copy(status = BackupStatus.PENDING)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CloudAccountChanged) {
            if (isCurrent()) mutableState.value = mutableState.value.copy(status = BackupStatus.PENDING, reconciliationReady = false)
        } catch (choice: CloudChoiceRequired) {
            if (isCurrent()) attention(account, choice.progress, isCurrent)
        } catch (race: CloudImportConflict) {
            if (isCurrent()) attention(account, race.progress, isCurrent)
        } catch (_: IllegalArgumentException) {
            if (isCurrent()) mutableState.value = mutableState.value.copy(status = BackupStatus.NEEDS_ATTENTION, invalidData = true, reconciliationReady = false)
        } catch (_: IllegalStateException) {
            if (isCurrent()) mutableState.value = mutableState.value.copy(status = BackupStatus.NEEDS_ATTENTION, invalidData = true, reconciliationReady = false)
        } catch (_: Exception) {
            if (isCurrent()) mutableState.value = mutableState.value.copy(status = BackupStatus.PENDING, reconciliationReady = false)
        }
    }

    /** A dialog cancellation does not call this; candidates stay durable and local play is untouched. */
    suspend fun chooseVersion(account: String, roundId: String, versionId: String, isCurrent: () -> Boolean) = mutex.withLock {
        ensureCurrent(isCurrent)
        val pending = requireNotNull(repository.pendingCloudMerge(account)) { "No pending cloud choice" }
        val fresh = CloudMerge.merge(repository.exportCloudProgress(account), pending, account).progress
        val resolved = CloudMerge.resolve(fresh, roundId, versionId)
        repository.retainPendingMerge(account, resolved.progress)
        ensureCurrent(isCurrent)
        mutableState.value = mutableState.value.copy(choice = resolved, status = BackupStatus.NEEDS_ATTENTION)
    }

    suspend fun keepSavedRounds(account: String, selectedRoundId: String?, isCurrent: () -> Boolean) = mutex.withLock {
        ensureCurrent(isCurrent)
        val pending = requireNotNull(repository.pendingCloudMerge(account)) { "No pending cloud choice" }
        try {
            val stamp = repository.importAndStamp(account, pending, selectedRoundId)
            ensureCurrent(isCurrent)
            mutableState.value = mutableState.value.copy(status = BackupStatus.PENDING, choice = null,
                reconciliationReady = false, reconciledStamp = stamp)
        } catch (race: CloudImportConflict) {
            attention(account, race.progress, isCurrent)
        }
    }

    fun localProgressChanged() {
        localChangeRevision++
        if (mutableState.value.status == BackupStatus.SAVED) mutableState.value = mutableState.value.copy(
            status = BackupStatus.PENDING, reconciliationReady = false)
    }

    fun choiceFailed() { mutableState.value = mutableState.value.copy(status = BackupStatus.NEEDS_ATTENTION,
        invalidData = true, reconciliationReady = false) }

    /** Called after cancelling the old foreground job; no SDK result may revive its account. */
    fun disconnected() { mutableState.value = CloudBackupState() }

    private suspend fun attention(account: String, progress: CloudProgress, isCurrent: () -> Boolean) {
        repository.retainPendingMerge(account, progress)
        if (!isCurrent()) return
        mutableState.value = mutableState.value.copy(status = BackupStatus.NEEDS_ATTENTION,
            choice = CloudMerge.inspect(progress), reconciliationReady = false)
    }

    private fun ensureCurrent(isCurrent: () -> Boolean) { if (!isCurrent()) throw CloudAccountChanged() }
}
