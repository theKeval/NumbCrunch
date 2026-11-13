package io.keval.apps.numbcrunch.domain.cloud

interface CloudSyncRepository : CloudProgressRepository {
    suspend fun pendingCloudMerge(accountPlayerId: String): CloudProgress?
    suspend fun lastConfirmedBackup(accountPlayerId: String): Long?
    suspend fun recordConfirmedBackup(accountPlayerId: String, at: Long)
}

/** The adapter reads every remote candidate before calling merge; upload happens only after local acceptance. */
fun interface SavedGamesGateway {
    suspend fun exchange(local: CloudProgress, acceptMerged: suspend (CloudProgress) -> CloudProgress): CloudWriteResult
}

data class CloudWriteResult(val progress: CloudProgress, val serverConfirmed: Boolean)

class CloudImportConflict(val progress: CloudProgress) : IllegalStateException("Progress changed during reconciliation")
class CloudChoiceRequired(val progress: CloudProgress) : IllegalStateException("Choose a saved game version")
class CloudAccountChanged : IllegalStateException("Play Games account changed")

enum class BackupStatus { DEVICE_ONLY, SAVING, SAVED, PENDING, NEEDS_ATTENTION }

data class CloudBackupState(
    val status: BackupStatus = BackupStatus.DEVICE_ONLY,
    val accountPlayerId: String? = null,
    val generation: Long = 0,
    val lastSuccessfulBackup: Long? = null,
    val reconciliationReady: Boolean = false,
    val choice: CloudMergeResult? = null,
    val invalidData: Boolean = false,
)
