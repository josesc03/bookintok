package com.bookintok.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Prepares a photo picked from the gallery for upload: scaled down to 1280 px and re-encoded as
 * JPEG on the device, so uploads are small. Re-encoding also leaves the photo's metadata
 * (including GPS position) behind; the server strips it again in any case.
 */
@Singleton
class ImageCompressor @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun compress(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = decode(uri)
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
            output.toByteArray()
        }
    }

    private fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF rotation and can downsample while decoding
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE * 2) decoder.setTargetSampleSize(longest / MAX_SIDE)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = max(1, max(bounds.outWidth, bounds.outHeight) / MAX_SIDE)
            }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Cannot read the image")
        }

    private companion object {
        const val MAX_SIDE = 1280
        const val QUALITY = 85
    }
}
