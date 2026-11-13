package io.keval.apps.numbcrunch.data.progress

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.keval.apps.numbcrunch.domain.cloud.*
import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*

/** Called only inside the progress repository's locked SQLite transaction. */
internal object CloudProgressStore {
    fun createSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE remote_namespaces (profile_id TEXT PRIMARY KEY NOT NULL REFERENCES profiles(id), account_profile_id TEXT NOT NULL REFERENCES profiles(id))")
        db.execSQL("""CREATE TABLE cloud_outcomes (
            id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL REFERENCES profiles(id), mode TEXT NOT NULL,
            difficulty TEXT NOT NULL, origin TEXT NOT NULL, rules_version INTEGER NOT NULL, revision INTEGER NOT NULL,
            status TEXT NOT NULL, guess_count INTEGER NOT NULL, hint_used INTEGER NOT NULL,
            created_at INTEGER NOT NULL, started_at INTEGER, completed_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX outcomes_by_owner ON cloud_outcomes(owner_id)")
        db.execSQL("CREATE TABLE tutorial_completions (owner_id TEXT NOT NULL REFERENCES profiles(id), version INTEGER NOT NULL, completed_at INTEGER NOT NULL, PRIMARY KEY(owner_id, version))")
        db.execSQL("CREATE TABLE cloud_manifests (account_id TEXT NOT NULL, kind TEXT NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(account_id, kind))")
        db.execSQL("CREATE TABLE cloud_chunks (account_id TEXT NOT NULL, name TEXT NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(account_id, name))")
        // Cloud alternatives may share an owner. New local rounds still use the repository's guarded start.
        db.execSQL("DROP INDEX IF EXISTS unfinished_by_owner")
    }

    fun read(db: SQLiteDatabase, account: String, kind: String): CloudProgress? {
        val manifest = db.query("cloud_manifests", arrayOf("payload"), "account_id = ? AND kind = ?", arrayOf(account, kind), null, null, null)
            .use { if (it.moveToFirst()) it.getBlob(0) else null } ?: return null
        val chunks = CloudCodec.chunkNames(manifest, account).associateWith { name ->
            db.query("cloud_chunks", arrayOf("payload"), "account_id = ? AND name = ?", arrayOf(account, name), null, null, null)
                .use { require(it.moveToFirst()) { "Local cloud archive incomplete" }; it.getBlob(0) }
        }
        return CloudCodec.decode(manifest, chunks, account)
    }

    fun write(db: SQLiteDatabase, account: String, kind: String, progress: CloudProgress) {
        progress.validate(account)
        val bundle = CloudCodec.encode(progress)
        bundle.chunks.forEach { (name, payload) ->
            db.insertWithOnConflict("cloud_chunks", null, ContentValues().apply {
                put("account_id", account); put("name", name); put("payload", payload)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        db.insertWithOnConflict("cloud_manifests", null, ContentValues().apply {
            put("account_id", account); put("kind", kind); put("payload", bundle.manifest)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        pruneChunks(db, account)
    }

    /** Only local unreferenced chunks are removed; pending and recovery manifests keep their bytes. */
    fun pruneChunks(db: SQLiteDatabase, account: String) {
        val retained = db.query("cloud_manifests", arrayOf("payload"), "account_id = ?", arrayOf(account), null, null, null).use { cursor ->
            buildSet { while (cursor.moveToNext()) addAll(CloudCodec.chunkNames(cursor.getBlob(0), account)) }
        }
        val obsolete = db.query("cloud_chunks", arrayOf("name"), "account_id = ?", arrayOf(account), null, null, null).use { cursor ->
            buildList { while (cursor.moveToNext()) if (cursor.getString(0) !in retained) add(cursor.getString(0)) }
        }
        obsolete.forEach { db.delete("cloud_chunks", "account_id = ? AND name = ?", arrayOf(account, it)) }
    }

    fun project(db: SQLiteDatabase, snapshot: ProgressSnapshot): CloudProgress {
        val account = requireNotNull(snapshot.profile.pgsPlayerId)
        val projected = CloudProgress.fromSnapshot(snapshot, snapshot.tutorialCompletions)
        val previous = read(db, account, "state")
        val versions = projected.versions.map { candidate ->
            val old = previous?.versions?.singleOrNull { it.roundId == candidate.roundId }
            when {
                old == null -> candidate
                old.unfinished == candidate.unfinished && old.outcome == candidate.outcome -> old
                old.unfinished != null -> old.advance(requireNotNull(snapshot.rounds.singleOrNull { it.id == candidate.roundId }))
                else -> error("Completed local cloud receipt changed")
            }
        }
        require(previous == null || previous.versions.all { old -> versions.any { it.roundId == old.roundId } }) {
            "Local cloud history missing; recovery required"
        }
        return projected.copy(versions = versions).also { write(db, account, "state", it) }
    }

    fun associatedProfiles(db: SQLiteDatabase, canonical: PlayerProfile): List<PlayerProfile> =
        db.rawQuery("SELECT p.* FROM profiles p JOIN remote_namespaces n ON n.profile_id = p.id WHERE n.account_profile_id = ? ORDER BY p.id", arrayOf(canonical.id)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(PlayerProfile(cursor.string("id"), canonical.pgsPlayerId, cursor.long("tracking_started_at"))) }
        }

    fun isRemoteNamespace(db: SQLiteDatabase, id: String): Boolean =
        db.rawQuery("SELECT 1 FROM remote_namespaces WHERE profile_id = ?", arrayOf(id)).use { it.moveToFirst() }

    fun outcomes(db: SQLiteDatabase, owners: List<String>): List<OutcomeFact> =
        owners.chunked(500).flatMap { batch ->
            db.query("cloud_outcomes", null, "owner_id IN (${batch.joinToString { "?" }})", batch.toTypedArray(), null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.outcome()) }
            }
        }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    fun hasOutcome(db: SQLiteDatabase, id: String): Boolean =
        db.rawQuery("SELECT 1 FROM cloud_outcomes WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    fun tutorials(db: SQLiteDatabase, owners: List<String>): List<TutorialCompletion> =
        owners.chunked(500).flatMap { batch ->
            db.query("tutorial_completions", null, "owner_id IN (${batch.joinToString { "?" }})", batch.toTypedArray(), null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(TutorialCompletion(cursor.string("owner_id"), cursor.long("completed_at"), cursor.long("version").toInt())) }
            }
        }.sortedWith(compareBy({ it.ownerId }, { it.tutorialVersion }))

    fun recordTutorial(db: SQLiteDatabase, completion: TutorialCompletion) {
        completion.validate()
        db.insertWithOnConflict("tutorial_completions", null, ContentValues().apply {
            put("owner_id", completion.ownerId); put("version", completion.tutorialVersion); put("completed_at", completion.completedAt)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL("UPDATE tutorial_completions SET completed_at = MIN(completed_at, ?) WHERE owner_id = ? AND version = ?",
            arrayOf<Any>(completion.completedAt, completion.ownerId, completion.tutorialVersion))
    }

    fun importProgress(db: SQLiteDatabase, canonical: PlayerProfile, progress: CloudProgress, localRounds: List<RoundRecord>) {
        progress.validate(requireNotNull(canonical.pgsPlayerId))
        progress.profiles.forEach { remote ->
            val existing = db.query("profiles", null, "id = ?", arrayOf(remote.id), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) PlayerProfile(cursor.string("id"), cursor.getString(cursor.getColumnIndexOrThrow("pgs_player_id")), cursor.long("tracking_started_at")) else null
            }
            if (remote.id == canonical.id) require(remote == canonical) { "Local profile identity changed" }
            else {
                require(existing == null || (existing.pgsPlayerId == null && existing.trackingStartedAt == remote.trackingStartedAt && isRemoteNamespace(db, remote.id))) { "Namespace belongs to another player" }
                if (existing == null) insertProfile(db, remote.copy(pgsPlayerId = null))
                val binding = db.rawQuery("SELECT account_profile_id FROM remote_namespaces WHERE profile_id = ?", arrayOf(remote.id)).use { if (it.moveToFirst()) it.getString(0) else null }
                require(binding == null || binding == canonical.id) { "Remote namespace already bound" }
                db.insertWithOnConflict("remote_namespaces", null, ContentValues().apply {
                    put("profile_id", remote.id); put("account_profile_id", canonical.id)
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
        progress.guestClaims.forEach { claim ->
            val guestExists = db.rawQuery("SELECT pgs_player_id FROM profiles WHERE id = ?", arrayOf(claim.guestProfileId)).use {
                if (it.moveToFirst()) { require(it.isNull(0)) { "Claim refers to an account profile" }; true } else false
            }
            require(!isRemoteNamespace(db, claim.guestProfileId)) { "Claim refers to remote account namespace" }
            val existing = db.rawQuery("SELECT account_profile_id FROM guest_claims WHERE guest_profile_id = ?", arrayOf(claim.guestProfileId)).use { if (it.moveToFirst()) it.getString(0) else null }
            require(existing == null || existing == claim.accountProfileId) { "Guest progress already belongs to another account" }
            require(!guestExists || existing != null) { "Local guest progress requires explicit consent" }
            if (!guestExists) insertProfile(db, PlayerProfile(claim.guestProfileId, null, claim.claimedAt))
            db.insertWithOnConflict("guest_claims", null, ContentValues().apply {
                put("guest_profile_id", claim.guestProfileId); put("account_profile_id", claim.accountProfileId); put("claimed_at", claim.claimedAt)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        progress.versions.forEach { version ->
            for (table in listOf("rounds", "cloud_outcomes")) {
                db.query(table, arrayOf("owner_id"), "id = ?", arrayOf(version.roundId), null, null, null).use {
                    require(!it.moveToFirst() || it.getString(0) == version.ownerId) { "Round identity belongs to another namespace" }
                }
            }
            val previous = localRounds.singleOrNull { it.id == version.roundId }
            val terminal = version.outcome
            if (terminal != null) {
                // Keep matching locally earned outcomes for their local-only delivery ledger; imported receipts never enqueue actions.
                if (previous != null && (!previous.status.isTerminal || OutcomeFact.fromRound(previous) != terminal))
                    db.delete("rounds", "id = ?", arrayOf(terminal.id))
                db.insertWithOnConflict("cloud_outcomes", null, outcomeValues(terminal), SQLiteDatabase.CONFLICT_REPLACE)
            } else {
                require(!hasOutcome(db, version.roundId)) { "A completed round cannot resume" }
                val round = requireNotNull(version.unfinished)
                if (previous == null) db.insertOrThrow("rounds", null, values(round))
                else db.update("rounds", values(round), "id = ?", arrayOf(round.id))
            }
        }
        progress.tutorialCompletions.forEach { recordTutorial(db, it) }
    }

    private fun insertProfile(db: SQLiteDatabase, profile: PlayerProfile) {
        db.insertOrThrow("profiles", null, ContentValues().apply {
            put("id", profile.id); put("pgs_player_id", profile.pgsPlayerId); put("tracking_started_at", profile.trackingStartedAt)
        })
    }
}

private fun outcomeValues(fact: OutcomeFact) = ContentValues().apply {
    put("id", fact.id); put("owner_id", fact.ownerId); put("mode", fact.mode.name); put("difficulty", fact.difficulty.name)
    put("origin", fact.origin.name); put("rules_version", fact.rulesVersion); put("revision", fact.revision)
    put("status", fact.status.name); put("guess_count", fact.guessCount); put("hint_used", if (fact.hintUsed) 1 else 0)
    put("created_at", fact.createdAt); put("started_at", fact.startedAt); put("completed_at", fact.completedAt)
}
private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name))
private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
private fun Cursor.outcome() = OutcomeFact(string("id"), string("owner_id"), RoundMode.valueOf(string("mode")),
    Difficulty.valueOf(string("difficulty")), RoundOrigin.valueOf(string("origin")), long("rules_version").toInt(),
    long("revision"), RoundStatus.valueOf(string("status")), long("guess_count"), long("hint_used") != 0L,
    long("created_at"), getColumnIndexOrThrow("started_at").let { if (isNull(it)) null else getLong(it) }, long("completed_at"))
    .also(OutcomeFact::validate)
