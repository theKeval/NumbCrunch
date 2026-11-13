package io.keval.apps.numbcrunch.domain.gamestats

import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import io.keval.apps.numbcrunch.domain.connection.PlayGamesPlayer
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.progress.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GameStatsDeliveryTest {
    private fun win(mode: RoundMode = RoundMode.SINGLE, difficulty: Difficulty = Difficulty.HARD) =
        RoundRecord("r", "owner", mode, difficulty, "012", 1).editInput("012",2).submitGuess(3)
    private fun entry(id: String = "r", account: String = "account") = GameStatsDelivery(id,account,
        requireNotNull(RoundCompletedEvent.fromLocalRound(win())))

    @Test fun `only local started terminal rounds create occurrence events`() {
        assertNotNull(RoundCompletedEvent.fromLocalRound(win()))
        val active=RoundRecord("r","owner",RoundMode.SINGLE,Difficulty.HARD,"012",1)
        assertNull(RoundCompletedEvent.fromLocalRound(active))
        assertNull(RoundCompletedEvent.fromLocalRound(active.pause(2)))
        assertNull(RoundCompletedEvent.fromLocalRound(active.finish(RoundStatus.ABANDONED,2)))
        assertNull(RoundCompletedEvent.fromLocalRound(win().copy(origin=RoundOrigin.LEGACY)))
        val abandoned=active.editInput("098",2).submitGuess(3).finish(RoundStatus.ABANDONED,4)
        assertEquals(RoundStatus.ABANDONED,RoundCompletedEvent.fromLocalRound(abandoned)?.outcome)
        assertThrows(IllegalArgumentException::class.java) { entry().event.copy(guessCount=0).validate() }
    }

    @Test fun `strict derived conjunctions exclude other modes difficulties hints and outcomes`() {
        for (mode in RoundMode.entries) for (difficulty in Difficulty.entries) {
            val event=requireNotNull(RoundCompletedEvent.fromLocalRound(win(mode,difficulty)))
            assertEquals(mode==RoundMode.SINGLE && difficulty==Difficulty.HARD,event.hardSoloWin)
            assertEquals(mode==RoundMode.SINGLE && difficulty==Difficulty.MEDIUM,event.unaidedMediumSoloWin)
        }
        val medium=entry().event.copy(difficulty=Difficulty.MEDIUM,hintUsed=true)
        assertFalse(medium.unaidedMediumSoloWin)
        assertFalse(entry().event.copy(outcome=RoundStatus.GIVEN_UP).hardSoloWin)
    }

    @Test fun `normal callbacks and repeated collectors hand off once`() = runBlocking {
        val e=entry();val box=Box(e);val gateway=Gateway();val delivery=GameStatsDeliveryController(box)
        delivery.deliver(listOf(e,e),"account",gateway)
        delivery.deliver(listOf(e),"account",gateway)
        assertEquals(1,gateway.calls);assertEquals(GameStatsDeliveryState.BUFFERED,box.states[e.roundId])
    }

    @Test fun `offline identity unavailable keeps ready event for same account reconnect`() = runBlocking {
        val e=entry();val box=Box(e);val gateway=Gateway(account=null);val delivery=GameStatsDeliveryController(box)
        delivery.deliver(listOf(e),"account",gateway)
        assertEquals(0,gateway.calls);assertEquals(GameStatsDeliveryState.READY,box.states[e.roundId])
        gateway.account="account";delivery.deliver(listOf(e),"account",gateway)
        assertEquals(1,gateway.calls)
    }

    @Test fun `missing IDs and another profile never claim or transfer a queue`() = runBlocking {
        val e=entry();val box=Box(e);val gateway=Gateway(account="other");val delivery=GameStatsDeliveryController(box)
        delivery.deliver(listOf(e),"",gateway)
        delivery.deliver(listOf(e),"other",gateway)
        delivery.deliver(listOf(e),"account",gateway)
        assertEquals(0,gateway.calls);assertEquals(GameStatsDeliveryState.READY,box.states[e.roundId])
    }

    @Test fun `schema rejection and ambiguous errors never automatically resend`() = runBlocking {
        for (result in listOf(GameStatsRecordResult.REJECTED,GameStatsRecordResult.UNKNOWN)) {
            val e=entry();val box=Box(e);val gateway=Gateway(result=result);val delivery=GameStatsDeliveryController(box)
            delivery.deliver(listOf(e),"account",gateway);delivery.deliver(listOf(e),"account",gateway)
            assertEquals(1,gateway.calls)
            assertEquals(if (result==GameStatsRecordResult.REJECTED) GameStatsDeliveryState.REJECTED else GameStatsDeliveryState.UNKNOWN,box.states[e.roundId])
        }
    }

    @Test fun `process death at handoff leaves tombstone even without completion callback`() = runBlocking {
        val e=entry();val box=Box(e);val gateway=Gateway(fail=CancellationException("process ended"))
        try { GameStatsDeliveryController(box).deliver(listOf(e),"account",gateway);fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(GameStatsDeliveryState.HANDOFF,box.states[e.roundId])
        val restarted=Gateway();GameStatsDeliveryController(box).deliver(listOf(e),"account",restarted)
        assertEquals(0,restarted.calls)
    }

    @Test fun `restored device has no delivery ledger and does not reconstruct events from history`() = runBlocking {
        val original=Box(entry());val restored=Box();val gateway=Gateway()
        GameStatsDeliveryController(restored).deliver(listOf(entry()),"account",gateway)
        assertEquals(0,gateway.calls)
        assertEquals(GameStatsDeliveryState.READY,original.states["r"])
    }

    @Test fun `guest selection while SDK identity is suspended prevents the handoff gate`() = runBlocking {
        val profile=PlayerProfile("local","account",1)
        var session=PlayerSession(status=ConnectionStatus.CONNECTED,selectedProfile=profile,
            player=PlayGamesPlayer("account","Tester"),accountProfile=profile,generation=4)
        val scope=GameStatsScope("account","local",4)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val allowed=async { isCurrentGameStatsAccount("account",{ scope.isCurrent(session) }) {
            entered.complete(Unit);release.await();"account"
        } }
        entered.await()
        session=session.copy(selectedProfile=PlayerProfile("guest",null,1),generation=5)
        release.complete(Unit)
        assertFalse(allowed.await())
        var reads=0
        assertFalse(isCurrentGameStatsAccount("account",{ scope.isCurrent(session) }) { reads++;"account" })
        assertEquals(0,reads)
        assertFalse(scope.isCurrent(session.copy(selectedProfile=profile))) // Same account, newer generation stays stale.
    }

    @Test fun `scope change after identity check leaves ready work for later account reconnect`() = runBlocking {
        val e=entry();val box=Box(e);var current=true
        val gateway=object:GameStatsGateway {
            override suspend fun currentAccountPlayerId():String { current=false;return "account" }
            override suspend fun record(expectedAccountPlayerId:String,event:RoundCompletedEvent):GameStatsRecordResult =
                throw AssertionError("Must not hand off after selection changed")
        }
        GameStatsDeliveryController(box).deliver(listOf(e),"account",gateway) { current }
        assertEquals(GameStatsDeliveryState.READY,box.states[e.roundId])
    }

    @Test fun `scope change during durable claim preserves ambiguous tombstone without SDK call`() = runBlocking {
        val e=entry();val box=Box(e);var current=true
        box.afterClaim={ current=false }
        val gateway=Gateway()
        GameStatsDeliveryController(box).deliver(listOf(e),"account",gateway) { current }
        assertEquals(0,gateway.calls);assertEquals(GameStatsDeliveryState.HANDOFF,box.states[e.roundId])
        current=true
        GameStatsDeliveryController(box).deliver(listOf(e),"account",gateway) { current }
        assertEquals(0,gateway.calls)
    }

    private class Box(vararg entries: GameStatsDelivery):GameStatsOutbox {
        var afterClaim:()->Unit={}
        val entries=entries.associateBy { it.roundId };val states=entries.associate { it.roundId to GameStatsDeliveryState.READY }.toMutableMap()
        override fun observePending(accountPlayerId:String):Flow<List<GameStatsDelivery>> = flowOf(entries.values.filter { it.accountPlayerId==accountPlayerId && states[it.roundId]==GameStatsDeliveryState.READY })
        override suspend fun claim(roundId:String,accountPlayerId:String):GameStatsDelivery? {
            val entry=entries[roundId]?.takeIf { it.accountPlayerId==accountPlayerId && states[roundId]==GameStatsDeliveryState.READY } ?: return null
            states[roundId]=GameStatsDeliveryState.HANDOFF;afterClaim();return entry
        }
        override suspend fun finish(roundId:String,accountPlayerId:String,state:GameStatsDeliveryState) { states[roundId]=state }
    }
    private class Gateway(var account:String?="account",val result:GameStatsRecordResult=GameStatsRecordResult.BUFFERED,val fail:Exception?=null):GameStatsGateway {
        var calls=0
        override suspend fun currentAccountPlayerId()=account
        override suspend fun record(expectedAccountPlayerId:String,event:RoundCompletedEvent):GameStatsRecordResult { calls++;fail?.let { throw it };return result }
    }
}
