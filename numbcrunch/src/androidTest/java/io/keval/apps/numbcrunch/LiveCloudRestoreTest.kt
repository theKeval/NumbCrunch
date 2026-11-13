package io.keval.apps.numbcrunch

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.tasks.Task
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.cloud.CloudCodec
import io.keval.apps.numbcrunch.domain.cloud.CloudMerge
import io.keval.apps.numbcrunch.domain.progress.PlayerStatistics
import io.keval.apps.numbcrunch.domain.progress.RoundMode
import io.keval.apps.numbcrunch.domain.progress.RoundStatus
import io.keval.apps.numbcrunch.domain.game.Difficulty
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Opt-in tester-only read: no cloud commits, fake cloud facts, app-data clearing or account changes. */
class LiveCloudRestoreTest {
    @Test fun confirmedSavedGamesRestoresIntoSeparateLocalDatabase(): Unit = runBlocking {
        assumeTrue("Requires an explicitly authorized connected tester and existing cloud backup",
            InstrumentationRegistry.getArguments().getString("liveCloudRestore") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "live-cloud-read-${UUID.randomUUID()}.db"
        val repository = SqliteProgressRepository(context, name)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var activity: ComponentActivity
                scenario.onActivity { activity = it }
                withContext(Dispatchers.Main) {
                    assertTrue("Tester must already be authenticated", PlayGames.getGamesSignInClient(activity).isAuthenticated().awaitLive().isAuthenticated)
                    val account = PlayGames.getPlayersClient(activity).currentPlayer.awaitLive().playerId
                    val client = PlayGames.getSnapshotsClient(activity)
                    val loaded = client.load(true).awaitLive()
                    val metadata = requireNotNull(loaded.get())
                    val opened = mutableListOf<Snapshot>()
                    try {
                        assertFalse("Server metadata unavailable; cached data is insufficient", loaded.isStale)
                        val entries = (0 until metadata.count).map { metadata.get(it).freeze() }.associateBy { it.uniqueName }
                        suspend fun read(name: String): ByteArray {
                            val entry = requireNotNull(entries[name]) { "Referenced cloud file is missing" }
                            assertFalse("Referenced cloud file is not confirmed yet", entry.hasChangePending())
                            assertTrue("Cloud file account changed", entry.owner.playerId == account)
                            val result = client.open(entry, SnapshotsClient.RESOLUTION_POLICY_MANUAL).awaitLive()
                            if (result.isConflict) {
                                result.conflict?.let { opened += it.snapshot; opened += it.conflictingSnapshot }
                                fail("Resolve cloud conflict in the app before read-only restore verification")
                            }
                            val snapshot = requireNotNull(result.data)
                            opened += snapshot
                            assertTrue("Snapshot account changed", snapshot.metadata.owner.playerId == account)
                            val bytes = withContext(Dispatchers.IO) { snapshot.snapshotContents.readFully() }
                            require(bytes.size <= CloudCodec.MAX_FILE_BYTES) { "Cloud file exceeds protocol limit" }
                            client.discardAndClose(snapshot).awaitLive()
                            opened.remove(snapshot)
                            return bytes
                        }
                        val manifest = read(CloudCodec.ROOT_NAME)
                        val chunks = CloudCodec.chunkNames(manifest, account).associateWith { read(it) }
                        val progress = withContext(Dispatchers.Default) { CloudCodec.decode(manifest, chunks, account) }
                        assertTrue("Account changed during download", PlayGames.getPlayersClient(activity).currentPlayer.awaitLive().playerId == account)
                        assertTrue("Cloud contains unresolved versions", CloudMerge.inspect(progress).roundConflicts.isEmpty())
                        val local = repository.activatePlayerProfile(account)
                        val unfinished = progress.versions.mapNotNull { it.unfinished }
                        val outcomes = progress.versions.mapNotNull { it.outcome }
                        if (InstrumentationRegistry.getArguments().getString("liveCloudRequirePaused") == "true") {
                            assertTrue("Expected a real paused Medium round with guess, spent hint and partial input", unfinished.any {
                                it.mode == RoundMode.SINGLE && it.difficulty == Difficulty.MEDIUM && it.status == RoundStatus.PAUSED &&
                                    it.guesses.isNotEmpty() && it.hintedGuessIndex != null && it.currentInput.isNotEmpty()
                            })
                        }
                        repository.importResolvedProgress(account, progress, unfinished.firstOrNull()?.id)
                        repository.importResolvedProgress(account, progress, unfinished.firstOrNull()?.id)
                        val restored = repository.exportProgress(local.id)
                        assertTrue("Completed facts changed during restore", restored.completedFacts.toSet() == outcomes.toSet())
                        assertTrue("Paused inputs, guesses or hint state changed", restored.rounds.toSet() == unfinished.toSet())
                        assertTrue("Statistics changed during restore", restored.statistics == PlayerStatistics.fromFacts(outcomes, unfinished))
                        assertTrue("Tutorial evidence changed", restored.tutorialCompletions.toSet() == progress.tutorialCompletions.toSet())
                        assertTrue("Claimed ownership scope was lost", restored.ownerIds.containsAll(progress.ownerIds))
                        assertTrue("Cloud import must not replay activity events", repository.observePending(account).first().isEmpty())
                        Log.i("NumbCrunchCloudQA", "restore_verified completed=${outcomes.size} unfinished=${unfinished.size} tutorials=${progress.tutorialCompletions.size}")
                    } finally {
                        opened.forEach { snapshot -> runCatching {
                            if (snapshot.snapshotContents?.isClosed == false) client.discardAndClose(snapshot).awaitLive()
                        } }
                        metadata.release()
                    }
                }
            }
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }
}

private suspend fun <T> Task<T>.awaitLive(): T = kotlinx.coroutines.withTimeout(20_000L) {
    suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(IllegalStateException("Live SDK read failed; inspect safe diagnostics"))
        }
    }
}
}
