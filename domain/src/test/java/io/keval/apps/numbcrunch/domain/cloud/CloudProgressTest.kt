package io.keval.apps.numbcrunch.domain.cloud

import io.keval.apps.numbcrunch.domain.engagement.*
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class CloudProgressTest {
    private val profile = PlayerProfile("local-a", "pgs-a", 1)
    private fun round(id: String = "r", owner: String = profile.id, difficulty: Difficulty = Difficulty.HARD) =
        RoundRecord(id, owner, RoundMode.SINGLE, difficulty, "012", 1)
    private fun win(id: String = "r", owner: String = profile.id, guesses: Int = 1, at: Long = 10): RoundRecord {
        var value = round(id, owner)
        repeat(guesses - 1) { value = value.editInput("098", 2).submitGuess(3) }
        return value.editInput("012", at).submitGuess(at)
    }
    private fun state(vararg versions: CloudRoundVersion) = CloudProgress("pgs-a", listOf(profile), versions = versions.toList())
    private fun merge(a: CloudProgress, b: CloudProgress) = CloudMerge.merge(a, b, "pgs-a")
    private fun outcomes(progress: CloudProgress) = progress.versions.mapNotNull { it.outcome }

    @Test fun `independent offline outcomes union idempotently commutatively and associatively`() {
        val a = state(CloudRoundVersion.initial(win("a", guesses = 3)))
        val b = state(CloudRoundVersion.initial(win("b", guesses = 7)))
        val c = state(CloudRoundVersion.initial(round("c").editInput("098", 4).submitGuess(5).finish(RoundStatus.ABANDONED, 20)))
        val ab = merge(a,b).progress
        assertEquals(ab, merge(b,a).progress)
        assertEquals(ab, merge(ab,b).progress)
        assertEquals(merge(ab,c).progress, merge(a,merge(b,c).progress).progress)
        val stats = PlayerStatistics.fromFacts(outcomes(merge(ab,c).progress))
        assertEquals(3L, stats.played); assertEquals(2L, stats.wins); assertEquals(1L, stats.abandoned)
        assertEquals(10L, stats.winningGuessSum); assertEquals(5.0, requireNotNull(stats.averageWinningGuesses), 0.0)
        assertEquals(3L, stats.bestWinningGuesses); assertEquals(2L, stats.bestWinStreak); assertEquals(0L, stats.currentWinStreak)
        assertEquals(2L, LifetimeHardWins.scoreFacts(ab.ownerIds, outcomes(ab)))
    }

    @Test fun `normal observed autosave advances without conflict and stale pause cannot resurrect win`() {
        val paused = round().editInput("098", 2).submitGuess(3).pause(4)
        val first = CloudRoundVersion.initial(paused)
        val resumed = paused.resume(5).editInput("012", 6).submitGuess(7)
        val won = first.advance(resumed)
        assertFalse(merge(state(first),state(won)).requiresChoice)
        assertEquals(listOf(won), merge(state(won),state(first)).progress.versions)
        assertThrows(IllegalArgumentException::class.java) { won.advance(resumed.copy(status = RoundStatus.ACTIVE)) }
    }

    @Test fun `unknown ancestry never uses revision or timestamp to silently choose`() {
        val oldPause = CloudRoundVersion.initial(round().pause(2))
        val terminal = CloudRoundVersion.initial(win(at = 5000))
        val conflict = merge(state(oldPause),state(terminal))
        assertEquals(1, conflict.roundConflicts.size)
        assertEquals(2, conflict.progress.versions.size)
        assertThrows(IllegalArgumentException::class.java) { CloudMerge.resolve(conflict.progress,"r",oldPause.versionId) }
        val resolved = CloudMerge.resolve(conflict.progress,"r",terminal.versionId)
        assertFalse(resolved.requiresChoice)
        assertEquals(RoundStatus.WON,resolved.progress.versions.single().outcome?.status)
        assertFalse(merge(resolved.progress,state(oldPause)).requiresChoice)
    }

    @Test fun `same resumed round win versus abandonment is one explicit recoverable choice`() {
        val active = round().editInput("098",2).submitGuess(3)
        val base = CloudRoundVersion.initial(active)
        val win = base.advance(active.editInput("012",4).submitGuess(5))
        val abandon = base.advance(active.finish(RoundStatus.ABANDONED,6))
        val conflict = merge(state(win),state(abandon))
        assertEquals(2,conflict.roundConflicts.single().candidates.size)
        val choice = CloudMerge.resolve(conflict.progress,"r",win.versionId)
        assertEquals(1L,PlayerStatistics.fromFacts(outcomes(choice.progress)).played)
        assertEquals(1L,PlayerStatistics.fromFacts(outcomes(choice.progress)).wins)
        assertEquals(choice.progress,merge(choice.progress,conflict.progress).progress)
    }

    @Test fun `two unfinished round slots survive until a selection without inventing abandonments`() {
        val result = merge(state(CloudRoundVersion.initial(round("a"))),state(CloudRoundVersion.initial(round("b"))))
        assertTrue(result.requiresChoice)
        assertEquals(setOf("a","b"),result.unfinishedRoundIds)
        assertEquals(2,result.progress.versions.size)
        assertTrue(result.roundConflicts.isEmpty())
    }

    @Test fun `different local profile namespaces on same platform account and claimed guest are preserved`() {
        val remoteProfile = PlayerProfile("local-b","pgs-a",20)
        val claim = GuestProgressClaim("guest",profile.id,30)
        val local = state(CloudRoundVersion.initial(win(owner="guest"))).copy(guestClaims=listOf(claim))
        val remote = CloudProgress("pgs-a",listOf(remoteProfile),versions=listOf(CloudRoundVersion.initial(win("remote",owner=remoteProfile.id))))
        val result = merge(local,remote).progress
        assertEquals(setOf("local-a","local-b","guest"),result.ownerIds)
        assertEquals(2L,LifetimeHardWins.scoreFacts(result.ownerIds,outcomes(result)))
        assertEquals(result,merge(result,local).progress)
        assertEquals("guest",outcomes(result).single { it.id=="r" }.ownerId)
    }

    @Test fun `guest-only unclaimed and cross-account data fail before export or merge`() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudProgress.fromSnapshot(ProgressSnapshot(profile=profile.copy(pgsPlayerId=null),rounds=listOf(round())))
        }
        assertThrows(IllegalArgumentException::class.java) { state(CloudRoundVersion.initial(round(owner="guest"))).validate() }
        assertThrows(IllegalArgumentException::class.java) { merge(state(),CloudProgress("pgs-b",listOf(profile.copy(pgsPlayerId="pgs-b")))) }
        val a = state().copy(guestClaims=listOf(GuestProgressClaim("guest",profile.id,10)))
        val other = PlayerProfile("other","pgs-a",1)
        val b = CloudProgress("pgs-a",listOf(other),guestClaims=listOf(GuestProgressClaim("guest",other.id,10)))
        assertThrows(IllegalArgumentException::class.java) { merge(a,b) }
    }

    @Test fun `mutating immutable mode across copies is rejected not offered as a scoring choice`() {
        val a=CloudRoundVersion.initial(round())
        val b=CloudRoundVersion.initial(round().copy(mode=RoundMode.DOUBLE))
        assertThrows(IllegalArgumentException::class.java) { merge(state(a),state(b)) }
    }

    @Test fun `codec preserves exact resumable input hint and all lifetime facts`() {
        val active=round("medium",difficulty=Difficulty.MEDIUM).editInput("098",2).submitGuess(3)
            .useHint(0,4).editInput("5",5).pause(6)
        val value=state(CloudRoundVersion.initial(active),CloudRoundVersion.initial(win("finished",guesses=4)))
            .copy(tutorialCompletions=listOf(TutorialCompletion(profile.id,10)))
        val encoded=CloudCodec.encode(value)
        val decoded=CloudCodec.decode(encoded.manifest,encoded.chunks,"pgs-a")
        assertEquals(value.versions.toSet(),decoded.versions.toSet())
        assertEquals(active,decoded.versions.single { !it.terminal }.unfinished)
        assertEquals(value.tutorialCompletions,decoded.tutorialCompletions)
        assertEquals(4L,PlayerStatistics.fromFacts(outcomes(decoded)).winningGuessSum)
        assertEquals(1,AchievementCatalog.progressFacts(decoded.ownerIds,outcomes(decoded),decoded.tutorialCompletions).first().steps)
        assertArrayEquals(encoded.manifest,CloudCodec.encode(decoded).manifest)
    }

    @Test fun `completed wire receipts do not depend on secrets or individual guess contents`() {
        val first=win(guesses=2)
        val other=first.copy(secret="573",guesses=listOf("245","573"))
        val a=CloudCodec.encode(state(CloudRoundVersion.initial(first)))
        val b=CloudCodec.encode(state(CloudRoundVersion.initial(other)))
        assertArrayEquals(a.manifest,b.manifest)
        assertEquals(a.chunks.keys,b.chunks.keys)
        a.chunks.keys.forEach { assertArrayEquals(a.chunks[it],b.chunks[it]) }
        assertNull(CloudCodec.decode(a.manifest,a.chunks,"pgs-a").versions.single().unfinished)
    }

    @Test fun `unknown schema corrupt bytes missing chunks and wrong account preserve caller state`() {
        val original=state(CloudRoundVersion.initial(win()))
        val bundle=CloudCodec.encode(original)
        val corrupt=bundle.manifest.copyOf().also { it[10]=(it[10].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { CloudCodec.decode(corrupt,bundle.chunks,"pgs-a") }
        val future=bundle.manifest.copyOfRange(0,bundle.manifest.size-32).also { it[7]=2 }
        val futureManifest=future+MessageDigest.getInstance("SHA-256").digest(future)
        assertThrows(IllegalArgumentException::class.java) { CloudCodec.decode(futureManifest,bundle.chunks,"pgs-a") }
        assertThrows(IllegalArgumentException::class.java) { CloudCodec.decode(bundle.manifest,emptyMap(),"pgs-a") }
        assertThrows(IllegalArgumentException::class.java) { CloudCodec.decode(bundle.manifest,bundle.chunks,"pgs-b") }
        val altered=bundle.chunks.mapValues { (_,bytes)->bytes.copyOf().also { it[it.lastIndex]=0 } }
        assertThrows(IllegalArgumentException::class.java) { CloudCodec.decode(bundle.manifest,altered,"pgs-a") }
        assertEquals(original,state(CloudRoundVersion.initial(win())))
    }

    @Test fun `partitioned archive is bounded deterministic and adding a round changes only its bucket`() {
        val versions=(1..2000).map { CloudRoundVersion.initial(win("round-$it")) }
        val before=CloudCodec.encode(state(*versions.toTypedArray()))
        assertTrue(before.chunks.size>1)
        assertTrue(before.chunks.values.all { it.size<=CloudCodec.MAX_FILE_BYTES })
        assertEquals(before.chunks.keys.toList(),CloudCodec.chunkNames(before.manifest,"pgs-a"))
        assertEquals(2000,CloudCodec.decode(before.manifest,before.chunks,"pgs-a").versions.size)
        val reordered=CloudCodec.encode(state(*versions.reversed().toTypedArray()))
        assertArrayEquals(before.manifest,reordered.manifest)
        val after=CloudCodec.encode(state(*(versions+CloudRoundVersion.initial(win("one-new"))).toTypedArray()))
        assertEquals(1,(after.chunks.keys-before.chunks.keys).size)
        assertEquals(1,(before.chunks.keys-after.chunks.keys).size)
    }

    @Test fun `oversized record or identity collection fails without dropping history`() {
        val tooManyProfiles=(1..1001).map { profile.copy(id="p$it") }
        assertThrows(IllegalArgumentException::class.java) { CloudCodec.encode(state().copy(profiles=tooManyProfiles)) }
        val huge=round().copy(guesses=List(50_001){"098"},startedAt=1)
        assertThrows(IllegalArgumentException::class.java) { CloudRoundVersion.initial(huge) }
        assertEquals(50_001,huge.guesses.size)
    }

    @Test fun `shared reductions agree for full local records and redacted receipts`() {
        val records=listOf(win("a",guesses=3),win("b",guesses=9),round("c").editInput("098",2).submitGuess(3).finish(RoundStatus.GIVEN_UP,20))
        val facts=records.map(OutcomeFact::fromRound)
        assertEquals(PlayerStatistics.fromRounds(records),PlayerStatistics.fromFacts(facts+facts))
        assertEquals(AchievementCatalog.progress(setOf(profile.id),records),AchievementCatalog.progressFacts(setOf(profile.id),facts+facts))
        assertEquals(LifetimeHardWins.score(setOf(profile.id),records),LifetimeHardWins.scoreFacts(setOf(profile.id),facts+facts))
        val overflow=facts.first().copy(guessCount=Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) { PlayerStatistics.fromFacts(listOf(overflow,facts[1])) }
    }
}
