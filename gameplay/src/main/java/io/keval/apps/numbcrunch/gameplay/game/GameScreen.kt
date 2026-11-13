package io.keval.apps.numbcrunch.gameplay.game

import androidx.compose.foundation.layout.heightIn

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldBorder
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchOrangeTop
import io.keval.apps.numbcrunch.domain.game.CODE_LENGTH
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.game.DigitFeedback
import io.keval.apps.numbcrunch.domain.game.GuessEvaluation
import io.keval.apps.numbcrunch.domain.game.GuessRules
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.audio.GameSound
import io.keval.apps.numbcrunch.gameplay.audio.LocalGameSoundPlayer
import io.keval.apps.numbcrunch.gameplay.audio.digitKeyPitch
import io.keval.apps.numbcrunch.gameplay.audio.rememberTapFeedback
import io.keval.apps.numbcrunch.gameplay.common.labelRes
import io.keval.apps.numbcrunch.gameplay.home.PlayerMode
import io.keval.apps.numbcrunch.gameplay.navigation.GAMEPLAY_GUIDE_ENABLED
import io.keval.apps.numbcrunch.gameplay.progress.NewRoundRequest
import io.keval.apps.numbcrunch.gameplay.progress.ProgressDialog
import io.keval.apps.numbcrunch.gameplay.common.GuideAction
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

private const val ROW_NUMBER_UNITS = 0.55f
private const val REMARK_UNITS = 2.15f
private const val INFO_UNITS = 0.5f
private const val ROW_UNITS = CODE_LENGTH + ROW_NUMBER_UNITS + REMARK_UNITS + INFO_UNITS

// Gaps between row number, digit tiles, remark box and info button.
private const val ROW_GAP_COUNT = CODE_LENGTH + 2
private const val MIN_VISIBLE_ROWS = 5
private const val TILE_REVEAL_STAGGER_MS = 180
private const val TILE_FLIP_MS = 420

// The flip sound lands as the tile turns edge-on; the remark pops in once the last tile lands.
private const val TILE_FLIP_SOUND_OFFSET_MS = 170L
private const val REMARK_REVEAL_DELAY_MS = ((CODE_LENGTH - 1) * TILE_REVEAL_STAGGER_MS + TILE_FLIP_MS).toLong()
private const val SECRET_REVEAL_START_MS = 450L
private val SwipeBackThreshold = 96.dp
private val SwipeMaxShift = 48.dp
private const val WIN_DIALOG_DELAY_MS = 1_100L
private val MaxContentWidth = 520.dp
private val DialogButtonHeight = 48.dp
private val BoardOverlay = Color(0xA81A0B04)
private val KeypadOverlay = Color(0x66140802)

@Composable
fun GameRoute(
    friendSecretNumber: String?,
    difficulty: Difficulty,
    progressRepository: ProgressRepository,
    onExitToHome: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    ownerId: String? = null,
    roundId: String? = null,
    onRequestNewRound: (NewRoundRequest) -> Unit = {},
    modifier: Modifier = Modifier,
    onLeaderboardClick: () -> Unit = {},
) {
    val viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(friendSecretNumber, difficulty, progressRepository, ownerId, roundId))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val requestExit = {
        if (viewModel.onBackRequested()) onExitToHome()
    }
    BackHandler(onBack = requestExit)

    GameScreen(
        uiState = uiState,
        onBack = requestExit,
        onHelp = onHelp,
        onSettings = onSettings,
        onQuit = {
            if (viewModel.onQuitRequested()) onExitToHome()
        },
        onConfirmQuit = viewModel::onConfirmQuit,
        onDismissQuit = viewModel::onDismissQuitConfirmation,
        onQuitRevealDone = onExitToHome,
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onEnter = viewModel::onEnter,
        onHint = viewModel::onHint,
        onConfirmExit = { viewModel.onConfirmExit(onExitToHome) },
        onDismissExit = viewModel::onDismissExitConfirmation,
        onPlayAgain = { viewModel.replayRequest()?.let(onRequestNewRound) },
        onSaveAndPause = { viewModel.onSaveAndPause(onExitToHome) },
        onWinHome = onExitToHome,
        onDismissWinDialog = viewModel::onDismissWinDialog,
        modifier = modifier,
        onLeaderboardClick = { viewModel.onDismissWinDialog(); onLeaderboardClick() },
    )
    if (uiState.roundRecovery != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.progress_changed_title)) },
            text = { Text(stringResource(when {
                uiState.hasPersistenceError -> R.string.progress_reload_failed
                uiState.roundRecovery == RoundRecovery.COMPLETED -> R.string.progress_completed_elsewhere
                else -> R.string.progress_updated_elsewhere
            })) },
            confirmButton = {
                if (uiState.roundRecovery == RoundRecovery.UPDATED) TextButton(
                    onClick = if (uiState.hasPersistenceError) viewModel::onRetryPersistence else viewModel::onReloadChangedRound,
                    enabled = !uiState.isSaving,
                ) { Text(stringResource(R.string.progress_reload_saved)) }
                else TextButton(onClick = onExitToHome, enabled = !uiState.isSaving) {
                    Text(stringResource(R.string.progress_back_home))
                }
            },
            dismissButton = {
                if (uiState.roundRecovery == RoundRecovery.UPDATED) TextButton(onClick = onExitToHome, enabled = !uiState.isSaving) {
                    Text(stringResource(R.string.progress_back_home))
                }
            },
        )
    } else if (uiState.hasPersistenceError) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissPersistenceError,
            title = { Text(stringResource(R.string.progress_save_failed_title)) },
            text = { Text(stringResource(R.string.progress_save_failed_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onRetryPersistence) { Text(stringResource(R.string.progress_retry)) }
            },
            dismissButton = {
                if (uiState.isLoading) {
                    TextButton(onClick = onExitToHome) { Text(stringResource(R.string.progress_back_home)) }
                } else {
                    TextButton(onClick = viewModel::onDismissPersistenceError) {
                        Text(stringResource(R.string.progress_keep_playing))
                    }
                }
            },
        )
    }
}

@Composable
fun GameScreen(
    uiState: GameUiState,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onQuit: () -> Unit,
    onConfirmQuit: () -> Unit,
    onDismissQuit: () -> Unit,
    onQuitRevealDone: () -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onHint: (Int) -> Unit,
    onConfirmExit: () -> Unit,
    onDismissExit: () -> Unit,
    onPlayAgain: () -> Unit,
    onWinHome: () -> Unit,
    onDismissWinDialog: () -> Unit,
    onSaveAndPause: () -> Unit = {},
    modifier: Modifier = Modifier,
    onLeaderboardClick: (() -> Unit)? = null,
) {
    var remarkInfo by remember { mutableStateOf<GuessEvaluation?>(null) }
    GuessResultFeedback(uiState)

    val swipeOffset = remember { Animatable(0f) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .swipeToGoBack(enabled = remarkInfo == null, offset = swipeOffset, onSwipe = onBack),
    ) {
        Image(
            painter = painterResource(id = R.drawable.home_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // While the remark popup is open, hide the game from accessibility services so
                // only the popup can be explored and activated.
                .then(if (remarkInfo != null) Modifier.clearAndSetSemantics {} else Modifier)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentWidth = minOf(maxWidth, MaxContentWidth)
            val metrics = remember(contentWidth, maxHeight) { GameMetrics.from(contentWidth, maxHeight) }

            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize()
                    .graphicsLayer { translationX = swipeOffset.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GameHeader(
                    metrics = metrics,
                    onBack = onBack,
                    onHelp = onHelp,
                    onSettings = onSettings,
                )
                Spacer(modifier = Modifier.height(metrics.sectionGap * 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameModeBadge(
                        text = stringResource(
                            if (uiState.playerMode == PlayerMode.DOUBLE) R.string.game_mode_double else R.string.game_mode_single,
                        ),
                        height = metrics.badgeHeight,
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(uiState.difficulty.labelRes),
                            color = NumbCrunchOrangeTop,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (uiState.difficulty == Difficulty.MEDIUM) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.game_hints_remaining,
                                    uiState.hintsRemaining,
                                    uiState.hintsRemaining,
                                ),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                color = NumbCrunchGoldBorder,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    GamePillButton(
                        label = stringResource(R.string.quit_show),
                        contentDescription = stringResource(R.string.cd_quit_game),
                        height = metrics.badgeHeight,
                        onClick = onQuit,
                    )
                }
                Spacer(modifier = Modifier.height(metrics.sectionGap))
                GuessBoard(
                    uiState = uiState,
                    onRemarkInfo = { remarkInfo = it },
                    onHint = onHint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(modifier = Modifier.height(metrics.sectionGap))
                Keypad(
                    uiState = uiState,
                    keyHeight = metrics.keyHeight,
                    onDigit = onDigit,
                    onDelete = onDelete,
                    onEnter = onEnter,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        RemarkInfoPopup(
            evaluation = remarkInfo,
            onDismiss = { remarkInfo = null },
        )

        if (uiState.showExitConfirmation) {
            ExitConfirmationDialog(
                onConfirm = onConfirmExit,
                onDismiss = onDismissExit,
                onPause = onSaveAndPause,
                isSaving = uiState.isSaving,
            )
        }

        if (uiState.showQuitConfirmation) {
            QuitConfirmationDialog(
                playerMode = uiState.playerMode,
                onConfirm = onConfirmQuit,
                onDismiss = onDismissQuit,
            )
        }

        if (uiState.isGivenUp) {
            QuitRevealDialog(
                secret = uiState.revealedSecret.orEmpty(),
                guessCount = uiState.guesses.size,
                onHome = onQuitRevealDone,
            )
        }

        var winDialogVisible by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.showWinDialog) {
            if (uiState.showWinDialog) {
                if (uiState.lastRevealedIndex != null) delay(WIN_DIALOG_DELAY_MS)
                winDialogVisible = true
            } else {
                winDialogVisible = false
            }
        }
        if (winDialogVisible && uiState.showWinDialog) {
            WinDialog(
                uiState = uiState,
                onPlayAgain = onPlayAgain,
                onHome = onWinHome,
                onDismiss = onDismissWinDialog,
                onLeaderboard = onLeaderboardClick,
            )
        }
    }
}

private data class GameMetrics(
    val iconSize: Dp,
    val titleWidth: Dp,
    val titleHeight: Dp,
    val badgeHeight: Dp,
    val keyHeight: Dp,
    val sectionGap: Dp,
) {
    companion object {
        private const val TITLE_ASPECT = 2125f / 472f

        // Keypad height = 4 key rows + 3 row gaps (0.22 key) + vertical padding (2 x 0.28 key).
        private const val KEYPAD_HEIGHT_IN_KEYS = 4f + 3 * 0.22f + 2 * 0.28f

        fun from(width: Dp, height: Dp): GameMetrics {
            val iconSize = (width * 0.13f).coerceIn(44.dp, 60.dp)
            val titleHeight = minOf((width * 0.6f) / TITLE_ASPECT, height * 0.085f)
            val badgeHeight = minOf((width * 0.085f).coerceIn(28.dp, 40.dp), height * 0.05f).coerceAtLeast(24.dp)
            val keyHeight = minOf(
                (width * 0.125f).coerceIn(40.dp, 64.dp),
                height * 0.34f / KEYPAD_HEIGHT_IN_KEYS,
            ).coerceAtLeast(36.dp)
            return GameMetrics(
                iconSize = iconSize,
                titleWidth = titleHeight * TITLE_ASPECT,
                titleHeight = titleHeight,
                badgeHeight = badgeHeight,
                keyHeight = keyHeight,
                sectionGap = (height * 0.014f).coerceIn(6.dp, 16.dp),
            )
        }
    }
}

@Composable
private fun GameHeader(
    metrics: GameMetrics,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameRoundIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_back_home),
            size = metrics.iconSize,
            onClick = onBack,
        )
        if (GAMEPLAY_GUIDE_ENABLED) {
            // Balance the help button so the title stays centered.
            Spacer(modifier = Modifier.size(metrics.iconSize.coerceAtLeast(48.dp)))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.home_title),
                contentDescription = stringResource(R.string.cd_game_title),
                modifier = Modifier
                    .width(metrics.titleWidth)
                    .height(metrics.titleHeight),
                contentScale = ContentScale.Fit,
            )
        }
        if (GAMEPLAY_GUIDE_ENABLED) {
            GameRoundIconButton(
                icon = Icons.Filled.QuestionMark,
                contentDescription = stringResource(R.string.cd_help),
                size = metrics.iconSize,
                onClick = onHelp,
            )
        }
        GameRoundIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.cd_settings),
            size = metrics.iconSize,
            onClick = onSettings,
        )
    }
}

/**
 * Plays the analysis feedback for a newly submitted guess: a tick as each tile flips, then a
 * sound and haptic matched to the remark once it pops in. The win celebration plays with the
 * win dialog instead.
 */
@Composable
private fun GuessResultFeedback(uiState: GameUiState) {
    val player = LocalGameSoundPlayer.current
    val view = LocalView.current
    // Saved so returning from Help or Settings does not replay the last result.
    var playedGuessCount by rememberSaveable { mutableIntStateOf(uiState.guesses.size) }
    if (uiState.guesses.size < playedGuessCount) playedGuessCount = uiState.guesses.size

    val revealed = uiState.lastRevealedIndex?.let { uiState.guesses.getOrNull(it) }
    LaunchedEffect(uiState.lastRevealedIndex, uiState.guesses.size) {
        if (revealed == null || uiState.guesses.size <= playedGuessCount) return@LaunchedEffect
        playedGuessCount = uiState.guesses.size
        var elapsed = 0L
        repeat(CODE_LENGTH) { column ->
            val flipAt = column * TILE_REVEAL_STAGGER_MS + TILE_FLIP_SOUND_OFFSET_MS
            delay(flipAt - elapsed)
            elapsed = flipAt
            player.play(GameSound.Flip, pitch = 1f + column * 0.08f)
        }
        if (revealed.isWin) return@LaunchedEffect
        delay(REMARK_REVEAL_DELAY_MS - elapsed)
        if (revealed.remark == GuessRules.SMYLE_REMARK) {
            player.play(GameSound.Smyle)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } else {
            player.play(GameSound.Clue)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

/**
 * A horizontal swipe in either direction (like Android's edge back gesture) asks to go back.
 * The content follows the finger slightly so the gesture feels connected.
 */
@Composable
private fun Modifier.swipeToGoBack(
    enabled: Boolean,
    offset: Animatable<Float, AnimationVector1D>,
    onSwipe: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val density = LocalDensity.current
    val threshold = with(density) { SwipeBackThreshold.toPx() }
    val maxShift = with(density) { SwipeMaxShift.toPx() }
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (abs(total) >= threshold) currentOnSwipe()
                scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f) } },
        ) { change, dragAmount ->
            change.consume()
            total += dragAmount
            scope.launch { offset.snapTo((total * 0.3f).coerceIn(-maxShift, maxShift)) }
        }
    }
}

@Composable
private fun GuessBoard(
    uiState: GameUiState,
    onRemarkInfo: (GuessEvaluation) -> Unit,
    onHint: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    WoodPanel(
        textureRes = R.drawable.game_board_wood,
        overlay = BoardOverlay,
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val baseGap = (maxWidth * 0.022f).coerceIn(4.dp, 10.dp)
            val widthTile = (maxWidth - baseGap * ROW_GAP_COUNT) / ROW_UNITS
            val heightTile = (maxHeight - baseGap * (MIN_VISIBLE_ROWS - 1)) / MIN_VISIBLE_ROWS
            val tile = minOf(widthTile, heightTile).coerceAtLeast(24.dp)
            val rowWidth = tile * ROW_UNITS + baseGap * ROW_GAP_COUNT
            val visibleRows = floor(((maxHeight + baseGap) / (tile + baseGap)).toDouble()).toInt().coerceAtLeast(1)
            val rowGap = if (visibleRows > 1) {
                ((maxHeight - tile * visibleRows) / (visibleRows - 1)).coerceIn(baseGap, baseGap * 2.5f)
            } else {
                baseGap
            }

            val activeRow = if (uiState.isRoundOver) null else uiState.guesses.size
            val usedRows = uiState.guesses.size + if (activeRow != null) 1 else 0
            val totalRows = maxOf(visibleRows, usedRows)
            val listState = rememberLazyListState()

            LaunchedEffect(usedRows, visibleRows) {
                val target = (usedRows - visibleRows).coerceAtLeast(0)
                if (target > 0 || listState.firstVisibleItemIndex > 0) {
                    listState.animateScrollToItem(target)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(rowWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                items(count = totalRows) { index ->
                    GuessRow(
                        index = index,
                        uiState = uiState,
                        isActive = index == activeRow,
                        tile = tile,
                        gap = baseGap,
                        onRemarkInfo = onRemarkInfo,
                        onHint = onHint,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuessRow(
    index: Int,
    uiState: GameUiState,
    isActive: Boolean,
    tile: Dp,
    gap: Dp,
    onRemarkInfo: (GuessEvaluation) -> Unit,
    onHint: (Int) -> Unit,
) {
    val evaluation = uiState.guesses.getOrNull(index)
    val animateReveal = evaluation != null && uiState.lastRevealedIndex == index
    val showsDigitFeedback = uiState.showsDigitFeedback(index)
    val rowDescription = when {
        evaluation != null -> stringResource(
            R.string.cd_guess_row,
            index + 1,
            evaluation.guess.toList().joinToString(" "),
            evaluation.remark,
        )
        isActive -> stringResource(R.string.cd_active_row, index + 1, uiState.currentInput.toList().joinToString(" "))
        else -> stringResource(R.string.cd_empty_row, index + 1)
    }

    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = rowDescription },
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowNumber(number = index + 1, height = tile, modifier = Modifier.width(tile * ROW_NUMBER_UNITS))
        repeat(CODE_LENGTH) { column ->
            val state = when {
                evaluation != null -> TileState.Revealed(evaluation.guess[column], evaluation.digits[column])
                isActive && column < uiState.currentInput.length -> TileState.Pending(uiState.currentInput[column])
                else -> TileState.Empty
            }
            DigitTile(
                state = state,
                size = tile,
                isCursor = isActive && column == uiState.currentInput.length,
                animateRevealDelayMs = if (animateReveal) column * TILE_REVEAL_STAGGER_MS else null,
                colorFeedbackEnabled = showsDigitFeedback,
            )
        }
        val infoInteraction = remember { MutableInteractionSource() }
        val infoPressed by infoInteraction.collectIsPressedAsState()
        val isMedium = uiState.difficulty == Difficulty.MEDIUM
        val remarkAction = if (evaluation != null) {
            val description = stringResource(R.string.cd_remark_info, evaluation.remark)
            Modifier.clickable(
                interactionSource = infoInteraction,
                indication = null,
                role = Role.Button,
                onClick = { onRemarkInfo(evaluation) },
            ).semantics { contentDescription = description }
        } else {
            Modifier
        }
        val canUseHint = uiState.canUseHint(index)
        val hintAction = if (canUseHint) {
            val description = stringResource(R.string.cd_game_hint, index + 1)
            val feedback = rememberTapFeedback()
            Modifier.clickable(role = Role.Button) {
                feedback()
                onHint(index)
            }.semantics { contentDescription = description }
        } else {
            Modifier
        }
        // Medium reserves the original info slot for a separate action beside the remark.
        Row(
            modifier = Modifier
                .height(tile)
                .then(if (isMedium) Modifier else remarkAction),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(tile * REMARK_UNITS)
                    .fillMaxHeight()
                    .then(if (isMedium) remarkAction else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                RemarkBox(
                    remark = evaluation?.remark,
                    isWinner = evaluation?.isWin == true,
                    height = tile * 0.74f,
                    modifier = Modifier.fillMaxWidth(),
                    animateReveal = animateReveal,
                    revealDelayMs = REMARK_REVEAL_DELAY_MS,
                )
            }
            Box(
                modifier = Modifier
                    .width(tile * INFO_UNITS)
                    .fillMaxHeight()
                    .then(hintAction),
                contentAlignment = Alignment.Center,
            ) {
                if (canUseHint) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = NumbCrunchGoldBorder,
                        modifier = Modifier.size(tile * 0.46f),
                    )
                } else if (!isMedium && evaluation != null) {
                    RemarkInfoIcon(size = tile * 0.42f, isPressed = infoPressed)
                }
            }
        }
    }
}

@Composable
private fun Keypad(
    uiState: GameUiState,
    keyHeight: Dp,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    WoodPanel(
        textureRes = R.drawable.game_keypad_wood,
        overlay = KeypadOverlay,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = keyHeight * 0.4f, vertical = keyHeight * 0.28f),
            verticalArrangement = Arrangement.spacedBy(keyHeight * 0.22f),
        ) {
            val horizontalGap = keyHeight * 0.45f
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                    row.forEach { digit -> DigitKey(digit, uiState, keyHeight, onDigit, Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                KeypadKey(
                    label = stringResource(R.string.key_delete),
                    contentDescription = stringResource(R.string.cd_key_delete),
                    style = KeyStyle.Delete,
                    enabled = uiState.canDelete,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
                DigitKey('0', uiState, keyHeight, onDigit, Modifier.weight(1f))
                KeypadKey(
                    label = stringResource(R.string.key_enter),
                    contentDescription = stringResource(R.string.cd_key_enter),
                    style = KeyStyle.Enter,
                    enabled = uiState.canEnter,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = onEnter,
                )
            }
        }
    }
}

@Composable
private fun DigitKey(
    digit: Char,
    uiState: GameUiState,
    keyHeight: Dp,
    onDigit: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeypadKey(
        label = digit.toString(),
        contentDescription = stringResource(R.string.cd_key_digit, digit.toString()),
        style = KeyStyle.Digit,
        enabled = uiState.isDigitEnabled(digit),
        height = keyHeight,
        modifier = modifier,
        soundPitch = digitKeyPitch(digit),
        onClick = { onDigit(digit) },
    )
}

@Composable
private fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    isSaving: Boolean,
) {
    ProgressDialog(
        title = stringResource(R.string.progress_pause_title),
        message = stringResource(R.string.progress_pause_message),
        onDismiss = { if (!isSaving) onDismiss() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideAction(stringResource(R.string.progress_save_pause), onPause, Modifier.fillMaxWidth(), enabled = !isSaving)
            GuideAction(stringResource(R.string.progress_continue), onDismiss, Modifier.fillMaxWidth(), enabled = !isSaving)
            TextButton(onClick = onConfirm, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.progress_abandon_confirm), color = NumbCrunchGoldCream)
            }
        }
    }
}

@Composable
private fun QuitConfirmationDialog(
    playerMode: PlayerMode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val modeName = stringResource(
        if (playerMode == PlayerMode.DOUBLE) R.string.game_mode_double else R.string.game_mode_single,
    )
    Dialog(onDismissRequest = onDismiss) {
        NumbCrunchDialogFrame(
            textureRes = R.drawable.game_keypad_wood,
            title = stringResource(R.string.quit_title),
            message = stringResource(R.string.quit_message, modeName),
        ) {
            DialogButtons(
                secondaryText = stringResource(R.string.exit_cancel),
                onSecondary = onDismiss,
                primaryText = stringResource(R.string.quit_confirm),
                onPrimary = onConfirm,
            )
        }
    }
}

@Composable
private fun QuitRevealDialog(
    secret: String,
    guessCount: Int,
    onHome: () -> Unit,
) {
    val player = LocalGameSoundPlayer.current
    LaunchedEffect(secret) {
        // A tick for each secret tile as it flips over.
        delay(SECRET_REVEAL_START_MS)
        repeat(secret.length) { index ->
            player.play(GameSound.Flip, pitch = 1f + index * 0.08f)
            delay(TILE_REVEAL_STAGGER_MS.toLong())
        }
    }
    Dialog(onDismissRequest = onHome) {
        NumbCrunchDialogFrame(
            textureRes = R.drawable.game_keypad_wood,
            title = stringResource(R.string.quit_reveal_title),
            message = pluralStringResource(R.plurals.quit_reveal_message, guessCount, guessCount),
            content = {
                Row(
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = secret.toList().joinToString(" ")
                    },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    secret.forEachIndexed { index, digit ->
                        DigitTile(
                            state = TileState.Revealed(digit, DigitFeedback.EXACT),
                            size = 56.dp,
                            isCursor = false,
                            animateRevealDelayMs = SECRET_REVEAL_START_MS.toInt() - TILE_FLIP_SOUND_OFFSET_MS.toInt() +
                                index * TILE_REVEAL_STAGGER_MS,
                            colorFeedbackEnabled = false,
                        )
                    }
                }
            },
        ) {
            KeypadKey(
                label = stringResource(R.string.win_home),
                contentDescription = stringResource(R.string.win_home),
                style = KeyStyle.Enter,
                enabled = true,
                height = DialogButtonHeight,
                modifier = Modifier.fillMaxWidth(0.6f),
                onClick = onHome,
            )
        }
    }
}

@Composable
private fun WinDialog(
    uiState: GameUiState,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    onDismiss: () -> Unit,
    onLeaderboard: (() -> Unit)?,
) {
    val guessCount = uiState.guesses.size
    val player = LocalGameSoundPlayer.current
    val view = LocalView.current
    LaunchedEffect(Unit) {
        player.play(GameSound.Applause)
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
        )
    }
    // Full-window dialog so the confetti can rain over the whole screen, not just the card.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tapping outside the card dismisses, like a standard dialog.
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            WinDialogCard(uiState, guessCount, onPlayAgain, onHome, onLeaderboard)
            ConfettiBurst(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun WinDialogCard(
    uiState: GameUiState,
    guessCount: Int,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    onLeaderboard: (() -> Unit)?,
) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
    NumbCrunchDialogFrame(
        textureRes = R.drawable.game_keypad_wood,
        title = stringResource(R.string.win_title),
        message = pluralStringResource(
            R.plurals.win_message,
            guessCount,
            uiState.revealedSecret.orEmpty(),
            guessCount,
        ),
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .widthIn(max = 420.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            // Swallow taps on the card so they do not dismiss the dialog.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        if (uiState.playerMode == PlayerMode.SINGLE) {
            DialogButtons(
                secondaryText = stringResource(R.string.win_home),
                onSecondary = onHome,
                primaryText = stringResource(R.string.win_play_again),
                onPrimary = onPlayAgain,
            )
            if (uiState.difficulty == Difficulty.HARD && onLeaderboard != null) {
                TextButton(onClick = onLeaderboard, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.hard_board_title), color = NumbCrunchGoldCream)
                }
            }
        } else {
            KeypadKey(
                label = stringResource(R.string.win_home),
                contentDescription = stringResource(R.string.win_home),
                style = KeyStyle.Enter,
                enabled = true,
                height = DialogButtonHeight,
                modifier = Modifier.fillMaxWidth(0.6f),
                onClick = onHome,
            )
        }
    }
}

@Composable
private fun DialogButtons(
    secondaryText: String,
    onSecondary: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeypadKey(
            label = secondaryText,
            contentDescription = secondaryText,
            style = KeyStyle.Secondary,
            enabled = true,
            height = DialogButtonHeight,
            modifier = Modifier.weight(1f),
            onClick = onSecondary,
        )
        KeypadKey(
            label = primaryText,
            contentDescription = primaryText,
            style = KeyStyle.Enter,
            enabled = true,
            height = DialogButtonHeight,
            modifier = Modifier.weight(1f),
            onClick = onPrimary,
        )
    }
}

private fun previewState(): GameUiState {
    val secret = "573"
    return GameUiState(
        guesses = listOf("481", "219", "573").map { GuessRules.evaluate(secret, it) },
        isWon = true,
        revealedSecret = secret,
    )
}

@Composable
private fun GameScreenPreviewContent(uiState: GameUiState) {
    NumbCrunchTheme {
        GameScreen(
            uiState = uiState,
            onBack = {},
            onHelp = {},
            onSettings = {},
            onQuit = {},
            onConfirmQuit = {},
            onDismissQuit = {},
            onQuitRevealDone = {},
            onDigit = {},
            onDelete = {},
            onEnter = {},
            onHint = {},
            onConfirmExit = {},
            onDismissExit = {},
            onPlayAgain = {},
            onWinHome = {},
            onDismissWinDialog = {},
        )
    }
}

@Preview(showBackground = true, name = "In progress", widthDp = 411, heightDp = 891)
@Composable
private fun GameScreenInProgressPreview() {
    val secret = "573"
    GameScreenPreviewContent(
        GameUiState(
            guesses = listOf("481", "219").map { GuessRules.evaluate(secret, it) },
            currentInput = "57",
        ),
    )
}

@Preview(showBackground = true, name = "Won", widthDp = 411, heightDp = 891)
@Composable
private fun GameScreenWonPreview() {
    GameScreenPreviewContent(previewState())
}

@Preview(showBackground = true, name = "Small phone", widthDp = 320, heightDp = 568)
@Composable
private fun GameScreenSmallPreview() {
    GameScreenPreviewContent(GameUiState(playerMode = PlayerMode.DOUBLE, currentInput = "1"))
}

@Preview(showBackground = true, name = "Tablet", widthDp = 800, heightDp = 1280)
@Composable
private fun GameScreenTabletPreview() {
    GameScreenPreviewContent(previewState())
}
