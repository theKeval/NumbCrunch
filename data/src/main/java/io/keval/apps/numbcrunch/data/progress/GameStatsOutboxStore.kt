package io.keval.apps.numbcrunch.data.progress

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.gamestats.*
import io.keval.apps.numbcrunch.domain.progress.*

/** Owned by the progress database transaction. Never called from cloud import or historical scans. */
object GameStatsOutboxStore {
    fun createSchema(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS game_stats_outbox (
            round_id TEXT NOT NULL, event_name TEXT NOT NULL, account_player_id TEXT NOT NULL,
            player_mode TEXT NOT NULL, difficulty TEXT NOT NULL, outcome TEXT NOT NULL,
            guess_count INTEGER NOT NULL, hint_used INTEGER NOT NULL, state TEXT NOT NULL,
            PRIMARY KEY(round_id, event_name))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS game_stats_pending ON game_stats_outbox(account_player_id, state)")
    }

    /** accountPlayerIdAtOccurrence is null for guests, imports, and rounds owned by another installation. */
    fun onLocalTerminal(db: SQLiteDatabase, round: RoundRecord, accountPlayerIdAtOccurrence: String?) {
        check(db.inTransaction()) { "Round and event must commit atomically" }
        if (accountPlayerIdAtOccurrence.isNullOrBlank()) return
        val event = RoundCompletedEvent.fromLocalRound(round) ?: return
        db.insertWithOnConflict("game_stats_outbox", null, ContentValues().apply {
            put("round_id", round.id); put("event_name", RoundCompletedEvent.NAME)
            put("account_player_id", accountPlayerIdAtOccurrence)
            put("player_mode", event.playerMode.name); put("difficulty", event.difficulty.name)
            put("outcome", event.outcome.name); put("guess_count", event.guessCount)
            put("hint_used", if (event.hintUsed) 1 else 0); put("state", GameStatsDeliveryState.READY.name)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun pending(db: SQLiteDatabase, accountPlayerId: String): List<GameStatsDelivery> =
        db.query("game_stats_outbox", null, "account_player_id = ? AND event_name = ? AND state = ?",
            arrayOf(accountPlayerId, RoundCompletedEvent.NAME, GameStatsDeliveryState.READY.name),
            null, null, "rowid", "100").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.delivery()) } }

    fun claim(db: SQLiteDatabase, roundId: String, accountPlayerId: String): GameStatsDelivery? {
        check(db.inTransaction()) { "Claim must be atomic" }
        val changed = db.update("game_stats_outbox", ContentValues().apply { put("state", GameStatsDeliveryState.HANDOFF.name) },
            "round_id = ? AND event_name = ? AND account_player_id = ? AND state = ?",
            arrayOf(roundId, RoundCompletedEvent.NAME, accountPlayerId, GameStatsDeliveryState.READY.name))
        if (changed == 0) return null
        return db.query("game_stats_outbox", null, "round_id = ? AND event_name = ?",
            arrayOf(roundId, RoundCompletedEvent.NAME), null, null, null).use { check(it.moveToFirst()); it.delivery() }
    }

    fun finish(db: SQLiteDatabase, roundId: String, accountPlayerId: String, state: GameStatsDeliveryState) {
        check(db.inTransaction()) { "Delivery outcome must commit atomically" }
        require(state in setOf(GameStatsDeliveryState.BUFFERED, GameStatsDeliveryState.UNKNOWN, GameStatsDeliveryState.REJECTED))
        db.update("game_stats_outbox", ContentValues().apply { put("state", state.name) },
            "round_id = ? AND event_name = ? AND account_player_id = ? AND state = ?",
            arrayOf(roundId, RoundCompletedEvent.NAME, accountPlayerId, GameStatsDeliveryState.HANDOFF.name))
    }

    private fun Cursor.delivery(): GameStatsDelivery {
        fun text(name: String) = getString(getColumnIndexOrThrow(name))
        val event = RoundCompletedEvent(RoundMode.valueOf(text("player_mode")), Difficulty.valueOf(text("difficulty")),
            RoundStatus.valueOf(text("outcome")), getLong(getColumnIndexOrThrow("guess_count")),
            getInt(getColumnIndexOrThrow("hint_used")) != 0).also(RoundCompletedEvent::validate)
        return GameStatsDelivery(text("round_id"), text("account_player_id"), event)
    }
}
