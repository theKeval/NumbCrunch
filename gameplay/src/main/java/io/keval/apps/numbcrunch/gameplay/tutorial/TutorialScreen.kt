package io.keval.apps.numbcrunch.gameplay.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchOrangeTop
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.game.GuessRules
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.common.FitToHeight
import io.keval.apps.numbcrunch.gameplay.common.GuideAction
import io.keval.apps.numbcrunch.gameplay.common.GuideBody
import io.keval.apps.numbcrunch.gameplay.common.GuideGuess
import io.keval.apps.numbcrunch.gameplay.common.GuideNumberedRule
import io.keval.apps.numbcrunch.gameplay.common.GuidePageDots
import io.keval.apps.numbcrunch.gameplay.common.GuidePanel
import io.keval.apps.numbcrunch.gameplay.common.GuideScaffold
import io.keval.apps.numbcrunch.gameplay.common.GuideSkipAction
import io.keval.apps.numbcrunch.gameplay.common.highlightClueWords
import io.keval.apps.numbcrunch.gameplay.common.highlightGameDetails
import io.keval.apps.numbcrunch.gameplay.game.DigitTile
import io.keval.apps.numbcrunch.gameplay.game.KeyStyle
import io.keval.apps.numbcrunch.gameplay.game.KeypadKey
import io.keval.apps.numbcrunch.gameplay.game.TileState
import io.keval.apps.numbcrunch.gameplay.info.LearningExamples
import kotlinx.coroutines.launch

private const val TUTORIAL_PAGE_COUNT = 3
private const val CLUES_PAGE = 1

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun TutorialScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstRun: Boolean = false,
    onCompleteFirstRun: () -> Unit = onBack,
    isCompletingFirstRun: Boolean = false,
    firstRunCompletionError: Boolean = false,
) {
    val pagerState = rememberPagerState(pageCount = { TUTORIAL_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val page by remember { derivedStateOf { pagerState.currentPage } }
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val evaluation = remember(submitted) {
        submitted.takeIf(GuessRules::isValidCode)?.let { GuessRules.evaluate(LearningExamples.SECRET, it) }
    }

    val back: () -> Unit = {
        if (page > 0) {
            scope.launch { pagerState.scrollToPage(page - 1) }
            Unit
        } else if (isFirstRun) {
            onCompleteFirstRun()
        } else {
            onBack()
        }
    }
    BackHandler(onBack = back)
    // The header back button leaves the tutorial from any page (first run counts as done, like
    // Skip); the footer BACK button and system Back step through the pages.
    val exit: () -> Unit = { if (isFirstRun) onCompleteFirstRun() else onBack() }

    GuideScaffold(
        title = stringResource(R.string.label_tutorial),
        onBack = exit,
        modifier = modifier,
        fixedContent = true,
        trailingAction = {
            if (isFirstRun) {
                GuideSkipAction(
                    text = stringResource(R.string.tutorial_skip),
                    onClick = onCompleteFirstRun,
                    enabled = !isCompletingFirstRun,
                )
            }
        },
        footer = {
            if (firstRunCompletionError) {
                GuideBody(
                    stringResource(R.string.tutorial_completion_error),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            GuideBody(
                stringResource(R.string.tutorial_progress, page + 1, TUTORIAL_PAGE_COUNT),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            GuidePageDots(
                count = TUTORIAL_PAGE_COUNT,
                selectedIndex = page,
                pageDescription = { stringResource(R.string.tutorial_dot_description, it + 1, TUTORIAL_PAGE_COUNT) },
                onSelect = { index -> scope.launch { pagerState.scrollToPage(index) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (page > 0) {
                    GuideAction(
                        stringResource(R.string.tutorial_back),
                        { scope.launch { pagerState.scrollToPage(page - 1) } },
                        Modifier.weight(1f),
                        enabled = !isCompletingFirstRun,
                    )
                }
                val isLastPage = page == TUTORIAL_PAGE_COUNT - 1
                val actionText = when {
                    !isLastPage -> R.string.tutorial_next
                    isFirstRun -> R.string.tutorial_get_started
                    else -> R.string.tutorial_play
                }
                GuideAction(
                    stringResource(actionText),
                    onClick = {
                        if (isLastPage) {
                            if (isFirstRun) onCompleteFirstRun() else onPlay()
                        } else {
                            scope.launch { pagerState.scrollToPage(page + 1) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isCompletingFirstRun,
                )
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it },
        ) { pageIndex ->
            val pageContent: @Composable ColumnScope.() -> Unit = {
                TutorialPage(
                    pageIndex,
                    input,
                    onInputChange = { input = it },
                    onSubmit = {
                        submitted = input
                        input = ""
                    },
                    evaluation = evaluation,
                )
            }
            if (pageIndex == CLUES_PAGE) {
                // The clue rules are read-only, so they zoom to fit the screen instead of scrolling.
                FitToHeight(Modifier.fillMaxSize(), content = pageContent)
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = pageContent,
                )
            }
        }
    }
}

@Composable
private fun TutorialPage(
    page: Int,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    evaluation: io.keval.apps.numbcrunch.domain.game.GuessEvaluation?,
) {
    when (page) {
        0 -> GuidePanel(stringResource(R.string.tutorial_goal_title)) {
            GuideBody(highlightGameDetails(stringResource(R.string.tutorial_goal_body)))
//            Row(
//                Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
//            ) {
//                LearningExamples.SECRET.forEach {
//                    DigitTile(TileState.Pending(it), 64.dp, false, null)
//                }
//            }
//            GuideBody(stringResource(R.string.rules_code_body))
        }
        CLUES_PAGE -> GuidePanel(stringResource(R.string.tutorial_clues_title)) {
            GuideBody(stringResource(R.string.rules_clues_title_main_1))
            GuideNumberedRule(1, highlightClueWords(stringResource(R.string.rules_clues_title_main_2)))
            GuideNumberedRule(2, highlightClueWords(stringResource(R.string.rules_clues_title_main_3)))
            GuideNumberedRule(3, highlightClueWords(stringResource(R.string.rules_clues_title_main_4)))
//            GuideBody(stringResource(R.string.rules_clues_body))
//            GuideGuess(LearningExamples.evaluations.first())
//            GuideBody(stringResource(R.string.example_mixed))
//            GuideBody(stringResource(R.string.tutorial_deduction))
        }
        2 -> GuidePanel(stringResource(R.string.tutorial_practice_title)) {
            GuideBody(stringResource(R.string.tutorial_practice_body, LearningExamples.SECRET))
            val inputDescription = stringResource(R.string.tutorial_input_description, input, input.length)
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = inputDescription },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(3) { index ->
                    DigitTile(
                        input.getOrNull(index)?.let { TileState.Pending(it) } ?: TileState.Empty,
                        56.dp, index == input.length, null,
                    )
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val rows = if (maxWidth < 272.dp) listOf("123", "456", "789", " 0 ") else listOf("12345", "67890")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { digits ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            digits.forEach { digit ->
                                if (digit == ' ') {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    KeypadKey(
                                        digit.toString(),
                                        stringResource(R.string.cd_key_digit, digit.toString()),
                                        KeyStyle.Digit,
                                        input.length < 3 && digit !in input,
                                        48.dp,
                                        Modifier.weight(1f),
                                    ) { onInputChange(input + digit) }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideAction(
                    stringResource(R.string.key_delete),
                    { onInputChange(input.dropLast(1)) },
                    Modifier.weight(1f),
                    input.isNotEmpty(),
                )
                GuideAction(
                    stringResource(R.string.key_enter),
                    onSubmit,
                    Modifier.weight(1f),
                    GuessRules.isValidCode(input),
                )
            }
            if (evaluation != null) {
                // Practice uses Hard's gray tiles and remarks.
                GuideGuess(evaluation, announce = true, colorFeedbackEnabled = false)
                GuideBody(
                    stringResource(if (evaluation.isWin) R.string.tutorial_practice_win else R.string.tutorial_practice_retry),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "Compact phone", widthDp = 320, heightDp = 568)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Tablet", widthDp = 800, heightDp = 1280)
@Composable
private fun TutorialPreview() {
    NumbCrunchTheme { TutorialScreen(onBack = {}, onPlay = {}) }
}
