package io.keval.apps.numbcrunch.gameplay.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchChocolateDark
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchOrangeTop
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchBronzeDark
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldBorder
import io.keval.apps.numbcrunch.domain.game.GuessEvaluation
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.audio.rememberTapFeedback
import io.keval.apps.numbcrunch.gameplay.game.DigitTile
import io.keval.apps.numbcrunch.gameplay.game.GameRoundIconButton
import io.keval.apps.numbcrunch.gameplay.game.GoldFrameBrush
import io.keval.apps.numbcrunch.gameplay.game.RemarkBox
import io.keval.apps.numbcrunch.gameplay.game.TileState
import io.keval.apps.numbcrunch.gameplay.game.WoodPanel
import io.keval.apps.numbcrunch.gameplay.game.digitFeedbackDescription

@Composable
internal fun GuideScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fixedContent: Boolean = false,
    trailingAction: @Composable RowScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    NumbCrunchScreenScaffold(modifier) {
        Column(
            Modifier.fillMaxSize().background(NumbCrunchChocolateDark.copy(alpha = 0.3f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GameRoundIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.guide_back),
                    48.dp,
                    onClick = onBack,
                )
                GuideHeading(title, Modifier.weight(1f))
                trailingAction()
            }
            if (fixedContent) {
                Column(
                    Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
            } else {
                Column(
                    Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
            }
            footer()
        }
    }
}

@Composable
internal fun GuideSkipAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val feedback = rememberTapFeedback()
    TextButton(
        onClick = {
            feedback()
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(text, color = NumbCrunchGoldCream, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun GuidePageDots(
    count: Int,
    selectedIndex: Int,
    pageDescription: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = rememberTapFeedback()
    Row(
        modifier = modifier.semantics { selectableGroup() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == selectedIndex
            val description = pageDescription(index)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!selected) feedback()
                            onSelect(index)
                        },
                    )
                    .semantics {
                        contentDescription = description
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(if (selected) 14.dp else 12.dp).clip(CircleShape)) {
                    if (selected) {
                        drawCircle(color = NumbCrunchOrangeTop)
                        drawCircle(
                            color = NumbCrunchBronzeDark,
                            radius = size.minDimension * 0.5f,
                            center = Offset(size.width / 2, size.height / 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                        )
                    } else {
                        drawCircle(
                            color = NumbCrunchGoldCream.copy(alpha = 0.5f),
                            radius = size.minDimension * 0.5f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GuideHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.semantics { heading() }, color = NumbCrunchOrangeTop,
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp,
    )
}

private val ClueRightColor = Color(0xFF8EE06A)
private val ClueWrongColor = Color(0xFFF3C541)
private val ClueSmyleColor = Color(0xFF4FD1C5)
private val ClueSmyleMeaningColor = Color(0xFFFFD43B)
private val DetailsGoalColor = Color(0xFFFFD36B)
private val DetailsRuleColor = Color(0xFFFFB04D)
private val DetailsSingleColor = Color(0xFF7FD4FF)
private val DetailsDoubleColor = Color(0xFFC9A7FF)
private val DetailsStartColor = Color(0xFFF7A23B)

private fun bold(color: Color = Color.Unspecified) = SpanStyle(color = color, fontWeight = FontWeight.Bold)

private val ClueWordStyles = listOf(
    Regex("\\b(RIGHT|1R|2R)\\b") to bold(ClueRightColor),
    Regex("\\b(WRONG|1W|2W|3W)\\b") to bold(ClueWrongColor),
    Regex("\\bSMYLE\\b") to bold(ClueSmyleColor),
    Regex("\\bSimply Make Your Life Easy\\b") to bold(ClueSmyleMeaningColor),
)

private val GameDetailStyles = listOf(
    Regex("\\bhidden number\\b") to bold(DetailsGoalColor),
    Regex("\\b3 unique digits\\b|\\b0 to 9\\b|\\(no repeat\\)") to bold(DetailsRuleColor),
    Regex("\\bclue\\b") to bold(),
    Regex("\\bsingle player\\b", RegexOption.IGNORE_CASE) to bold(DetailsSingleColor),
    Regex("\\bdouble player\\b", RegexOption.IGNORE_CASE) to bold(DetailsDoubleColor),
    Regex("\\bstart\\b", RegexOption.IGNORE_CASE) to bold(DetailsStartColor),
    Regex("Swipe left for play details\\.?") to SpanStyle(fontStyle = FontStyle.Italic),
    Regex("\\bSwipe left\\b") to bold(DetailsGoalColor),
)

/** Adds [styles] to every whole-word match in [text]; the text itself is never changed. */
private fun highlight(text: String, styles: List<Pair<Regex, SpanStyle>>): AnnotatedString = buildAnnotatedString {
    append(text)
    styles.forEach { (pattern, style) ->
        pattern.findAll(text).forEach { match -> addStyle(style, match.range.first, match.range.last + 1) }
    }
}

/**
 * Makes the clue words stand out in bold: RIGHT, 1R and 2R in green; WRONG, 1W, 2W and 3W in
 * yellow; SMYLE in teal and "Simply Make Your Life Easy" in yellow. Only styling is added; the
 * text is unchanged.
 */
internal fun highlightClueWords(text: String): AnnotatedString = highlight(text, ClueWordStyles)

/**
 * Brings out the key ideas of the game introduction, all in bold: the hidden number in gold;
 * the code rules (3 unique digits, 0 to 9, no repeat) in amber; each mode in its own color;
 * start in Start-button orange; and the closing swipe hint in italics. Only styling is added;
 * the text is unchanged.
 */
internal fun highlightGameDetails(text: String): AnnotatedString = highlight(text, GameDetailStyles)

@Composable
internal fun GuideBody(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(text, modifier, color = NumbCrunchGoldCream, fontSize = 16.sp, lineHeight = 24.sp)
}

@Composable
internal fun GuideBody(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = NumbCrunchGoldCream, fontSize = 16.sp, lineHeight = 24.sp)
}

/**
 * One rule in a numbered list: a gold-framed number badge beside the rule text, on a softly
 * inset card. The pair is read as a single item by accessibility services.
 */
@Composable
internal fun GuideNumberedRule(number: Int, text: AnnotatedString, modifier: Modifier = Modifier) {
    val cardShape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .background(NumbCrunchChocolateDark.copy(alpha = 0.55f), cardShape)
            .border(1.dp, NumbCrunchGoldBorder.copy(alpha = 0.35f), cardShape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .squareAtLeast(34.dp)
                .shadow(4.dp, CircleShape, clip = false)
                .background(GoldFrameBrush, CircleShape)
                .padding(2.dp)
                .background(Brush.verticalGradient(listOf(NumbCrunchOrangeTop, NumbCrunchBronzeDark)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = NumbCrunchChocolateDark,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        GuideBody(text, Modifier.weight(1f))
    }
}

/** Sizes the element as a square that fits its content, so a circle stays round at any font scale. */
private fun Modifier.squareAtLeast(minSide: Dp): Modifier = layout { measurable, constraints ->
    val width = measurable.maxIntrinsicWidth(constraints.maxHeight)
    val height = measurable.maxIntrinsicHeight(width)
    val side = maxOf(width, height, minSide.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

@Composable
internal fun GuidePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    WoodPanel(R.drawable.game_board_wood, Color(0xE62A1A12), Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                GuideHeading(title)
                content()
            },
        )
    }
}

@Composable
internal fun GuideAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val feedback = rememberTapFeedback()
    Button(
        onClick = {
            feedback()
            onClick()
        },
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = NumbCrunchOrangeTop, contentColor = NumbCrunchChocolateDark,
            disabledContainerColor = NumbCrunchGoldCream.copy(alpha = 0.2f),
            disabledContentColor = NumbCrunchGoldCream.copy(alpha = 0.6f),
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/**
 * A scored guess row. With [colorFeedbackEnabled] false, it shows Hard's gray tiles and
 * announces only the guess and its remark.
 */
@Composable
internal fun GuideGuess(
    evaluation: GuessEvaluation,
    announce: Boolean = false,
    colorFeedbackEnabled: Boolean = true,
) {
    val description = if (colorFeedbackEnabled) {
        stringResource(
            R.string.guide_guess_description, evaluation.guess, evaluation.remark,
            evaluation.digits.mapIndexed { index, feedback ->
                digitFeedbackDescription(evaluation.guess[index], feedback)
            }.joinToString("; "),
        )
    } else {
        stringResource(R.string.guide_guess_remark_description, evaluation.guess, evaluation.remark)
    }
    // With colors on, a spoken clue for each digit conveys the same information as the tile colors.
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = description
            if (announce) liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            evaluation.guess.forEachIndexed { index, digit ->
                DigitTile(
                    TileState.Revealed(digit, evaluation.digits[index]),
                    56.dp,
                    false,
                    null,
                    colorFeedbackEnabled = colorFeedbackEnabled,
                )
            }
        }
        RemarkBox(evaluation.remark, evaluation.isWin, 52.dp, Modifier.fillMaxWidth())
    }
}
