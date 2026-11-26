package com.bookintok.api.image

import com.bookintok.api.common.BusinessRuleException
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Normalises uploaded photos: decodes them (rejecting anything that is not a real image),
 * scales them down and re-encodes them as JPEG.
 *
 * Re-encoding also drops all metadata, including EXIF GPS coordinates that phones add to photos:
 * without it, a book photo could reveal where its owner lives.
 */
object ImageProcessor {
    const val MAX_SIDE = 1280
    private const val QUALITY = 0.85f

    fun normalize(bytes: ByteArray): ByteArray {
        val source = try {
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            null
        } ?: throw BusinessRuleException("The file is not a supported image (JPEG or PNG)")

        val scale = minOf(1.0, MAX_SIDE.toDouble() / max(source.width, source.height))
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)

        // Drawing onto an RGB canvas also flattens PNG transparency, which JPEG cannot store
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            color = java.awt.Color.WHITE
            fillRect(0, 0, width, height)
            drawImage(source, 0, 0, width, height, null)
            dispose()
        }
        return encodeJpeg(target)
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(output).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = QUALITY
            }
            writer.write(null, IIOImage(image, null, null), params)
            writer.dispose()
        }
        return output.toByteArray()
    }
}
