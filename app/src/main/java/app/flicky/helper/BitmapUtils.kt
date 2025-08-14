package app.flicky.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

/**
 * Utilities for bitmap related operations
 */
object BitmapUtils {

    /**
     * Convert a drawable to a bitmap
     * @param drawable The drawable to convert
     * @param defaultSize The default size to use if intrinsic dimensions are invalid
     * @return The converted bitmap, or null if conversion fails
     */
    fun drawableToBitmap(
        drawable: Drawable?,
        defaultSize: Int = 48
    ): Bitmap? {
        if (drawable == null) return null

        return try {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: defaultSize
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: defaultSize

            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)

            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}