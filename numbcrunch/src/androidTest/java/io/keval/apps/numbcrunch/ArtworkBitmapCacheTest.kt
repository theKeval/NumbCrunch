package io.keval.apps.numbcrunch

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.artwork.ArtworkBitmapCache
import io.keval.apps.numbcrunch.gameplay.R
import org.junit.Assert.*
import org.junit.Test

class ArtworkBitmapCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun allBundledArtworkStaysSharedAcrossRepeatedDestinationLoads() {
        val cache = ArtworkBitmapCache.Shared
        val ids = listOf(R.drawable.home_background, R.drawable.home_title,
            R.drawable.game_board_wood, R.drawable.game_keypad_wood, R.drawable.play_games_controller)
        val first = ids.associateWith { cache.load(context.resources, it) }
        repeat(8) {
            ids.forEach { id -> assertSame(first.getValue(id), cache.load(context.resources, id)) }
        }
        assertTrue(first.values.sumOf { it.allocationByteCount.toLong() } < 16L * 1024 * 1024)
    }

    @Test fun unchangedNodpiArtworkKeepsItsPixelsAcrossDensityAndConfigurationChanges() {
        val cache = ArtworkBitmapCache.Shared
        val first = cache.load(context.resources, R.drawable.play_games_controller)
        val changed = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            densityDpi = if (densityDpi == 160) 320 else 160
            orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        })
        val second = cache.load(changed.resources, R.drawable.play_games_controller)
        assertSame(first, second)
        assertEquals(512, second.width)
        assertEquals(512, second.height)
    }

    @Test fun byteLimitEvictsWithoutRecyclingAnImageStillOwnedByAPainter() {
        val cache = ArtworkBitmapCache(maxBytes = 1)
        val first = cache.load(context.resources, R.drawable.play_games_controller)
        val second = cache.load(context.resources, R.drawable.play_games_controller)
        assertNotSame(first, second)
        assertFalse(first.isRecycled)
        assertEquals(first.getPixel(0, 0), second.getPixel(0, 0))
    }
}
