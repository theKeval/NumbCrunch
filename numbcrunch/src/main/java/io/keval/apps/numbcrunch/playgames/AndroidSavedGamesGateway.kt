package io.keval.apps.numbcrunch.playgames

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import io.keval.apps.numbcrunch.domain.cloud.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Activity-bound PGS exchange. Immutable chunks are committed before the manifest that references them. */
class AndroidSavedGamesGateway(
    private val activity: Activity,
    private val accountPlayerId: String,
    private val isCurrent: () -> Boolean,
) : SavedGamesGateway {
    private val client get() = PlayGames.getSnapshotsClient(activity)
    private val opened = mutableListOf<Snapshot>()

    override suspend fun exchange(local: CloudProgress, acceptMerged: suspend (CloudProgress) -> CloudProgress): CloudWriteResult =
        withContext(Dispatchers.Main.immediate) {
            var stage = "account_check"
            try {
                withTimeoutOrNull(120_000L) {
                    checkAccount()
                    stage = "open_root"
                    val maxFileSize = client.maxDataSize.awaitSnapshotTask()
                    var result = client.open(CloudCodec.ROOT_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL).awaitSnapshotTask()
                    var current = local
                    repeat(3) {
                        checkAccount()
                        if (result.isConflict) {
                            val conflict = requireNotNull(result.conflict)
                            val server = track(conflict.snapshot)
                            val other = track(conflict.conflictingSnapshot)
                            stage = "read_conflicts"
                            for (snapshot in listOf(server, other)) {
                                readProgress(snapshot)?.let { current = CloudMerge.merge(current, it, accountPlayerId).progress }
                            }
                            stage = "merge_local"
                            current = acceptMerged(current)
                            val bundle = withContext(Dispatchers.Default) { CloudCodec.encode(current) }
                            stage = "write_chunks"
                            writeChunks(bundle, maxFileSize)
                            stage = "confirm_chunks"
                            if (!confirmUploaded(bundle, includeRoot = false)) {
                                diagnostic(stage, "pending")
                                return@withTimeoutOrNull CloudWriteResult(current, false)
                            }
                            checkSnapshotAccount(server); checkSnapshotAccount(other)
                            require(bundle.manifest.size <= maxFileSize)
                            check(withContext(Dispatchers.IO) { conflict.resolutionSnapshotContents.writeBytes(bundle.manifest) }) { "Snapshot write failed" }
                            checkAccount()
                            result = client.resolveConflict(conflict.conflictId, server.metadata.snapshotId,
                                description(), conflict.resolutionSnapshotContents).awaitSnapshotTask()
                        } else {
                            val snapshot = track(requireNotNull(result.data))
                            stage = "read_root"
                            readProgress(snapshot)?.let { current = CloudMerge.merge(current, it, accountPlayerId).progress }
                            stage = "merge_local"
                            current = acceptMerged(current)
                            val bundle = withContext(Dispatchers.Default) { CloudCodec.encode(current) }
                            stage = "write_chunks"
                            writeChunks(bundle, maxFileSize)
                            stage = "confirm_chunks"
                            if (!confirmUploaded(bundle, includeRoot = false)) {
                                diagnostic(stage, "pending")
                                return@withTimeoutOrNull CloudWriteResult(current, false)
                            }
                            checkSnapshotAccount(snapshot)
                            require(bundle.manifest.size <= maxFileSize)
                            stage = "commit_root"
                            if (readBytes(snapshot).contentEquals(bundle.manifest)) {
                                client.discardAndClose(snapshot).awaitSnapshotTask()
                            } else {
                                check(withContext(Dispatchers.IO) { snapshot.snapshotContents.writeBytes(bundle.manifest) }) { "Snapshot write failed" }
                                checkAccount()
                                client.commitAndClose(snapshot, description()).awaitSnapshotTask()
                            }
                            // commitAndClose only queues upload. Never call it a cloud backup until confirmed.
                            stage = "confirm_upload"
                            val confirmed = confirmUploaded(bundle)
                            diagnostic(stage, if (confirmed) "server_confirmed" else "pending")
                            return@withTimeoutOrNull CloudWriteResult(current, confirmed)
                        }
                    }
                    throw IOException("Saved Games conflicts changed; retry")
                } ?: run {
                    diagnostic(stage, "timeout")
                    throw IOException("Saved Games timed out")
                }
            } catch (cancelled: CancellationException) {
                diagnostic(stage, "lifecycle_cancelled")
                throw cancelled
            } catch (error: ApiException) {
                diagnostic(stage, "api_failure", error.statusCode)
                throw error
            } catch (error: Exception) {
                diagnostic(stage, when (error) {
                    is CloudAccountChanged -> "account_changed"
                    is CloudChoiceRequired, is CloudImportConflict -> "choice_required"
                    is IllegalArgumentException -> "invalid_data"
                    else -> "failure"
                })
                throw error
            } finally {
                withContext(NonCancellable) {
                    val discards = opened.mapNotNull { snapshot -> runCatching {
                        if (snapshot.snapshotContents.isClosed) null else client.discardAndClose(snapshot)
                    }.getOrNull() }
                    runCatching { withTimeout(5_000L) { discards.forEach { runCatching { it.awaitSnapshotTask() } } } }
                    opened.clear()
                }
            }
        }

    private suspend fun readProgress(snapshot: Snapshot): CloudProgress? {
        checkSnapshotAccount(snapshot)
        val manifest = readBytes(snapshot)
        if (manifest.isEmpty()) return null
        val names = CloudCodec.chunkNames(manifest, accountPlayerId)
        val chunks = names.associateWith { name ->
            checkAccount()
            val result = client.open(name, false, SnapshotsClient.RESOLUTION_POLICY_MANUAL).awaitSnapshotTask()
            if (result.isConflict) {
                val conflict = requireNotNull(result.conflict)
                val first = track(conflict.snapshot); val second = track(conflict.conflictingSnapshot)
                val a = readBytes(first); val b = readBytes(second)
                require(a.contentEquals(b)) { "Immutable cloud chunk conflict" }
                a
            } else readBytes(track(requireNotNull(result.data)))
        }
        return withContext(Dispatchers.Default) { CloudCodec.decode(manifest, chunks, accountPlayerId) }
    }

    private suspend fun writeChunks(bundle: CloudSaveBundle, maxFileSize: Int) {
        bundle.chunks.forEach { (name, bytes) ->
            require(bytes.size <= maxFileSize)
            checkAccount()
            var result = client.open(name, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL).awaitSnapshotTask()
            var completed = false
            repeat(3) {
                if (!completed) {
                    checkAccount()
                    if (result.isConflict) {
                        val conflict = requireNotNull(result.conflict)
                        val first = track(conflict.snapshot); val second = track(conflict.conflictingSnapshot)
                        for (candidate in listOf(first, second)) {
                            val existing = readBytes(candidate)
                            require(existing.isEmpty() || existing.contentEquals(bytes)) { "Immutable chunk content changed" }
                        }
                        check(withContext(Dispatchers.IO) { conflict.resolutionSnapshotContents.writeBytes(bytes) }) { "Chunk write failed" }
                        result = client.resolveConflict(conflict.conflictId, first.metadata.snapshotId,
                            description(), conflict.resolutionSnapshotContents).awaitSnapshotTask()
                    } else {
                        val snapshot = track(requireNotNull(result.data))
                        val existing = readBytes(snapshot)
                        require(existing.isEmpty() || existing.contentEquals(bytes)) { "Immutable chunk content changed" }
                        if (existing.isEmpty()) {
                            check(withContext(Dispatchers.IO) { snapshot.snapshotContents.writeBytes(bytes) }) { "Chunk write failed" }
                            checkAccount()
                            client.commitAndClose(snapshot, description()).awaitSnapshotTask()
                        } else client.discardAndClose(snapshot).awaitSnapshotTask()
                        completed = true
                    }
                }
            }
            if (!completed) throw IOException("Chunk conflict changed; retry")
        }
    }

    private suspend fun confirmUploaded(bundle: CloudSaveBundle, includeRoot: Boolean = true): Boolean {
        checkAccount()
        val required = bundle.chunks.keys + if (includeRoot) setOf(CloudCodec.ROOT_NAME) else emptySet()
        if (required.isEmpty()) return true
        val loaded = client.load(true).awaitSnapshotTask()
        val metadata = loaded.get() ?: return false
        try {
            if (loaded.isStale) return false
            val entries = (0 until metadata.count).map { metadata.get(it).freeze() }
                .filter { it.uniqueName in required }.associateBy { it.uniqueName }
            if (entries.keys != required || entries.values.any { it.hasChangePending() }) return false
            for ((name, entry) in entries) {
                checkMetadataAccount(entry)
                val result = client.open(entry, SnapshotsClient.RESOLUTION_POLICY_MANUAL).awaitSnapshotTask()
                if (result.isConflict) {
                    result.conflict?.let { track(it.snapshot); track(it.conflictingSnapshot) }
                    return false
                }
                val snapshot = track(requireNotNull(result.data))
                val expected = if (name == CloudCodec.ROOT_NAME) bundle.manifest else requireNotNull(bundle.chunks[name])
                if (!readBytes(snapshot).contentEquals(expected) || snapshot.metadata.hasChangePending()) return false
                client.discardAndClose(snapshot).awaitSnapshotTask()
            }
            checkAccount()
            return true
        } finally { metadata.release() }
    }

    private fun track(snapshot: Snapshot): Snapshot = snapshot.also { opened += it }

    private suspend fun readBytes(snapshot: Snapshot): ByteArray {
        checkSnapshotAccount(snapshot)
        return withContext(Dispatchers.IO) { snapshot.snapshotContents.readFully() }.also { require(it.size <= CloudCodec.MAX_FILE_BYTES) { "Snapshot too large" } }
    }

    private suspend fun checkSnapshotAccount(snapshot: Snapshot) {
        checkAccount()
        checkMetadataAccount(snapshot.metadata)
    }

    private fun checkMetadataAccount(metadata: SnapshotMetadata) {
        if (metadata.owner.playerId != accountPlayerId) throw CloudAccountChanged()
    }

    private suspend fun checkAccount() {
        if (!isCurrent()) throw CloudAccountChanged()
        val player = PlayGames.getPlayersClient(activity).currentPlayer.awaitSnapshotTask()
        if (!isCurrent() || player.playerId != accountPlayerId) throw CloudAccountChanged()
    }

    private fun diagnostic(stage: String, result: String, statusCode: Int? = null) {
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            Log.d("NumbCrunchCloud", "stage=$stage result=$result status=$statusCode")
    }

    private fun description() = SnapshotMetadataChange.Builder().setDescription("NumbCrunch player progress").build()
}

private suspend fun <T> Task<T>.awaitSnapshotTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) when {
            task.isCanceled -> continuation.resumeWithException(CancellationException("Saved Games request cancelled"))
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IOException("Saved Games request failed"))
        }
    }
}
