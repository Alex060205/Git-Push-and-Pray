package com.example.vitala.Recognition


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes a camera photo so its longest side is at most [maxSide] px and
 * rotates it upright using the EXIF tag (many cameras save sideways JPEGs
 * and only mark the rotation in EXIF).
 *
 * Decoding a 12+ MP JPEG is blocking work, so it runs on Dispatchers.IO.
 */
suspend fun loadScaledBitmap(file: File, maxSide: Int = 2000): Bitmap =
    withContext(Dispatchers.IO) {
        // 1. Read dimensions only, without allocating the full image.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read photo" }

        // 2. Decode at a power-of-two reduction that stays >= maxSide.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Could not decode photo")

        // 3. Exact scale to maxSide, then fix rotation.
        rotateUpright(scaleDown(decoded, maxSide), file)
    }

private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= maxSide) return bitmap
    val ratio = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).roundToInt(),
        (bitmap.height * ratio).roundToInt(),
        true
    )
}

private fun rotateUpright(bitmap: Bitmap, file: File): Bitmap {
    val degrees = when (
        ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Renders the first page of a PDF file to a Bitmap.
 * Returns a Pair of (Bitmap, totalPageCount).
 */
suspend fun renderPdfFirstPage(file: File): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            val pageCount = renderer.pageCount
            require(pageCount > 0) { "PDF has no pages" }
            renderer.openPage(0).use { page ->
                // Page size is in points (1/72"); render at 2x so text is legible.
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                // PDF pages are transparent; without a white fill, black text is
                // invisible against a dark theme.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                Pair(bitmap, pageCount)
            }
        }
    }
}

/**
 * Upright, downscaled JPEG bytes for sending to Gemini. The bitmap is freed
 * right away so several pages never sit in memory at full size together.
 */
suspend fun loadAsJpeg(file: File, maxSide: Int = 2000, quality: Int = 90): ByteArray {
    val bitmap = loadScaledBitmap(file, maxSide)
    return withContext(Dispatchers.Default) {
        try {
            java.io.ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                .toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
