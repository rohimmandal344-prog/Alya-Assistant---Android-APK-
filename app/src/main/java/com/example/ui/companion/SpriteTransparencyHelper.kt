package com.example.ui.companion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object SpriteTransparencyHelper {
    private val composeCache = mutableMapOf<Int, ImageBitmap>()
    private val nativeCache = mutableMapOf<Int, Bitmap>()

    /**
     * Converts a near-white background JPG into a transparent Bitmap for native Android views.
     */
    @Synchronized
    fun getTransparentBitmap(context: Context, drawableId: Int): Bitmap {
        val cached = nativeCache[drawableId]
        if (cached != null) return cached

        try {
            val options = BitmapFactory.Options().apply {
                inMutable = true
            }
            val original = BitmapFactory.decodeResource(context.resources, drawableId, options)
                ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            val width = original.width
            val height = original.height
            val pixels = IntArray(width * height)
            original.getPixels(pixels, 0, width, 0, 0, width, height)

            val visited = java.util.BitSet(width * height)
            val queue = IntArray(width * height)
            var head = 0
            var tail = 0

            fun isNearWhite(color: Int): Boolean {
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                return r > 190 && g > 190 && b > 190
            }

            // Seed border pixels
            for (x in 0 until width) {
                val idxTop = x
                if (isNearWhite(pixels[idxTop])) {
                    queue[tail++] = idxTop
                    visited.set(idxTop)
                }
                val idxBot = (height - 1) * width + x
                if (isNearWhite(pixels[idxBot])) {
                    queue[tail++] = idxBot
                    visited.set(idxBot)
                }
            }
            for (y in 1 until height - 1) {
                val idxLeft = y * width
                if (isNearWhite(pixels[idxLeft])) {
                    queue[tail++] = idxLeft
                    visited.set(idxLeft)
                }
                val idxRight = y * width + (width - 1)
                if (isNearWhite(pixels[idxRight])) {
                    queue[tail++] = idxRight
                    visited.set(idxRight)
                }
            }

            // Perform BFS to clear background near-white pixels
            while (head < tail) {
                val currIdx = queue[head++]
                pixels[currIdx] = Color.TRANSPARENT

                val cx = currIdx % width
                val cy = currIdx / width

                if (cx > 0) {
                    val nIdx = currIdx - 1
                    if (!visited.get(nIdx) && isNearWhite(pixels[nIdx])) {
                        visited.set(nIdx)
                        queue[tail++] = nIdx
                    }
                }
                if (cx < width - 1) {
                    val nIdx = currIdx + 1
                    if (!visited.get(nIdx) && isNearWhite(pixels[nIdx])) {
                        visited.set(nIdx)
                        queue[tail++] = nIdx
                    }
                }
                if (cy > 0) {
                    val nIdx = currIdx - width
                    if (!visited.get(nIdx) && isNearWhite(pixels[nIdx])) {
                        visited.set(nIdx)
                        queue[tail++] = nIdx
                    }
                }
                if (cy < height - 1) {
                    val nIdx = currIdx + width
                    if (!visited.get(nIdx) && isNearWhite(pixels[nIdx])) {
                        visited.set(nIdx)
                        queue[tail++] = nIdx
                    }
                }
            }

            val transparentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            transparentBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            if (original != transparentBitmap) {
                original.recycle()
            }
            
            nativeCache[drawableId] = transparentBitmap
            return transparentBitmap
        } catch (e: Exception) {
            com.example.core.logger.AlyaLogger.e("ALYA_TRANSPARENCY", "Error filtering background:", e)
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    /**
     * Converts a near-white background JPG into a transparent ImageBitmap for Jetpack Compose.
     */
    @Synchronized
    fun getTransparentImageBitmap(context: Context, drawableId: Int): ImageBitmap {
        val cached = composeCache[drawableId]
        if (cached != null) return cached

        val bitmap = getTransparentBitmap(context, drawableId)
        val imageBitmap = bitmap.asImageBitmap()
        composeCache[drawableId] = imageBitmap
        return imageBitmap
    }
}
