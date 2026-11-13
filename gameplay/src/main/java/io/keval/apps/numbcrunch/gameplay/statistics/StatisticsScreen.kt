package io.keval.apps.numbcrunch.gameplay.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchOrangeTop
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.*
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.common.*
import io.keval.apps.numbcrunch.gameplay.connection.LocalPlayerConnection
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatisticsRoute(repository: ProgressRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val binding = LocalPlayerConnection.current
    val session = binding?.session?.collectAsStateWithLifecycle()?.value
    val name = session?.takeIf { it.status == ConnectionStatus.CONNECTED && it.accountProfile?.id == state.profile?.id }
        ?.player?.displayName
    StatisticsScreen(state, onBack, viewModel::selectMode, viewModel::selectDifficulty, viewModel::refresh, name, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onBack: () -> Unit,
    onModeSelected: (RoundMode?) -> Unit,
    onDifficultySelected: (Difficulty?) -> Unit,
    onRetry: () -> Unit,
    playerName: String? = null,
    modifier: Modifier = Modifier,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val counts = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val averages = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 } }
    val percentages = remember(locale) { NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 } }
    val dates = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    val empty = stringResource(R.string.statistics_no_value)
    GuideScaffold(stringResource(R.string.statistics_title), onBack, modifier) {
        when {
            state.isLoading -> GuideBody(stringResource(R.string.statistics_loading))
            state.hasError -> GuidePanel(stringResource(R.string.statistics_error_title)) {
                GuideBody(stringResource(R.string.statistics_error_message))
                GuideAction(stringResource(R.string.progress_retry), onRetry, Modifier.fillMaxWidth())
            }
            else -> {
                val profileLabel = playerName?.takeIf { it.isNotBlank() } ?: stringResource(
                    if (state.profile?.pgsPlayerId == null) R.string.statistics_guest else R.string.statistics_player,
                )
                GuidePanel(profileLabel) {
                    GuideBody(stringResource(R.string.statistics_device_storage))
                    state.recordedSince?.let { GuideBody(stringResource(R.string.statistics_recorded_since, dates.format(Date(it)))) }
                    GuideBody(stringResource(R.string.statistics_no_history))
                }
                GuidePanel(stringResource(R.string.statistics_filter_title)) {
                    GuideBody(stringResource(R.string.statistics_mode_filter))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatFilter(stringResource(R.string.statistics_all_modes), state.filter.mode == null) { onModeSelected(null) }
                        RoundMode.entries.forEach { mode ->
                            StatFilter(modeLabel(mode), state.filter.mode == mode) { onModeSelected(mode) }
                        }
                    }
                    GuideBody(stringResource(R.string.difficulty_label))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatFilter(stringResource(R.string.statistics_all_difficulties), state.filter.difficulty == null) { onDifficultySelected(null) }
                        Difficulty.entries.forEach { difficulty ->
                            StatFilter(stringResource(difficulty.labelRes), state.filter.difficulty == difficulty) { onDifficultySelected(difficulty) }
                        }
                    }
                    val mode = state.filter.mode?.let { modeLabel(it) } ?: stringResource(R.string.statistics_all_modes)
                    val difficulty = state.filter.difficulty?.let { stringResource(it.labelRes) } ?: stringResource(R.string.statistics_all_difficulties)
                    Text(stringResource(R.string.statistics_scope, mode, difficulty), color = NumbCrunchGoldCream,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                val stats = state.statistics
                if (stats.played == 0L) GuideBody(stringResource(R.string.statistics_empty))
                GuidePanel(stringResource(R.string.statistics_games_title)) {
                    Metric(R.string.statistics_played, counts.format(stats.played))
                    Metric(R.string.statistics_wins, counts.format(stats.wins))
                    Metric(R.string.statistics_given_up, counts.format(stats.giveUps))
                    Metric(R.string.statistics_left, counts.format(stats.abandoned))
                    Metric(R.string.statistics_ongoing, counts.format(state.ongoing))
                    Metric(R.string.statistics_win_rate, stats.winRate?.let(percentages::format) ?: empty)
                    GuideBody(stringResource(R.string.statistics_win_rate_help))
                }
                GuidePanel(stringResource(R.string.statistics_guesses_title)) {
                    Metric(R.string.statistics_average, stats.averageWinningGuesses?.let(averages::format) ?: empty)
                    Metric(R.string.statistics_best, stats.bestWinningGuesses?.let(counts::format) ?: empty)
                    Metric(R.string.statistics_total_guesses, counts.format(stats.totalGuesses))
                    Metric(R.string.statistics_hints, counts.format(stats.hintsUsed))
                    if (stats.wins == 0L) GuideBody(stringResource(R.string.statistics_no_wins))
                    GuideBody(stringResource(R.string.statistics_average_help))
                }
                GuidePanel(stringResource(R.string.statistics_streaks_title)) {
                    Metric(R.string.statistics_current_streak, counts.format(stats.currentWinStreak))
                    Metric(R.string.statistics_best_streak, counts.format(stats.bestWinStreak))
                    GuideBody(stringResource(R.string.statistics_streaks_help))
                }
            }
        }
    }
}

@Composable
private fun modeLabel(mode: RoundMode) = stringResource(
    if (mode == RoundMode.SINGLE) R.string.label_single_player else R.string.label_double_player,
)

@Composable
private fun StatFilter(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) }, modifier = Modifier.heightIn(min = 48.dp))
}

@Composable
private fun Metric(label: Int, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), color = NumbCrunchGoldCream, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
        Text(value, color = NumbCrunchOrangeTop, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
