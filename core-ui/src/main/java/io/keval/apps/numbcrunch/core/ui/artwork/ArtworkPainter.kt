package io.keval.apps.numbcrunch.core.ui.artwork

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Shares raster pixels across destinations without retaining their Activity or Resources. */
class ArtworkBitmapCache(maxBytes: Int = 16 * 1024 * 1024) {
    companion object { val Shared = ArtworkBitmapCache() }

    private data class Key(val packageName: String, val cookie: Int, val path: String,
        val sourceDensity: Int, val targetDensity: Int)

    private val bitmaps = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
        // Eviction drops our reference; a painter may still be drawing that bitmap.
    }

    @Synchronized
    fun load(resources: Resources, @DrawableRes id: Int): Bitmap {
        val value = TypedValue().also { resources.getValue(id, it, true) }
        val path = requireNotNull(value.string) { "Artwork must be a raster resource" }.toString()
        // Android resolves locale/night/orientation variants first; nodpi pixels never scale.
        val key = Key(resources.getResourcePackageName(id), value.assetCookie, path, value.density,
            if (value.density == TypedValue.DENSITY_NONE) 0 else resources.displayMetrics.densityDpi)
        return bitmaps.get(key) ?: requireNotNull(BitmapFactory.decodeResource(resources, id)) {
            "Artwork must be a raster resource"
        }.also { bitmaps.put(key, it) }
    }
}

/** Use for bundled PNG artwork; vector resources should still use painterResource. */
@Composable
fun rememberArtworkPainter(@DrawableRes id: Int): Painter {
    val resources = LocalContext.current.resources
    val configuration = Configuration(LocalConfiguration.current)
    val bitmap = remember(resources, configuration, id) { ArtworkBitmapCache.Shared.load(resources, id) }
    return remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
}
