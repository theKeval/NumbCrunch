package io.keval.apps.numbcrunch.domain.cloud

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CloudBackupControllerTest {
    private val profile = PlayerProfile("local", "player", 1)
    private val empty = CloudProgress("player", listOf(profile))
    private val round = RoundRecord("round", profile.id, RoundMode.SINGLE, Difficulty.HARD, "123", 1)

    @Test fun sdkBufferedWriteIsNotConfirmedBackupOrSubmissionAuthority() = runBlocking {
        val repository = FakeRepository(empty)
        val controller = CloudBackupController(repository) { 20 }
        controller.synchronize("player", 1, SavedGamesGateway { local, accept ->
            CloudWriteResult(accept(local), false)
        }) { true }
        assertEquals(BackupStatus.PENDING, controller.state.value.status)
        assertFalse(controller.state.value.reconciliationReady)
        assertNull(repository.confirmedAt)
        assertEquals(empty.cloudProjectionStamp(), controller.state.value.reconciledStamp)
    }

    @Test fun confirmedExactCoverageEnablesSubmissionButNewLocalWriteInvalidatesIt() = runBlocking {
        val repository = FakeRepository(empty)
        val controller = CloudBackupController(repository) { 20 }
        controller.synchronize("player", 1, SavedGamesGateway { local, accept ->
            CloudWriteResult(accept(local), true)
        }) { true }
        assertEquals(BackupStatus.SAVED, controller.state.value.status)
        assertTrue(controller.state.value.reconciliationReady)
        assertEquals(20L, repository.confirmedAt)
        controller.localProgressChanged()
        assertEquals(BackupStatus.PENDING, controller.state.value.status)
        assertFalse(controller.state.value.reconciliationReady)
    }

    @Test fun writeDuringUploadCannotBeCoveredByOlderCloudConfirmation() = runBlocking {
        val repository = FakeRepository(empty)
        val controller = CloudBackupController(repository) { 20 }
        controller.synchronize("player", 1, SavedGamesGateway { local, accept ->
            val uploaded = accept(local)
            repository.local = local.copy(versions = listOf(CloudRoundVersion.initial(round.editInput("123", 2).submitGuess(3))))
            CloudWriteResult(uploaded, true)
        }) { true }
        assertEquals(BackupStatus.PENDING, controller.state.value.status)
        assertFalse(controller.state.value.reconciliationReady)
        assertNotEquals(repository.local.cloudProjectionStamp(), controller.state.value.reconciledStamp)
    }

    @Test fun staleAccountCallbackCannotImportOrReviveConnection() = runBlocking {
        val repository = FakeRepository(empty)
        val controller = CloudBackupController(repository)
        val entered = CompletableDeferred<Unit>()
        val continueRead = CompletableDeferred<Unit>()
        var current = true
        val work = async {
            controller.synchronize("player", 4, SavedGamesGateway { local, accept ->
                entered.complete(Unit); continueRead.await()
                CloudWriteResult(accept(local), true)
            }) { current }
        }
        entered.await(); current = false; controller.disconnected(); continueRead.complete(Unit); work.await()
        assertEquals(CloudBackupState(), controller.state.value)
        assertEquals(0, repository.importCount)
        assertNull(repository.confirmedAt)
    }

    @Test fun staleLocalMetadataReadCannotReviveOldAccountUi() = runBlocking {
        val repository = FakeRepository(empty)
        val controller = CloudBackupController(repository)
        var current = true
        repository.confirmedAt = 10
        repository.beforeLastBackupRead = { current = false; controller.disconnected() }
        controller.synchronize("player", 2, SavedGamesGateway { _, _ -> error("Stale account must not reach SDK") }) { current }
        assertEquals(CloudBackupState(), controller.state.value)
    }

    @Test fun divergentVersionsRemainDurableUntilExplicitChoiceAndConfirmation() = runBlocking {
        val base = CloudRoundVersion.initial(round)
        val left = base.advance(round.editInput("1", 2))
        val right = base.advance(round.editInput("2", 2))
        val repository = FakeRepository(empty.copy(versions = listOf(left)))
        val controller = CloudBackupController(repository)
        controller.synchronize("player", 1, SavedGamesGateway { _, accept ->
            CloudWriteResult(accept(empty.copy(versions = listOf(right))), true)
        }) { true }
        assertEquals(BackupStatus.NEEDS_ATTENTION, controller.state.value.status)
        assertEquals(2, repository.pending!!.versions.size)
        assertEquals(listOf(left), repository.local.versions)
        assertEquals(0, repository.importCount)
        // Closing UI makes no repository call, so both versions are retained across controller recreation.
        assertEquals(2, FakeRepository(repository.local, repository.pending).pending!!.versions.size)
        controller.chooseVersion("player", round.id, left.versionId) { true }
        assertTrue(controller.state.value.choice!!.roundConflicts.isEmpty())
        controller.keepSavedRounds("player", round.id) { true }
        assertNull(repository.pending)
        assertEquals("1", repository.local.versions.single().unfinished!!.currentInput)
        assertFalse(controller.state.value.reconciliationReady)
    }

    @Test fun offlineAndInvalidDataKeepLocalFactsAndAllowRetry() = runBlocking {
        val repository = FakeRepository(empty)
        val controller = CloudBackupController(repository)
        controller.synchronize("player", 1, SavedGamesGateway { _, _ -> throw IOException("offline") }) { true }
        assertEquals(BackupStatus.PENDING, controller.state.value.status)
        controller.synchronize("player", 1, SavedGamesGateway { _, _ -> throw IllegalArgumentException("bad archive") }) { true }
        assertEquals(BackupStatus.NEEDS_ATTENTION, controller.state.value.status)
        assertTrue(controller.state.value.invalidData)
        assertEquals(empty, repository.local)
        controller.synchronize("player", 1, SavedGamesGateway { local, accept -> CloudWriteResult(accept(local), true) }) { true }
        assertEquals(BackupStatus.SAVED, controller.state.value.status)
        assertFalse(controller.state.value.invalidData)
    }

    @Test fun multipleUnfinishedRoundsRequireExplicitPreferenceAndKeepBoth() = runBlocking {
        val repository = FakeRepository(empty.copy(versions = listOf(CloudRoundVersion.initial(round))))
        val remote = empty.copy(versions = listOf(CloudRoundVersion.initial(round.copy(id = "another"))))
        val controller = CloudBackupController(repository)
        val gateway = SavedGamesGateway { _, accept -> CloudWriteResult(accept(remote), true) }
        controller.synchronize("player", 1, gateway) { true }
        assertEquals(BackupStatus.NEEDS_ATTENTION, controller.state.value.status)
        controller.keepSavedRounds("player", round.id) { true }
        assertEquals(2, repository.local.versions.size)
        assertEquals(round.id, repository.preferred)
        controller.synchronize("player", 1, gateway) { true }
        assertEquals(BackupStatus.SAVED, controller.state.value.status)
    }

    private class FakeRepository(var local: CloudProgress, var pending: CloudProgress? = null) : CloudSyncRepository {
        var confirmedAt: Long? = null
        var preferred: String? = null
        var importCount = 0
        var beforeLastBackupRead: () -> Unit = {}
        override suspend fun exportCloudProgress(accountPlayerId: String) = local.also { it.validate(accountPlayerId) }
        override suspend fun pendingCloudMerge(accountPlayerId: String) = pending
        override suspend fun retainPendingMerge(accountPlayerId: String, progress: CloudProgress) { pending = progress }
        override suspend fun lastConfirmedBackup(accountPlayerId: String): Long? { beforeLastBackupRead(); return confirmedAt }
        override suspend fun recordConfirmedBackup(accountPlayerId: String, at: Long) { confirmedAt = at }
        override suspend fun preferredCloudRound(accountPlayerId: String) = preferred
        override suspend fun importResolvedProgress(accountPlayerId: String, progress: CloudProgress, selectedRoundId: String?) {
            importAndStamp(accountPlayerId, progress, selectedRoundId)
        }
        override suspend fun importAndStamp(accountPlayerId: String, progress: CloudProgress, selectedRoundId: String?): String {
            val merged = CloudMerge.merge(local, progress, accountPlayerId)
            if (merged.roundConflicts.isNotEmpty() || (merged.unfinishedRoundIds.size > 1 && selectedRoundId !in merged.unfinishedRoundIds))
                throw CloudImportConflict(merged.progress)
            local = merged.progress; pending = null; preferred = selectedRoundId; importCount++
            return local.cloudProjectionStamp()
        }
    }
}
