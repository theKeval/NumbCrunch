package io.keval.apps.numbcrunch.data.progress

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One process-owned repository. SQLite commits each game update and its outcome together. */
class SqliteProgressRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
    private val now: () -> Long = System::currentTimeMillis,
) : ProgressRepository {
    private val helper = ProgressDatabase(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private val changes = MutableStateFlow(0L)

    override suspend fun guestProfile(): PlayerProfile = transaction { db ->
        profile(db, null) ?: newProfile(db, null)
    }

    override suspend fun profileForPlayer(pgsPlayerId: String): PlayerProfile {
        require(pgsPlayerId.isNotBlank()) { "Missing platform association" }
        return transaction { db -> profile(db, pgsPlayerId) ?: newProfile(db, pgsPlayerId) }
    }

    override fun observeRounds(ownerId: String): Flow<List<RoundRecord>> = changes.map {
        read { rounds(it, ownerId) }
    }

    override suspend fun findRound(ownerId: String, roundId: String): RoundRecord? = read { db ->
        find(db, roundId)?.takeIf { it.ownerId == ownerId }
    }

    override suspend fun unfinishedRound(ownerId: String): RoundRecord? = read { db ->
        unfinished(db, ownerId)
    }

    override suspend fun createRound(round: RoundRecord): RoundRecord = transaction { db ->
        round.validate()
        require(round.revision == 0L && round.status == RoundStatus.ACTIVE && !round.isPlayed) {
            "New round must be unplayed"
        }
        val existing = find(db, round.id)
        if (existing != null) {
            if (existing != round) throw RoundConflictException()
            existing
        } else {
            insert(db, round)
            round
        }
    }

    override suspend fun updateRound(round: RoundRecord, expectedRevision: Long): RoundRecord = transaction { db ->
        val previous = find(db, round.id) ?: throw RoundConflictException()
        if (previous == round) return@transaction previous // Retry after a committed response was lost.
        if (previous.revision != expectedRevision) throw RoundConflictException()
        previous.validateSuccessor(round)
        db.update("rounds", values(round), "id = ? AND revision = ?", arrayOf(round.id, expectedRevision.toString()))
            .also { if (it != 1) throw RoundConflictException() }
        round
    }

    override suspend fun importRounds(ownerId: String, rounds: List<RoundRecord>) {
        transaction { db ->
            rounds.forEach { round ->
                require(round.ownerId == ownerId) { "Wrong progress owner" }
                round.validate()
                val existing = find(db, round.id)
                if (existing == null) insert(db, round)
                else if (existing != round) throw RoundConflictException()
            }
        }
    }

    override suspend fun importLegacyRound(round: RoundRecord): RoundRecord? = transaction { db ->
        require(round.origin == RoundOrigin.LEGACY) { "Expected legacy round" }
        round.validate()
        val consumed = db.rawQuery("SELECT value FROM metadata WHERE key = 'legacy_import'", null).use { it.moveToFirst() }
        if (consumed) return@transaction null
        // If durable progress already exists, never overwrite it with Android's older saved state.
        if (rounds(db, round.ownerId).isNotEmpty()) return@transaction null
        insert(db, round)
        db.execSQL("INSERT INTO metadata(key, value) VALUES ('legacy_import', '1')")
        round
    }

    override suspend fun exportProgress(ownerId: String): ProgressSnapshot = read { db ->
        val owner = db.query("profiles", null, "id = ?", arrayOf(ownerId), null, null, null).use {
            require(it.moveToFirst()) { "Unknown progress owner" }
            it.toProfile()
        }
        ProgressSnapshot(profile = owner, rounds = rounds(db, ownerId))
    }

    /** Used when an application/test instance is being destroyed, never during an active write. */
    fun close() = helper.close()

    private suspend fun <T> read(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(helper.readableDatabase) }
    }

    private suspend fun <T> transaction(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            val result = try {
                block(db).also { db.setTransactionSuccessful() }
            } finally {
                db.endTransaction()
            }
            changes.value = changes.value + 1
            result
        }
    }

    private fun insert(db: SQLiteDatabase, round: RoundRecord) {
        if (!round.status.isTerminal && unfinished(db, round.ownerId) != null) throw UnfinishedRoundException()
        db.insertOrThrow("rounds", null, values(round))
    }

    private fun find(db: SQLiteDatabase, id: String): RoundRecord? =
        db.query("rounds", null, "id = ?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) it.toRound() else null
        }

    private fun unfinished(db: SQLiteDatabase, ownerId: String): RoundRecord? =
        db.query("rounds", null, "owner_id = ? AND status IN ('ACTIVE', 'PAUSED')", arrayOf(ownerId), null, null, null).use {
            if (it.moveToFirst()) it.toRound() else null
        }

    private fun rounds(db: SQLiteDatabase, ownerId: String): List<RoundRecord> =
        db.query("rounds", null, "owner_id = ?", arrayOf(ownerId), null, null, "created_at, id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toRound()) }
        }

    private fun profile(db: SQLiteDatabase, association: String?): PlayerProfile? =
        db.query("profiles", null, if (association == null) "pgs_player_id IS NULL" else "pgs_player_id = ?",
            association?.let { arrayOf(it) }, null, null, null).use { if (it.moveToFirst()) it.toProfile() else null }

    private fun newProfile(db: SQLiteDatabase, association: String?): PlayerProfile {
        val profile = PlayerProfile(UUID.randomUUID().toString(), association, now())
        db.insertOrThrow("profiles", null, ContentValues().apply {
            put("id", profile.id)
            put("pgs_player_id", profile.pgsPlayerId)
            put("tracking_started_at", profile.trackingStartedAt)
        })
        return profile
    }

    companion object { const val DATABASE_NAME = "player_progress.db" }
}

private class ProgressDatabase(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles (id TEXT PRIMARY KEY NOT NULL, pgs_player_id TEXT UNIQUE, tracking_started_at INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE rounds (
            id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL REFERENCES profiles(id),
            mode TEXT NOT NULL, difficulty TEXT NOT NULL, secret TEXT NOT NULL,
            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, rules_version INTEGER NOT NULL,
            origin TEXT NOT NULL, revision INTEGER NOT NULL, status TEXT NOT NULL,
            guesses TEXT NOT NULL, current_input TEXT NOT NULL, hinted_guess_index INTEGER,
            started_at INTEGER, completed_at INTEGER
        )""")
        db.execSQL("CREATE INDEX rounds_by_owner ON rounds(owner_id, created_at, id)")
        db.execSQL("CREATE UNIQUE INDEX unfinished_by_owner ON rounds(owner_id) WHERE status IN ('ACTIVE', 'PAUSED')")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Fail closed until a tested, data-preserving migration is supplied for the next schema.
        error("Unsupported progress schema migration: $oldVersion to $newVersion")
    }
}

private fun values(round: RoundRecord) = ContentValues().apply {
    put("id", round.id); put("owner_id", round.ownerId); put("mode", round.mode.name)
    put("difficulty", round.difficulty.name); put("secret", round.secret)
    put("created_at", round.createdAt); put("updated_at", round.updatedAt); put("rules_version", round.rulesVersion)
    put("origin", round.origin.name); put("revision", round.revision); put("status", round.status.name)
    put("guesses", round.guesses.joinToString(",")); put("current_input", round.currentInput)
    put("hinted_guess_index", round.hintedGuessIndex); put("started_at", round.startedAt); put("completed_at", round.completedAt)
}

private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name))
private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
private fun Cursor.nullableLong(name: String): Long? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }
private fun Cursor.toProfile() = PlayerProfile(string("id"), string("pgs_player_id"), long("tracking_started_at"))
private fun Cursor.toRound() = RoundRecord(
    id = string("id"), ownerId = string("owner_id"), mode = RoundMode.valueOf(string("mode")),
    difficulty = Difficulty.valueOf(string("difficulty")), secret = string("secret"), createdAt = long("created_at"),
    updatedAt = long("updated_at"), rulesVersion = long("rules_version").toInt(), origin = RoundOrigin.valueOf(string("origin")),
    revision = long("revision"), status = RoundStatus.valueOf(string("status")),
    guesses = string("guesses").takeIf { it.isNotEmpty() }?.split(',') ?: emptyList(),
    currentInput = string("current_input"), hintedGuessIndex = nullableLong("hinted_guess_index")?.toInt(),
    startedAt = nullableLong("started_at"), completedAt = nullableLong("completed_at"),
).also { it.validate() }
