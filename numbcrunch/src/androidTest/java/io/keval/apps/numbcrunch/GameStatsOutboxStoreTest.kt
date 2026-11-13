package io.keval.apps.numbcrunch

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.keval.apps.numbcrunch.data.progress.GameStatsOutboxStore
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.gamestats.GameStatsDeliveryState
import io.keval.apps.numbcrunch.domain.progress.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameStatsOutboxStoreTest {
    private fun win(id:String="r")=RoundRecord(id,"local",RoundMode.SINGLE,Difficulty.HARD,"012",1)
        .editInput("012",2).submitGuess(3)
    private fun <T> transaction(db:SQLiteDatabase,block:()->T):T {
        db.beginTransaction()
        return try { block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }

    @Test fun rollbackDoesNotCreateEventAndDuplicateTerminalDoesNotRebindAccount() {
        SQLiteDatabase.create(null).use { db ->
            GameStatsOutboxStore.createSchema(db)
            db.beginTransaction()
            GameStatsOutboxStore.onLocalTerminal(db,win(),"account-a")
            db.endTransaction()
            assertTrue(GameStatsOutboxStore.pending(db,"account-a").isEmpty())
            transaction(db) { GameStatsOutboxStore.onLocalTerminal(db,win(),"account-a") }
            transaction(db) { GameStatsOutboxStore.onLocalTerminal(db,win(),"account-b") }
            assertEquals(1,GameStatsOutboxStore.pending(db,"account-a").size)
            assertTrue(GameStatsOutboxStore.pending(db,"account-b").isEmpty())
        }
    }

    @Test fun claimIsAtomicAndHandoffNeverReturnsToReady() {
        SQLiteDatabase.create(null).use { db ->
            GameStatsOutboxStore.createSchema(db)
            transaction(db) { GameStatsOutboxStore.onLocalTerminal(db,win(),"a") }
            assertNull(transaction(db) { GameStatsOutboxStore.claim(db,"r","b") })
            assertNotNull(transaction(db) { GameStatsOutboxStore.claim(db,"r","a") })
            assertNull(transaction(db) { GameStatsOutboxStore.claim(db,"r","a") })
            GameStatsOutboxStore.createSchema(db) // Simulated initialization does not reset ambiguous handoffs.
            assertTrue(GameStatsOutboxStore.pending(db,"a").isEmpty())
            transaction(db) { GameStatsOutboxStore.finish(db,"r","a",GameStatsDeliveryState.UNKNOWN) }
            assertNull(transaction(db) { GameStatsOutboxStore.claim(db,"r","a") })
        }
    }

    @Test fun guestsLegacyAndNonStartedRoundsNeverEnterQueue() {
        SQLiteDatabase.create(null).use { db ->
            GameStatsOutboxStore.createSchema(db)
            transaction(db) {
                GameStatsOutboxStore.onLocalTerminal(db,win("guest"),null)
                GameStatsOutboxStore.onLocalTerminal(db,win("legacy").copy(origin=RoundOrigin.LEGACY),"a")
                GameStatsOutboxStore.onLocalTerminal(db,RoundRecord("unplayed","local",RoundMode.SINGLE,Difficulty.HARD,"012",1)
                    .finish(RoundStatus.ABANDONED,2),"a")
            }
            assertTrue(GameStatsOutboxStore.pending(db,"a").isEmpty())
        }
    }

    @Test fun sdkBufferedMarkerRemainsBoundAndCannotBeOverwrittenByLateCallback() {
        SQLiteDatabase.create(null).use { db ->
            GameStatsOutboxStore.createSchema(db)
            transaction(db) { GameStatsOutboxStore.onLocalTerminal(db,win(),"a") }
            transaction(db) { GameStatsOutboxStore.claim(db,"r","a") }
            transaction(db) { GameStatsOutboxStore.finish(db,"r","a",GameStatsDeliveryState.BUFFERED) }
            transaction(db) { GameStatsOutboxStore.finish(db,"r","a",GameStatsDeliveryState.UNKNOWN) }
            db.rawQuery("SELECT state FROM game_stats_outbox",null).use {
                assertTrue(it.moveToFirst());assertEquals("BUFFERED",it.getString(0))
            }
            assertTrue(GameStatsOutboxStore.pending(db,"a").isEmpty())
            assertTrue(GameStatsOutboxStore.pending(db,"b").isEmpty())
        }
    }
}
