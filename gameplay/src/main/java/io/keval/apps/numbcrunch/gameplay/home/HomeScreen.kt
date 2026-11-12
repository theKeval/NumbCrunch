package io.keval.apps.numbcrunch.gameplay.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchChocolateDark
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldBorder
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.game.CODE_LENGTH
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.usecase.GetWelcomeMessageUseCase
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.audio.rememberTapFeedback
import io.keval.apps.numbcrunch.gameplay.common.labelRes
import io.keval.apps.numbcrunch.gameplay.game.GameRoundIconButton
import io.keval.apps.numbcrunch.gameplay.navigation.GAMEPLAY_GUIDE_ENABLED

@Composable
fun HomeRoute(
    getWelcomeMessageUseCase: GetWelcomeMessageUseCase,
    onStartClick: (HomeUiState) -> Unit,
    onTutorialClick: () -> Unit,
    onGameplayClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(getWelcomeMessageUseCase),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onPlayerModeSelected = viewModel::onPlayerModeSelected,
        onDifficultySelected = viewModel::onDifficultySelected,
        onFriendSecretDigit = viewModel::onFriendSecretDigit,
        onFriendSecretDelete = viewModel::onFriendSecretDelete,
        onStartClick = {
            if (viewModel.canStartGame()) {
                onStartClick(viewModel.uiState.value)
                viewModel.onGameStarted()
            }
        },
        onTutorialClick = onTutorialClick,
        onGameplayClick = onGameplayClick,
        onAboutClick = onAboutClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onPlayerModeSelected: (PlayerMode) -> Unit,
    onDifficultySelected: (Difficulty) -> Unit,
    onFriendSecretDigit: (Char) -> Unit,
    onFriendSecretDelete: () -> Unit,
    onStartClick: () -> Unit,
    onTutorialClick: () -> Unit,
    onGameplayClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The friend-secret keypad sheet; it opens by itself when Double Player is chosen.
    var secretSheetOpen by rememberSaveable { mutableStateOf(false) }
    val showSecretSheet = secretSheetOpen && uiState.playerMode == PlayerMode.DOUBLE
    val selectPlayerMode: (PlayerMode) -> Unit = { mode ->
        if (mode == PlayerMode.DOUBLE && uiState.playerMode != PlayerMode.DOUBLE &&
            uiState.friendSecretNumber.length < CODE_LENGTH
        ) {
            secretSheetOpen = true
        }
        onPlayerModeSelected(mode)
    }
    val startGame: () -> Unit = {
        // An incomplete secret reopens the keypad rather than only showing an error.
        if (uiState.playerMode == PlayerMode.DOUBLE && uiState.friendSecretNumber.length < CODE_LENGTH) {
            secretSheetOpen = true
        }
        onStartClick()
    }

    Surface(modifier = modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        val titlePainter = painterResource(id = R.drawable.home_title)
        val intrinsicSize = titlePainter.intrinsicSize
        val titleAspectRatio = if (intrinsicSize.height > 0f) {
            intrinsicSize.width / intrinsicSize.height
        } else {
            2125f / 472f
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val systemBars = WindowInsets.systemBars.asPaddingValues()
            val availableHeight = maxHeight - systemBars.calculateTopPadding() - systemBars.calculateBottomPadding()
            val maxContentWidth = minOf(maxWidth, 460.dp)
            val titleWidthBase = maxContentWidth * 0.72f
            val toggleWidthBase = maxContentWidth * 0.6f
            val startWidthBase = maxContentWidth * 0.53f
            val secondaryWidthBase = maxContentWidth * 0.51f

            val titleHeightBase = titleWidthBase / titleAspectRatio
            val toggleHeightBase = (toggleWidthBase / 3.4f).coerceAtLeast(48.dp)
            val inputHeightBase = (toggleWidthBase / 4.8f).coerceAtLeast(48.dp)
            val startHeightBase = (startWidthBase / 3.3f).coerceAtLeast(48.dp)
            val secondaryHeightBase = (secondaryWidthBase / 4.8f).coerceAtLeast(48.dp)

            val topGapBase = 16.dp
            val betweenControlsBase = if (uiState.playerMode == PlayerMode.DOUBLE) 8.dp else 14.dp
            val difficultyHeightBase = 100.dp
            val controlSpacingBase = maxContentWidth * 0.035f
            val bottomPaddingBase = 12.dp
            val validationHeightBase = if (uiState.playerMode == PlayerMode.DOUBLE && uiState.secretValidationMessage != null) 24.dp else 0.dp
            val inputSectionHeight = if (uiState.playerMode == PlayerMode.DOUBLE) {
                inputHeightBase + controlSpacingBase + validationHeightBase
            } else {
                0.dp
            }

            // Tutorial and About, plus Gameplay when its guide is enabled.
            val secondaryButtonCount = if (GAMEPLAY_GUIDE_ENABLED) 3f else 2f
            val controlsHeightBase =
                toggleHeightBase +
                    betweenControlsBase +
                    inputSectionHeight +
                    difficultyHeightBase +
                    startHeightBase +
                    (secondaryHeightBase * secondaryButtonCount) +
                    (controlSpacingBase * (secondaryButtonCount + 2))

            val staticHeight = topGapBase + titleHeightBase + bottomPaddingBase + controlsHeightBase
            val verticalHeadroom = (availableHeight - staticHeight).coerceAtLeast(0.dp)
            val scale = (availableHeight / staticHeight).coerceAtMost(1f)

            val titleWidth = titleWidthBase * scale
            val toggleWidth = toggleWidthBase * scale
            val startWidth = startWidthBase * scale
            val secondaryWidth = secondaryWidthBase * scale
            val titleHeight = titleHeightBase * scale
            val inputHeight = (inputHeightBase * scale).coerceAtLeast(48.dp)
            val gapBeforeTitle = topGapBase * scale + verticalHeadroom * 0.1f
            val gapAfterTitle = 8.dp + verticalHeadroom * 0.35f
            val gapAfterControls = bottomPaddingBase * scale + verticalHeadroom * 0.55f
            val controlSpacing = controlSpacingBase * scale
            val betweenControls = betweenControlsBase * scale

            Image(
                painter = painterResource(id = R.drawable.home_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // While the keypad sheet is open, only the sheet is exposed to accessibility services:
            // the whole Home background, including Settings, is hidden from them.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showSecretSheet) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(gapBeforeTitle))

                    Image(
                        painter = titlePainter,
                        contentDescription = null,
                        modifier = Modifier
                            .width(titleWidth)
                            .height(titleHeight),
                        contentScale = ContentScale.Fit,
                    )

                    Spacer(modifier = Modifier.height(gapAfterTitle))

                    ControlsSection(
                        uiState = uiState,
                        toggleWidth = toggleWidth,
                        inputHeight = inputHeight,
                        startWidth = startWidth,
                        secondaryWidth = secondaryWidth,
                        controlSpacing = controlSpacing,
                        betweenControls = betweenControls,
                        onPlayerModeSelected = selectPlayerMode,
                        onDifficultySelected = onDifficultySelected,
                        onSecretSlotClick = { secretSheetOpen = true },
                        onStartClick = startGame,
                        onTutorialClick = onTutorialClick,
                        onGameplayClick = onGameplayClick,
                        onAboutClick = onAboutClick,
                    )

                    Spacer(modifier = Modifier.height(gapAfterControls))
                }

                GameRoundIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    size = 44.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 4.dp, end = 8.dp),
                    onClick = onSettingsClick,
                )
            }

            FriendSecretSheet(
                visible = showSecretSheet,
                secret = uiState.friendSecretNumber,
                onDigit = onFriendSecretDigit,
                onDelete = onFriendSecretDelete,
                onDismiss = { secretSheetOpen = false },
            )
        }
    }
}

@Composable
private fun ControlsSection(
    uiState: HomeUiState,
    toggleWidth: Dp,
    inputHeight: Dp,
    startWidth: Dp,
    secondaryWidth: Dp,
    controlSpacing: Dp,
    betweenControls: Dp,
    onPlayerModeSelected: (PlayerMode) -> Unit,
    onDifficultySelected: (Difficulty) -> Unit,
    onSecretSlotClick: () -> Unit,
    onStartClick: () -> Unit,
    onTutorialClick: () -> Unit,
    onGameplayClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    PlayerModeToggle(
        playerMode = uiState.playerMode,
        singleLabel = stringResource(R.string.label_single_player),
        doubleLabel = stringResource(R.string.label_double_player),
        selectorDescription = stringResource(R.string.cd_player_mode_selector),
        singleDescription = stringResource(R.string.cd_single_player),
        doubleDescription = stringResource(R.string.cd_double_player),
        modifier = Modifier
            .width(toggleWidth)
            .height((toggleWidth / 3.4f).coerceAtLeast(48.dp)),
        onPlayerModeSelected = onPlayerModeSelected,
    )

    Spacer(modifier = Modifier.height(betweenControls))

    if (uiState.playerMode == PlayerMode.DOUBLE) {
        FriendSecretSlot(
            secret = uiState.friendSecretNumber,
            placeholder = stringResource(R.string.friend_secret_hint),
            onClick = onSecretSlotClick,
            modifier = Modifier
                .width(toggleWidth)
                .height(inputHeight),
        )

        if (uiState.secretValidationMessage != null) {
            Text(
                text = uiState.secretValidationMessage,
                modifier = Modifier
                    .width(toggleWidth)
                    .padding(top = 6.dp),
                color = NumbCrunchGoldCream,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(controlSpacing))
    }

    DifficultySelector(
        difficulty = uiState.difficulty,
        onDifficultySelected = onDifficultySelected,
    )

    Spacer(modifier = Modifier.height(controlSpacing))

    HomePrimaryButton(
        text = stringResource(R.string.label_start),
        contentDescription = stringResource(R.string.cd_start_game),
        modifier = Modifier.width(startWidth),
        onClick = onStartClick,
    )

    Spacer(modifier = Modifier.height(controlSpacing))

    HomeSecondaryButton(
        text = stringResource(R.string.label_tutorial),
        contentDescription = stringResource(R.string.cd_tutorial),
        modifier = Modifier.width(secondaryWidth),
        onClick = onTutorialClick,
    )

    if (GAMEPLAY_GUIDE_ENABLED) {
        Spacer(modifier = Modifier.height(controlSpacing))

        HomeSecondaryButton(
            text = stringResource(R.string.label_gameplay),
            contentDescription = stringResource(R.string.cd_gameplay),
            modifier = Modifier.width(secondaryWidth),
            onClick = onGameplayClick,
        )
    }

    Spacer(modifier = Modifier.height(controlSpacing))

    HomeSecondaryButton(
        text = stringResource(R.string.label_about),
        contentDescription = stringResource(R.string.cd_about),
        modifier = Modifier.width(secondaryWidth),
        onClick = onAboutClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultySelector(
    difficulty: Difficulty,
    onDifficultySelected: (Difficulty) -> Unit,
) {
    val feedback = rememberTapFeedback()
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = NumbCrunchGoldBorder,
        activeContentColor = NumbCrunchChocolateDark,
        activeBorderColor = NumbCrunchGoldBorder,
        inactiveContainerColor = NumbCrunchChocolateDark,
        inactiveContentColor = NumbCrunchGoldCream,
        inactiveBorderColor = NumbCrunchGoldBorder,
    )
    Column(
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.difficulty_label),
            color = NumbCrunchGoldCream,
            style = MaterialTheme.typography.labelLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Difficulty.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = difficulty == option,
                    onClick = {
                        if (difficulty != option) {
                            feedback()
                            onDifficultySelected(option)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, Difficulty.entries.size),
                    colors = colors,
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        Text(
            text = stringResource(
                when (difficulty) {
                    Difficulty.EASY -> R.string.difficulty_easy_description
                    Difficulty.MEDIUM -> R.string.difficulty_medium_description
                    Difficulty.HARD -> R.string.difficulty_hard_description
                },
            ),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = NumbCrunchGoldCream,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(showBackground = true, name = "Single mode")
@Composable
private fun HomeScreenSinglePreview() {
    NumbCrunchTheme {
        HomeScreen(
            uiState = HomeUiState(playerMode = PlayerMode.SINGLE),
            onPlayerModeSelected = {},
            onDifficultySelected = {},
            onFriendSecretDigit = {},
            onFriendSecretDelete = {},
            onStartClick = {},
            onTutorialClick = {},
            onGameplayClick = {},
            onAboutClick = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Double mode")
@Composable
private fun HomeScreenDoublePreview() {
    NumbCrunchTheme {
        HomeScreen(
            uiState = HomeUiState(
                playerMode = PlayerMode.DOUBLE,
                friendSecretNumber = "258",
                secretValidationMessage = "Digits must be unique",
            ),
            onPlayerModeSelected = {},
            onDifficultySelected = {},
            onFriendSecretDigit = {},
            onFriendSecretDelete = {},
            onStartClick = {},
            onTutorialClick = {},
            onGameplayClick = {},
            onAboutClick = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 568, name = "Small phone")
@Composable
private fun HomeScreenSmallPreview() {
    NumbCrunchTheme {
        HomeScreen(
            uiState = HomeUiState(playerMode = PlayerMode.DOUBLE, friendSecretNumber = ""),
            onPlayerModeSelected = {},
            onDifficultySelected = {},
            onFriendSecretDigit = {},
            onFriendSecretDelete = {},
            onStartClick = {},
            onTutorialClick = {},
            onGameplayClick = {},
            onAboutClick = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280, name = "Tablet")
@Composable
private fun HomeScreenTabletPreview() {
    NumbCrunchTheme {
        HomeScreen(
            uiState = HomeUiState(playerMode = PlayerMode.SINGLE),
            onPlayerModeSelected = {},
            onDifficultySelected = {},
            onFriendSecretDigit = {},
            onFriendSecretDelete = {},
            onStartClick = {},
            onTutorialClick = {},
            onGameplayClick = {},
            onAboutClick = {},
            onSettingsClick = {},
        )
    }
}
