package io.keval.apps.numbcrunch.gameplay.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.domain.engagement.*
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.common.*
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale

data class HardWinsBinding(
    val state: StateFlow<HardWinsLeaderboardState>,
    val load: (RankingWindow) -> Unit,
    val retrySubmission: () -> Unit,
)
val LocalHardWins = staticCompositionLocalOf<HardWinsBinding?> { null }

@Composable
fun HardWinsLeaderboardRoute(onBack: () -> Unit) {
    val binding = LocalHardWins.current
    val state = binding?.state?.collectAsStateWithLifecycle()?.value ?: HardWinsLeaderboardState()
    LaunchedEffect(state.context?.accountPlayerId, state.context?.generation) { binding?.load(state.window) }
    HardWinsLeaderboardScreen(state, onBack, { binding?.load(it) }, { binding?.retrySubmission() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardWinsLeaderboardScreen(
    state: HardWinsLeaderboardState,
    onBack: () -> Unit,
    onWindow: (RankingWindow) -> Unit,
    onRetrySubmission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    GuideScaffold(stringResource(R.string.hard_board_title), onBack, modifier) {
        GuidePanel(stringResource(R.string.hard_board_all_time)) {
            GuideBody(stringResource(R.string.hard_board_rules))
            GuideBody(stringResource(R.string.hard_board_trust))
            val count = state.context?.localWins
            GuideBody(if (count == null) stringResource(R.string.hard_board_local_loading)
                else stringResource(R.string.hard_board_local_total, numbers.format(count)))
            if (count == 0L) GuideBody(stringResource(R.string.hard_board_no_wins))
            Text(stringResource(when (state.delivery) {
                ScoreDelivery.LOCAL -> R.string.hard_board_local_only
                ScoreDelivery.WAITING_FOR_BACKUP -> R.string.hard_board_waiting
                ScoreDelivery.PENDING -> R.string.hard_board_pending
                ScoreDelivery.ACKNOWLEDGED -> R.string.hard_board_acknowledged
            }), color = NumbCrunchGoldCream, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.delivery == ScoreDelivery.PENDING || count == null) {
                GuideAction(stringResource(R.string.hard_board_retry_score), onRetrySubmission, Modifier.fillMaxWidth())
            }
        }
        if (state.status == RankingStatus.DISCONNECTED) {
            GuideBody(stringResource(R.string.hard_board_connect))
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RankingWindow.entries.forEach { window ->
                    FilterChip(selected = state.window == window, onClick = { onWindow(window) },
                        label = { Text(stringResource(if (window == RankingWindow.TOP) R.string.hard_board_top else R.string.hard_board_near)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            when (state.status) {
                RankingStatus.LOADING, RankingStatus.IDLE -> GuideBody(stringResource(R.string.hard_board_loading))
                RankingStatus.ERROR -> {
                    GuideBody(stringResource(R.string.hard_board_error))
                    GuideAction(stringResource(R.string.progress_retry), { onWindow(state.window) }, Modifier.fillMaxWidth())
                }
                RankingStatus.READY -> {
                    val page = state.page
                    if (page?.stale == true) GuideBody(stringResource(R.string.hard_board_cached))
                    GuidePanel(stringResource(R.string.hard_board_your_rank)) {
                        page?.currentPlayer?.let { RankingEntry(it) }
                            ?: GuideBody(stringResource(R.string.hard_board_unranked))
                    }
                    if (page?.rows.isNullOrEmpty()) GuideBody(stringResource(R.string.hard_board_empty))
                    else GuidePanel(stringResource(if (state.window == RankingWindow.TOP) R.string.hard_board_top else R.string.hard_board_near)) {
                        page?.rows?.forEach { RankingEntry(it) }
                    }
                    GuideAction(stringResource(R.string.hard_board_refresh), { onWindow(state.window) }, Modifier.fillMaxWidth())
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun RankingEntry(row: RankingRow) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(row.rank ?: stringResource(R.string.statistics_no_value), color = NumbCrunchGoldCream, modifier = Modifier.weight(1f))
        Text(row.playerName.ifBlank { stringResource(R.string.hard_board_unknown_player) }, color = NumbCrunchGoldCream,
            modifier = Modifier.weight(3f))
        Text(row.score, color = NumbCrunchGoldCream, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
    }
}
