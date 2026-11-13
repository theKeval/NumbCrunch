package io.keval.apps.numbcrunch.domain.progress

import io.keval.apps.numbcrunch.domain.cloud.CloudCodec
import io.keval.apps.numbcrunch.domain.cloud.CloudMerge
import io.keval.apps.numbcrunch.domain.cloud.CloudProgress
import io.keval.apps.numbcrunch.domain.cloud.CloudRoundVersion
import io.keval.apps.numbcrunch.domain.engagement.AchievementCatalog
import io.keval.apps.numbcrunch.domain.engagement.LifetimeHardWins
import io.keval.apps.numbcrunch.domain.game.Difficulty
import org.junit.Assert.*
import org.junit.Test

/** Cross-feature acceptance examples, independent of delivery/SDK mocks. */
class PlayerExperienceMatrixTest {
    private val profile=PlayerProfile("local","account",1)
    private fun win(id:String,mode:RoundMode,difficulty:Difficulty,guesses:Int):RoundRecord {
        var round=RoundRecord(id,profile.id,mode,difficulty,"012",1)
        repeat(guesses-1) { round=round.editInput("098",2).submitGuess(3) }
        return round.editInput("012",4).submitGuess(5)
    }

    @Test fun allSixCombinationsKeepStatisticsButOnlySingleHardContributesToLeaderboard() {
        val rounds=RoundMode.entries.flatMap { mode -> Difficulty.entries.map { difficulty ->
            win("$mode-$difficulty",mode,difficulty,4)
        } }
        assertEquals(6L,PlayerStatistics.fromRounds(rounds).wins)
        for (mode in RoundMode.entries) for (difficulty in Difficulty.entries) {
            val statistics=PlayerStatistics.fromRounds(rounds,StatisticsFilter(mode,difficulty))
            assertEquals(1L,statistics.played);assertEquals(1L,statistics.wins)
            assertEquals(4.0,requireNotNull(statistics.averageWinningGuesses),0.0)
        }
        assertEquals(1L,LifetimeHardWins.score(setOf(profile.id),rounds+rounds))
    }

    @Test fun weightedAverageAndPausedDenominatorsSurviveCloudRoundTripAndRepeatedMerge() {
        val wins=listOf(4,7,10).map { count -> win("win-$count",RoundMode.SINGLE,Difficulty.HARD,count) }
        val paused=RoundRecord("paused",profile.id,RoundMode.SINGLE,Difficulty.MEDIUM,"012",1)
            .editInput("098",2).submitGuess(3).useHint(0,4).editInput("5",5).pause(6)
        val zeroGuess=RoundRecord("unplayed",profile.id,RoundMode.DOUBLE,Difficulty.EASY,"345",1).pause(2)
        val records=wins+paused+zeroGuess
        val original=CloudProgress.fromSnapshot(ProgressSnapshot(profile=profile,rounds=records))
        val bundle=CloudCodec.encode(original)
        val restored=CloudCodec.decode(bundle.manifest,bundle.chunks,"account")
        val merged=CloudMerge.merge(original,restored,"account").progress
        val stats=PlayerStatistics.fromFacts(merged.versions.mapNotNull { it.outcome },merged.versions.mapNotNull { it.unfinished })
        assertEquals(4L,stats.played);assertEquals(3L,stats.resolved);assertEquals(3L,stats.wins)
        assertEquals(0L,stats.abandoned);assertEquals(0L,stats.giveUps)
        assertEquals(21L,stats.winningGuessSum);assertEquals(7.0,requireNotNull(stats.averageWinningGuesses),0.0)
        assertEquals(1.0,requireNotNull(stats.winRate),0.0);assertEquals(1L,stats.hintsUsed)
        assertEquals(3L,stats.currentWinStreak);assertEquals(4L,stats.bestWinningGuesses)
        assertEquals(PlayerStatistics.fromRounds(records),stats)
        assertEquals(AchievementCatalog.progress(setOf(profile.id),records),
            AchievementCatalog.progressFacts(merged.ownerIds,merged.versions.mapNotNull { it.outcome }))
        assertEquals(3L,LifetimeHardWins.scoreFacts(merged.ownerIds,merged.versions.mapNotNull { it.outcome }))
        assertEquals(paused,merged.versions.single { it.roundId=="paused" }.unfinished)
        assertTrue(CloudMerge.inspect(merged).requiresChoice) // Both unfinished games remain recoverable.
    }
}
