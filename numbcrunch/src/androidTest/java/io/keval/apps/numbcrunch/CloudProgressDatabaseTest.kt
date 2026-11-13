package io.keval.apps.numbcrunch

import androidx.test.platform.app.InstrumentationRegistry
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.cloud.*
import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CloudProgressDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val aName = "cloud-a-${UUID.randomUUID()}.db"
    private val bName = "cloud-b-${UUID.randomUUID()}.db"
    private var a = SqliteProgressRepository(context, aName) { 10 }
    private var b = SqliteProgressRepository(context, bName) { 20 }
    @After fun cleanUp() { a.close(); b.close(); context.deleteDatabase(aName); context.deleteDatabase(bName) }

    @Test fun restoreAcrossLocalNamespacesKeepsStatisticsTutorialAndExactPausedRound() = runBlocking {
        val aProfile = a.activatePlayerProfile("platform-player")
        val bProfile = b.activatePlayerProfile("platform-player")
        var won = a.createRound(round("won", aProfile.id))
        for (change in listOf<(RoundRecord) -> RoundRecord>({ it.editInput("456", 11) }, { it.submitGuess(12) },
            { it.useHint(0, 13) }, { it.editInput("123", 14) }, { it.submitGuess(15) }))
            won = a.updateRound(change(won), won)
        a.recordTutorialCompletion(TutorialCompletion(aProfile.id, 16))
        var paused = a.createRound(round("paused", aProfile.id).copy(createdAt = 16, updatedAt = 16))
        for (change in listOf<(RoundRecord) -> RoundRecord>({ it.editInput("456", 17) }, { it.submitGuess(18) },
            { it.useHint(0, 19) }, { it.editInput("0", 20) }, { it.pause(21) }))
            paused = a.updateRound(change(paused), paused)
        val cloud = a.exportCloudProgress("platform-player")
        assertTrue(cloud.versions.single { it.roundId == "won" }.terminal)
        b.importResolvedProgress("platform-player", cloud, paused.id)
        b.importResolvedProgress("platform-player", cloud, paused.id)
        val restored = b.exportProgress(bProfile.id)
        assertEquals(a.exportProgress(aProfile.id).statistics, restored.statistics)
        assertEquals(1, restored.completedFacts.size)
        assertEquals(1, restored.tutorialCompletions.size)
        assertEquals(paused, b.findRound(bProfile.id, paused.id))
        assertEquals(aProfile.id, restored.associatedProfiles.single().id)
        assertNotEquals(aProfile.id, b.guestProfile().id)
        assertTrue(runCatching { b.claimGuestProgress(aProfile.id, bProfile.id) }.isFailure)
        b.close(); b = SqliteProgressRepository(context, bName)
        assertEquals(restored, b.exportProgress(bProfile.id))
        assertEquals(0, CloudMerge.merge(cloud, b.exportCloudProgress("platform-player"), "platform-player").roundConflicts.size)
    }

    @Test fun failedImportRollsBackNamespacesFactsAndTutorialTogether() = runBlocking {
        val owner = a.activatePlayerProfile("player")
        val target = b.activatePlayerProfile("player")
        val created = a.createRound(round("source", owner.id))
        val input = a.updateRound(created.editInput("123", 11), created)
        a.updateRound(input.submitGuess(12), input)
        a.recordTutorialCompletion(TutorialCompletion(owner.id, 13))
        val cloud = a.exportCloudProgress("player")
        context.openOrCreateDatabase(bName, 0, null).use {
            it.execSQL("CREATE TRIGGER reject_import BEFORE INSERT ON cloud_outcomes BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        }
        assertTrue(runCatching { b.importResolvedProgress("player", cloud, null) }.isFailure)
        val untouched = b.exportProgress(target.id)
        assertTrue(untouched.associatedProfiles.isEmpty())
        assertTrue(untouched.outcomes.isEmpty())
        assertTrue(untouched.tutorialCompletions.isEmpty())
        context.openOrCreateDatabase(bName, 0, null).use { it.execSQL("DROP TRIGGER reject_import") }
        b.importResolvedProgress("player", cloud, null)
        assertEquals(1L, b.exportProgress(target.id).statistics.wins)
    }

    @Test fun localMutationAfterConflictPromptCannotBeOverwrittenByOldChoice() = runBlocking {
        val owner = a.activatePlayerProfile("player")
        var local = a.createRound(round("same", owner.id))
        val base = a.exportCloudProgress("player")
        val remoteRecord = local.editInput("7", 11)
        val remote = base.copy(versions = listOf(base.versions.single().advance(remoteRecord)))
        local = a.updateRound(local.editInput("8", 12), local)
        val conflict = CloudMerge.merge(a.exportCloudProgress("player"), remote, "player")
        a.retainPendingMerge("player", conflict.progress)
        val resolved = CloudMerge.resolve(conflict.progress, local.id, remote.versions.single().versionId)
        local = a.updateRound(local.editInput("89", 13), local)
        assertTrue(runCatching { a.importResolvedProgress("player", resolved.progress, local.id) }.exceptionOrNull() is CloudImportConflict)
        assertEquals(local, a.findRound(owner.id, local.id))
        a.close(); a = SqliteProgressRepository(context, aName)
        assertNotNull(a.pendingCloudMerge("player"))
    }

    @Test fun equalRevisionCloudBranchCannotBeOverwrittenByStaleGameplayWrite() = runBlocking {
        val owner = a.activatePlayerProfile("player")
        val created = a.createRound(round("same-revision", owner.id))
        val base = a.exportCloudProgress("player")
        val remoteRecord = created.editInput("7", 11)
        val remote = base.copy(versions = listOf(base.versions.single().advance(remoteRecord)))
        val local = a.updateRound(created.editInput("8", 11), created)
        assertEquals(local.revision, remoteRecord.revision)
        val conflict = CloudMerge.merge(a.exportCloudProgress("player"), remote, "player")
        val chosen = CloudMerge.resolve(conflict.progress, local.id, remote.versions.single().versionId)
        a.importResolvedProgress("player", chosen.progress, local.id)
        assertEquals(remoteRecord, a.findRound(owner.id, local.id))
        val failed = runCatching { a.updateRound(local.editInput("89", 12), local) }.exceptionOrNull()
        assertTrue(failed is RoundConflictException)
        assertEquals(remoteRecord, a.findRound(owner.id, local.id))
    }

    @Test fun migrationFromSchemaTwoPreservesLocalRound() = runBlocking {
        val owner = a.guestProfile()
        val saved = a.createRound(round("saved", owner.id))
        a.close()
        context.openOrCreateDatabase(aName, 0, null).use { db ->
            for (table in listOf("game_stats_outbox", "cloud_chunks", "cloud_manifests", "tutorial_completions", "cloud_outcomes", "remote_namespaces")) db.execSQL("DROP TABLE $table")
            db.version = 2
        }
        a = SqliteProgressRepository(context, aName)
        assertEquals(saved, a.findRound(owner.id, saved.id))
        val account = a.activatePlayerProfile("player")
        a.claimGuestProgress(owner.id, account.id)
        assertEquals(saved, a.exportCloudProgress("player").versions.single().unfinished)
    }

    @Test fun oldLocalCacheChunksArePrunedWhilePendingAndRecoveryBytesRemainReadable() = runBlocking {
        val owner = a.activatePlayerProfile("player")
        var current = a.createRound(round("cache", owner.id))
        val first = a.exportCloudProgress("player")
        a.retainPendingMerge("player", first)
        repeat(12) { index ->
            current = a.updateRound(current.editInput((index % 10).toString(), 11L + index), current)
            a.exportCloudProgress("player")
        }
        assertEquals(first, a.pendingCloudMerge("player"))
        context.openOrCreateDatabase(aName, 0, null).use { db ->
            val referenced = db.rawQuery("SELECT payload FROM cloud_manifests", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) addAll(CloudCodec.chunkNames(cursor.getBlob(0), "player")) }
            }
            val actual = db.rawQuery("SELECT name FROM cloud_chunks", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertEquals(referenced, actual)
            assertEquals(2, actual.size)
        }
        assertEquals(current, a.exportCloudProgress("player").versions.single().unfinished)
    }

    @Test fun manyClaimedNamespacesUseBoundedSqlParameters() = runBlocking {
        val local = b.activatePlayerProfile("player")
        val remote = PlayerProfile("remote", "player", 1)
        val claims = (0 until 1000).map { GuestProgressClaim("guest-$it", remote.id, 2) }
        val completed = OutcomeFact.fromRound(round("remote-win", "guest-999").editInput("123", 11).submitGuess(12))
        val cloud = CloudProgress("player", listOf(remote), claims,
            listOf(CloudRoundVersion.initial(completed)), listOf(TutorialCompletion("guest-998", 13)))
        b.importResolvedProgress("player", cloud, null)
        val snapshot = b.exportProgress(local.id)
        assertEquals(1002, snapshot.ownerIds.size)
        assertEquals(1L, snapshot.statistics.wins)
        assertEquals(1, snapshot.tutorialCompletions.size)
    }

    private fun round(id: String, owner: String) = RoundRecord(id, owner, RoundMode.SINGLE, Difficulty.MEDIUM, "123", 10)
}
