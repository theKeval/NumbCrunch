package io.keval.apps.numbcrunch.gameplay.game

import io.keval.apps.numbcrunch.domain.game.DigitFeedback
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class DigitTileBrushTest {

    private val neutralBrush = digitTileBrush(TileState.Empty, showFront = true, colorFeedbackEnabled = true)
    private val grayBrush = digitTileBrush(
        TileState.Revealed('3', DigitFeedback.ABSENT),
        showFront = false,
        colorFeedbackEnabled = false,
    )

    @Test
    fun `disabled color feedback keeps unanalysed tiles neutral and analysed tiles gray`() {
        listOf(TileState.Empty, TileState.Pending('2')).forEach { state ->
            listOf(true, false).forEach { showFront ->
                assertSame(neutralBrush, digitTileBrush(state, showFront, colorFeedbackEnabled = false))
            }
        }
        DigitFeedback.entries.forEach { feedback ->
            val state = TileState.Revealed('5', feedback)
            assertSame(neutralBrush, digitTileBrush(state, showFront = true, colorFeedbackEnabled = false))
            assertSame(grayBrush, digitTileBrush(state, showFront = false, colorFeedbackEnabled = false))
        }
    }

    @Test
    fun `enabled color feedback preserves neutral fronts and distinct revealed colors`() {
        assertNotSame(neutralBrush, grayBrush)
        assertSame(
            neutralBrush,
            digitTileBrush(TileState.Pending('2'), showFront = true, colorFeedbackEnabled = true),
        )
        val revealedBrushes = DigitFeedback.entries.map { feedback ->
            val state = TileState.Revealed('5', feedback)
            assertSame(neutralBrush, digitTileBrush(state, showFront = true, colorFeedbackEnabled = true))
            digitTileBrush(state, showFront = false, colorFeedbackEnabled = true).also {
                assertNotSame(neutralBrush, it)
                assertNotSame(grayBrush, it)
            }
        }
        assertNotSame(revealedBrushes[0], revealedBrushes[1])
        assertNotSame(revealedBrushes[0], revealedBrushes[2])
        assertNotSame(revealedBrushes[1], revealedBrushes[2])
    }
}
