package io.keval.apps.numbcrunch.domain.cloud

import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

data class CloudSaveBundle(val manifest: ByteArray, val chunks: Map<String, ByteArray>)

/** Bounded, deterministic format. Files contain no SDK delivery ledgers, commands, or device IDs. */
object CloudCodec {
    const val SCHEMA_VERSION = 1
    const val ROOT_NAME = "numbcrunch_progress_v1"
    const val MAX_FILE_BYTES = 1_000_000
    const val MAX_TOTAL_BYTES = 64_000_000
    const val MAX_CHUNKS = 256
    const val MAX_ANCESTORS = 4096
    private const val MAX_IDENTITIES = 1000
    private const val MAX_GUESSES = 50_000
    private const val MAX_RECORDS_PER_CHUNK = 10_000
    private const val ROOT_MAGIC = 0x4e435031
    private const val CHUNK_MAGIC = 0x4e434331

    fun encode(progress: CloudProgress): CloudSaveBundle {
        progress.validate()
        val chunks = linkedMapOf<String, ByteArray>()
        var archiveBytes = 0L
        var entries = mutableListOf<ByteArray>()
        var size = 12
        fun flush() {
            if (entries.isEmpty()) return
            val bytes = output { writeInt(CHUNK_MAGIC); writeInt(SCHEMA_VERSION); writeInt(entries.size)
                entries.forEach { writeInt(it.size); write(it) } }
            require(bytes.size <= MAX_FILE_BYTES) { "Cloud chunk size exceeded" }
            archiveBytes += bytes.size
            require(archiveBytes <= MAX_TOTAL_BYTES) { "Cloud archive limit reached; local history retained" }
            chunks["nc1_" + digest(bytes)] = bytes
            require(chunks.size <= MAX_CHUNKS) { "Cloud archive limit reached; local history retained" }
            entries = mutableListOf(); size = 12
        }
        // Stable buckets avoid rewriting the entire archive when a random round ID is inserted.
        progress.versions.groupBy { hash(it.roundId.toByteArray(Charsets.UTF_8))[0].toInt().and(31) }.toSortedMap()
            .values.forEach { bucket ->
            bucket.sortedWith(compareBy<CloudRoundVersion> { it.roundId }.thenBy { it.versionId }).forEach { version ->
                val bytes = output { writeVersion(version) }
                require(bytes.size + 16 <= MAX_FILE_BYTES) { "Round too large for cloud; local history retained" }
                if (size + 4 + bytes.size > MAX_FILE_BYTES || entries.size >= MAX_RECORDS_PER_CHUNK) flush()
                entries.add(bytes); size += 4 + bytes.size
            }
            flush()
        }
        val body = output {
            writeInt(ROOT_MAGIC); writeInt(SCHEMA_VERSION); string(progress.accountPlayerId)
            collection(progress.profiles.sortedBy { it.id }, MAX_IDENTITIES) { profile ->
                string(profile.id); string(requireNotNull(profile.pgsPlayerId)); writeLong(profile.trackingStartedAt)
            }
            collection(progress.guestClaims.sortedBy { it.guestProfileId }, MAX_IDENTITIES) { claim ->
                string(claim.guestProfileId); string(claim.accountProfileId); writeLong(claim.claimedAt)
            }
            collection(progress.tutorialCompletions.sortedWith(compareBy<TutorialCompletion> { it.ownerId }
                .thenBy { it.tutorialVersion }.thenBy { it.completedAt }), MAX_IDENTITIES) { completion ->
                string(completion.ownerId); writeLong(completion.completedAt); writeInt(completion.tutorialVersion)
            }
            collection(chunks.keys.toList(), MAX_CHUNKS) { string(it) }
        }
        val manifest = body + hash(body)
        require(manifest.size <= MAX_FILE_BYTES && manifest.size.toLong() + chunks.values.sumOf { it.size.toLong() } <= MAX_TOTAL_BYTES) {
            "Cloud archive limit reached; local history retained"
        }
        return CloudSaveBundle(manifest, chunks)
    }

    /** Read this first, then fetch every immutable named chunk before attempting any local import. */
    fun chunkNames(manifest: ByteArray, expectedAccountPlayerId: String): List<String> =
        readManifest(manifest, expectedAccountPlayerId).second

    fun decode(manifest: ByteArray, chunks: Map<String, ByteArray>, expectedAccountPlayerId: String): CloudProgress {
        val (header, names) = readManifest(manifest, expectedAccountPlayerId)
        require(chunks.keys == names.toSet()) { "Cloud archive incomplete or unexpected chunks" }
        require(manifest.size.toLong() + chunks.values.sumOf { it.size.toLong() } <= MAX_TOTAL_BYTES) { "Cloud archive limit exceeded" }
        val versions = names.flatMap { name ->
            val bytes = requireNotNull(chunks[name])
            require(bytes.size <= MAX_FILE_BYTES && name == "nc1_" + digest(bytes)) { "Cloud chunk checksum mismatch" }
            input(bytes) {
                require(readInt() == CHUNK_MAGIC && readInt() == SCHEMA_VERSION) { "Unsupported cloud chunk schema" }
                val records = collection(MAX_RECORDS_PER_CHUNK) {
                    val length = readInt()
                    require(length in 1..MAX_FILE_BYTES && length <= available()) { "Invalid cloud record length" }
                    val record = ByteArray(length); readFully(record)
                    input(record) { readVersion() }
                }
                records
            }
        }
        return header.copy(versions = versions).also { it.validate(expectedAccountPlayerId) }
    }

    internal fun versionId(round: RoundRecord?, outcome: OutcomeFact?, ancestors: Set<String>): String =
        digest(output { writePayload(round, outcome); collection(ancestors.sorted(), MAX_ANCESTORS) { string(it) } })

    private fun readManifest(bytes: ByteArray, account: String): Pair<CloudProgress, List<String>> {
        require(bytes.size in 40..MAX_FILE_BYTES) { "Invalid cloud manifest size" }
        val body = bytes.copyOfRange(0, bytes.size - 32)
        require(hash(body).contentEquals(bytes.copyOfRange(bytes.size - 32, bytes.size))) { "Cloud manifest checksum mismatch" }
        return input(body) {
            require(readInt() == ROOT_MAGIC && readInt() == SCHEMA_VERSION) { "Unsupported cloud schema" }
            val binding = string()
            require(binding == account && account.isNotBlank()) { "Wrong cloud account" }
            val profiles = collection(MAX_IDENTITIES) { PlayerProfile(string(), string(), readLong()) }
            val claims = collection(MAX_IDENTITIES) { GuestProgressClaim(string(), string(), readLong()) }
            val tutorials = collection(MAX_IDENTITIES) { TutorialCompletion(string(), readLong(), readInt()) }
            val names = collection(MAX_CHUNKS) { string() }
            require(names.distinct().size == names.size && names.all { it.matches(Regex("nc1_[a-f0-9]{64}")) }) { "Invalid chunk references" }
            CloudProgress(binding, profiles, claims, tutorialCompletions = tutorials).also { it.validate(account) } to names
        }
    }

    private fun DataOutputStream.writeVersion(version: CloudRoundVersion) {
        version.validate(); string(version.versionId)
        collection(version.ancestorIds.sorted(), MAX_ANCESTORS) { string(it) }
        writePayload(version.unfinished, version.outcome)
    }

    private fun DataInputStream.readVersion(): CloudRoundVersion {
        val id = string(); val ancestors = collection(MAX_ANCESTORS) { string() }
        require(ancestors.distinct().size == ancestors.size) { "Duplicate ancestor" }
        val version = if (readBoolean()) CloudRoundVersion(id, ancestors.toSet(), unfinished = readRound())
            else CloudRoundVersion(id, ancestors.toSet(), outcome = readOutcome())
        return version.also(CloudRoundVersion::validate)
    }

    private fun DataOutputStream.writePayload(round: RoundRecord?, outcome: OutcomeFact?) {
        require((round == null) != (outcome == null)) { "Expected one cloud payload" }
        writeBoolean(round != null)
        if (round != null) {
            string(round.id); string(round.ownerId); string(round.mode.name); string(round.difficulty.name)
            string(round.secret); writeLong(round.createdAt); writeLong(round.updatedAt)
            writeInt(round.rulesVersion); string(round.origin.name); writeLong(round.revision); string(round.status.name)
            collection(round.guesses, MAX_GUESSES) { string(it) }; string(round.currentInput)
            nullableLong(round.hintedGuessIndex?.toLong()); nullableLong(round.startedAt); nullableLong(round.completedAt)
        } else requireNotNull(outcome).let {
            string(it.id); string(it.ownerId); string(it.mode.name); string(it.difficulty.name); string(it.origin.name)
            writeInt(it.rulesVersion); writeLong(it.revision); string(it.status.name); writeLong(it.guessCount)
            writeBoolean(it.hintUsed); writeLong(it.createdAt); nullableLong(it.startedAt); writeLong(it.completedAt)
        }
    }

    private fun DataInputStream.readRound(): RoundRecord {
        val id = string(); val owner = string(); val mode = RoundMode.valueOf(string()); val difficulty = Difficulty.valueOf(string())
        val secret = string(); val created = readLong(); val updated = readLong(); val rules = readInt()
        val origin = RoundOrigin.valueOf(string()); val revision = readLong(); val status = RoundStatus.valueOf(string())
        val guesses = collection(MAX_GUESSES) { string() }; val currentInput = string(); val hint = nullableLong()
        require(hint == null || hint in -1L..Int.MAX_VALUE.toLong()) { "Invalid hint index" }
        return RoundRecord(id, owner, mode, difficulty, secret, created, updated, rules, origin, revision, status,
            guesses, currentInput, hint?.toInt(), nullableLong(), nullableLong()).also(RoundRecord::validate)
    }

    private fun DataInputStream.readOutcome(): OutcomeFact = OutcomeFact(string(), string(), RoundMode.valueOf(string()),
        Difficulty.valueOf(string()), RoundOrigin.valueOf(string()), readInt(), readLong(), RoundStatus.valueOf(string()),
        readLong(), readBoolean(), readLong(), nullableLong(), readLong()).also(OutcomeFact::validate)

    private fun DataOutputStream.string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1024) { "Cloud string too long" }; writeInt(bytes.size); write(bytes)
    }
    private fun DataInputStream.string(): String {
        val size = readInt(); require(size in 0..1024 && size <= available()) { "Invalid cloud string length" }
        val bytes = ByteArray(size); readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun DataOutputStream.nullableLong(value: Long?) { writeBoolean(value != null); if (value != null) writeLong(value) }
    private fun DataInputStream.nullableLong(): Long? = if (readBoolean()) readLong() else null
    private fun <T> DataOutputStream.collection(values: List<T>, max: Int, writeItem: DataOutputStream.(T) -> Unit) {
        require(values.size <= max) { "Cloud collection limit exceeded" }; writeInt(values.size); values.forEach { writeItem(it) }
    }
    private fun <T> DataInputStream.collection(max: Int, readItem: DataInputStream.() -> T): List<T> {
        val size = readInt(); require(size in 0..max && size <= available()) { "Invalid cloud collection length" }
        return List(size) { readItem() }
    }
    private fun output(write: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { write.invoke(it) }; bytes.toByteArray()
    }
    private fun <T> input(bytes: ByteArray, read: DataInputStream.() -> T): T = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
            read.invoke(stream).also { require(stream.available() == 0) { "Trailing cloud data" } }
        }
    } catch (exception: IOException) { throw IllegalArgumentException("Corrupt cloud payload", exception) }
    private fun hash(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun digest(bytes: ByteArray): String = hash(bytes).joinToString("") { "%02x".format(it) }
}
