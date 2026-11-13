package io.keval.apps.numbcrunch.data.progress

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.cloud.*
import io.keval.apps.numbcrunch.domain.engagement.TutorialCompletion
import io.keval.apps.numbcrunch.domain.gamestats.*
import io.keval.apps.numbcrunch.domain.progress.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One process-owned repository. SQLite commits each game update and its outcome together. */
class SqliteProgressRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
    private val now: () -> Long = System::currentTimeMillis,
) : ProgressRepository, CloudSyncRepository, GameStatsOutbox {
    private val helper = ProgressDatabase(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private val changes = MutableStateFlow(0L)

    override suspend fun guestProfile(): PlayerProfile = transaction { db ->
        guest(db)
    }

    override suspend fun profileForPlayer(pgsPlayerId: String): PlayerProfile {
        require(pgsPlayerId.isNotBlank()) { "Missing platform association" }
        return transaction { db -> profile(db, pgsPlayerId) ?: newProfile(db, pgsPlayerId) }
    }

    override suspend fun activatePlayerProfile(pgsPlayerId: String): PlayerProfile = transaction { db ->
        require(pgsPlayerId.isNotBlank()) { "Missing platform association" }
        val account = profile(db, pgsPlayerId) ?: newProfile(db, pgsPlayerId)
        if (metadata(db, "last_connected_profile") != account.id) {
            setMetadata(db, "selected_profile", account.id)
            setMetadata(db, "last_connected_profile", account.id)
        }
        account
    }

    override suspend fun selectedProfile(): PlayerProfile = transaction { db -> selected(db) }

    override suspend fun selectProfile(profileId: String): PlayerProfile = transaction { db ->
        val chosen = profileById(db, profileId)
        require(!CloudProgressStore.isRemoteNamespace(db, profileId)) { "Select the local account profile" }
        require(claimForGuest(db, profileId) == null) { "Guest progress already belongs to an account" }
        setMetadata(db, "selected_profile", chosen.id)
        chosen
    }

    override fun observeSelectedProfile(): Flow<PlayerProfile> = changes.map { read { db -> selected(db) } }
        .distinctUntilChanged()

    override suspend fun claimGuestProgress(guestProfileId: String, accountProfileId: String): GuestProgressClaim = transaction { db ->
        val existing = claimForGuest(db, guestProfileId)
        if (existing != null) {
            require(existing.accountProfileId == accountProfileId) { "Guest progress already claimed" }
            return@transaction existing
        }
        require(profileById(db, guestProfileId).pgsPlayerId == null && !CloudProgressStore.isRemoteNamespace(db, guestProfileId)) {
            "Expected unclaimed local guest profile"
        }
        require(profileById(db, accountProfileId).pgsPlayerId != null) { "Expected Play Games profile" }
        require(rounds(db, guestProfileId).isNotEmpty() || CloudProgressStore.outcomes(db, listOf(guestProfileId)).isNotEmpty() ||
            CloudProgressStore.tutorials(db, listOf(guestProfileId)).isNotEmpty()) { "No guest progress to import" }
        val claim = GuestProgressClaim(guestProfileId, accountProfileId, now())
        db.insertOrThrow("guest_claims", null, ContentValues().apply {
            put("guest_profile_id", claim.guestProfileId)
            put("account_profile_id", claim.accountProfileId)
            put("claimed_at", claim.claimedAt)
        })
        if (metadata(db, "selected_profile") == guestProfileId) setMetadata(db, "selected_profile", accountProfileId)
        claim
    }

    override fun observeProgress(ownerId: String): Flow<ProgressSnapshot> = changes.map {
        read { db -> snapshot(db, ownerId) }
    }.distinctUntilChanged()

    override suspend fun recordTutorialCompletion(completion: TutorialCompletion) {
        transaction { db -> CloudProgressStore.recordTutorial(db, completion) }
    }

    override fun observeRounds(ownerId: String): Flow<List<RoundRecord>> = changes.map {
        read { rounds(it, ownerId) }
    }

    override suspend fun findRound(ownerId: String, roundId: String): RoundRecord? = read { db ->
        find(db, roundId)?.takeIf { it.ownerId in ownerIds(db, ownerId) }
    }

    override suspend fun unfinishedRound(ownerId: String): RoundRecord? = read { db ->
        unfinished(db, ownerId).let { rounds ->
            if (rounds.size > 1) throw UnfinishedRoundException()
            rounds.singleOrNull()
        }
    }

    override suspend fun unfinishedRounds(ownerId: String): List<RoundRecord> = read { db ->
        val preferred = metadata(db, "cloud_selected_round:" + ownerId)
        unfinished(db, ownerId).sortedBy { it.id != preferred }
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
            require(!CloudProgressStore.hasOutcome(db, round.id)) { "Completed round identity already exists" }
            require(!CloudProgressStore.isRemoteNamespace(db, round.ownerId)) { "Start rounds in the local account profile" }
            require(claimForGuest(db, round.ownerId) == null) { "Start new rounds in the account profile" }
            if (unfinished(db, round.ownerId).isNotEmpty()) throw UnfinishedRoundException()
            insert(db, round)
            round
        }
    }

    override suspend fun updateRound(round: RoundRecord, expected: RoundRecord): RoundRecord = transaction { db ->
        val previous = find(db, round.id) ?: throw RoundConflictException()
        if (previous == round) return@transaction previous // Retry after a committed response was lost.
        if (previous != expected) throw RoundConflictException()
        previous.validateSuccessor(round)
        db.update("rounds", values(round), "id = ? AND revision = ?", arrayOf(round.id, expected.revision.toString()))
            .also { if (it != 1) throw RoundConflictException() }
        if (round.status.isTerminal) GameStatsOutboxStore.onLocalTerminal(db, round, localOwnerAccount(db, round.ownerId))
        round
    }

    override suspend fun importRounds(ownerId: String, rounds: List<RoundRecord>) {
        transaction { db ->
            rounds.forEach { round ->
                require(round.ownerId == ownerId) { "Wrong progress owner" }
                round.validate()
                val existing = find(db, round.id)
                if (existing == null) {
                    require(!CloudProgressStore.hasOutcome(db, round.id)) { "Completed round identity already exists" }
                    insert(db, round)
                } else if (existing != round) throw RoundConflictException()
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

    override fun observePending(accountPlayerId: String): Flow<List<GameStatsDelivery>> = changes.map {
        read { db -> GameStatsOutboxStore.pending(db, accountPlayerId) }
    }.distinctUntilChanged()

    override suspend fun claim(roundId: String, accountPlayerId: String): GameStatsDelivery? = transaction { db ->
        GameStatsOutboxStore.claim(db, roundId, accountPlayerId)
    }

    override suspend fun finish(roundId: String, accountPlayerId: String, state: GameStatsDeliveryState) {
        transaction { db -> GameStatsOutboxStore.finish(db, roundId, accountPlayerId, state) }
    }

    private fun localOwnerAccount(db: SQLiteDatabase, ownerId: String): String? =
        db.rawQuery("""SELECT pgs_player_id FROM profiles WHERE id = ? AND pgs_player_id IS NOT NULL
            UNION SELECT p.pgs_player_id FROM guest_claims g JOIN profiles p ON p.id = g.account_profile_id
            WHERE g.guest_profile_id = ? AND p.pgs_player_id IS NOT NULL LIMIT 1""", arrayOf(ownerId, ownerId))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    override suspend fun exportProgress(ownerId: String): ProgressSnapshot = read { db -> snapshot(db, ownerId) }

    private fun snapshot(db: SQLiteDatabase, ownerId: String): ProgressSnapshot {
        val owner = profileById(db, ownerId)
        val owners = ownerIds(db, ownerId)
        return ProgressSnapshot(profile = owner, rounds = rounds(db, ownerId), guestClaims = claims(db, ownerId),
            associatedProfiles = CloudProgressStore.associatedProfiles(db, owner),
            outcomes = CloudProgressStore.outcomes(db, owners), tutorialCompletions = CloudProgressStore.tutorials(db, owners))
    }

    override suspend fun exportCloudProgress(accountPlayerId: String): CloudProgress = transaction(notify = false) { db ->
        exportCloud(db, accountPlayerId)
    }

    private fun exportCloud(db: SQLiteDatabase, accountPlayerId: String): CloudProgress {
        val account = profile(db, accountPlayerId) ?: throw IllegalArgumentException("Unknown account profile")
        return CloudProgressStore.project(db, snapshot(db, account.id))
    }

    override suspend fun retainPendingMerge(accountPlayerId: String, progress: CloudProgress) {
        transaction(notify = false) { db -> CloudProgressStore.write(db, accountPlayerId, "pending", progress) }
    }

    override suspend fun pendingCloudMerge(accountPlayerId: String): CloudProgress? = read { db ->
        CloudProgressStore.read(db, accountPlayerId, "pending")
    }

    override suspend fun importResolvedProgress(accountPlayerId: String, progress: CloudProgress, selectedRoundId: String?) {
        importAndStamp(accountPlayerId, progress, selectedRoundId)
    }

    override suspend fun importAndStamp(accountPlayerId: String, progress: CloudProgress, selectedRoundId: String?): String =
        transaction { db ->
            val local = exportCloud(db, accountPlayerId)
            val merged = CloudMerge.merge(local, progress, accountPlayerId)
            if (merged.roundConflicts.isNotEmpty() || (merged.unfinishedRoundIds.size > 1 && selectedRoundId !in merged.unfinishedRoundIds))
                throw CloudImportConflict(merged.progress)
            val account = requireNotNull(profile(db, accountPlayerId))
            CloudProgressStore.write(db, accountPlayerId, "recovery", local)
            CloudProgressStore.importProgress(db, account, merged.progress, rounds(db, account.id))
            CloudProgressStore.write(db, accountPlayerId, "state", merged.progress)
            db.delete("cloud_manifests", "account_id = ? AND kind = 'pending'", arrayOf(accountPlayerId))
            CloudProgressStore.pruneChunks(db, accountPlayerId)
            if (selectedRoundId != null) setMetadata(db, "cloud_selected_round:" + account.id, selectedRoundId)
            snapshot(db, account.id).cloudProjectionStamp()
        }

    override suspend fun preferredCloudRound(accountPlayerId: String): String? = read { db ->
        profile(db, accountPlayerId)?.let { metadata(db, "cloud_selected_round:" + it.id) }
    }

    override suspend fun lastConfirmedBackup(accountPlayerId: String): Long? = read { db ->
        metadata(db, "last_cloud_backup:" + accountPlayerId)?.toLongOrNull()
    }

    override suspend fun recordConfirmedBackup(accountPlayerId: String, at: Long) {
        require(at >= 0)
        transaction(notify = false) { db -> setMetadata(db, "last_cloud_backup:" + accountPlayerId, at.toString()) }
    }

    /** Used when an application/test instance is being destroyed, never during an active write. */
    fun close() = helper.close()

    private suspend fun <T> read(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(helper.readableDatabase) }
    }

    private suspend fun <T> transaction(notify: Boolean = true, block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            val result = try {
                block(db).also { db.setTransactionSuccessful() }
            } finally {
                db.endTransaction()
            }
            if (notify) changes.value = changes.value + 1
            result
        }
    }

    private fun insert(db: SQLiteDatabase, round: RoundRecord) {
        if (!round.status.isTerminal && unfinished(db, round.ownerId).any { it.ownerId == round.ownerId }) throw UnfinishedRoundException()
        db.insertOrThrow("rounds", null, values(round))
    }

    private fun find(db: SQLiteDatabase, id: String): RoundRecord? =
        db.query("rounds", null, "id = ?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) it.toRound() else null
        }

    private fun unfinished(db: SQLiteDatabase, ownerId: String): List<RoundRecord> =
        rounds(db, ownerId, unfinishedOnly = true)

    private fun rounds(db: SQLiteDatabase, ownerId: String, unfinishedOnly: Boolean = false): List<RoundRecord> {
        val owners = ownerIds(db, ownerId)
        // API 24 SQLite limits bound parameters; archive namespaces can exceed one statement's limit.
        return owners.chunked(500).flatMap { batch ->
            val selection = "owner_id IN (${batch.joinToString { "?" }})" +
                if (unfinishedOnly) " AND status IN ('ACTIVE', 'PAUSED')" else ""
            db.query("rounds", null, selection, batch.toTypedArray(), null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.toRound()) }
            }
        }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    private fun ownerIds(db: SQLiteDatabase, ownerId: String): List<String> =
        (listOf(ownerId) + CloudProgressStore.associatedProfiles(db, profileById(db, ownerId)).map { it.id } +
            claims(db, ownerId).map { it.guestProfileId }).distinct()

    private fun claims(db: SQLiteDatabase, accountId: String): List<GuestProgressClaim> =
        db.query("guest_claims", null, "account_profile_id = ? OR account_profile_id IN (SELECT profile_id FROM remote_namespaces WHERE account_profile_id = ?)", arrayOf(accountId, accountId), null, null, "guest_profile_id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toClaim()) }
        }

    private fun claimForGuest(db: SQLiteDatabase, guestId: String): GuestProgressClaim? =
        db.query("guest_claims", null, "guest_profile_id = ?", arrayOf(guestId), null, null, null).use {
            if (it.moveToFirst()) it.toClaim() else null
        }

    private fun guest(db: SQLiteDatabase): PlayerProfile =
        db.rawQuery("SELECT * FROM profiles WHERE pgs_player_id IS NULL AND id NOT IN (SELECT guest_profile_id FROM guest_claims) AND id NOT IN (SELECT profile_id FROM remote_namespaces) ORDER BY tracking_started_at, id LIMIT 1", null)
            .use { if (it.moveToFirst()) it.toProfile() else null } ?: newProfile(db, null)

    private fun selected(db: SQLiteDatabase): PlayerProfile {
        val selectedId = metadata(db, "selected_profile")
        if (selectedId != null) return profileById(db, selectedId)
        return guest(db).also { setMetadata(db, "selected_profile", it.id) }
    }

    private fun profileById(db: SQLiteDatabase, id: String): PlayerProfile =
        db.query("profiles", null, "id = ?", arrayOf(id), null, null, null).use {
            require(it.moveToFirst()) { "Unknown progress owner" }
            it.toProfile()
        }

    private fun metadata(db: SQLiteDatabase, key: String): String? =
        db.query("metadata", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun setMetadata(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("metadata", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
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

private class ProgressDatabase(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 4) {
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
        createClaims(db)
        CloudProgressStore.createSchema(db)
        GameStatsOutboxStore.createSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..3 && newVersion == 4) { "Unsupported progress schema migration" }
        if (oldVersion == 1) createClaims(db)
        if (oldVersion < 3) CloudProgressStore.createSchema(db)
        GameStatsOutboxStore.createSchema(db)
    }

    private fun createClaims(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE guest_claims (
            guest_profile_id TEXT PRIMARY KEY NOT NULL REFERENCES profiles(id),
            account_profile_id TEXT NOT NULL REFERENCES profiles(id), claimed_at INTEGER NOT NULL
        )""")
    }
}

internal fun values(round: RoundRecord) = ContentValues().apply {
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

private fun Cursor.toClaim() = GuestProgressClaim(string("guest_profile_id"), string("account_profile_id"), long("claimed_at"))
