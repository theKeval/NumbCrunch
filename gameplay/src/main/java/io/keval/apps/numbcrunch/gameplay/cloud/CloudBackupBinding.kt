package io.keval.apps.numbcrunch.gameplay.cloud

import androidx.compose.runtime.staticCompositionLocalOf
import io.keval.apps.numbcrunch.domain.cloud.CloudBackupState
import kotlinx.coroutines.flow.StateFlow

data class CloudBackupBinding(
    val state: StateFlow<CloudBackupState>,
    val retry: () -> Unit,
    val chooseVersion: (account: String, generation: Long, roundId: String, versionId: String) -> Unit,
    val keepSavedRounds: (account: String, generation: Long, selectedRoundId: String?) -> Unit,
)

val LocalCloudBackup = staticCompositionLocalOf<CloudBackupBinding?> { null }
