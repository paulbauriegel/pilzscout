package de.pilzscout.app.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt

/**
 * Reproduces the training-time evaluation transform of the fine-tuned model:
 * resize so the shorter edge equals [evalResize] (366), then center-crop [inputSize] (320).
 * The exported graph expects NHWC float32 RGB in the 0..255 range (ImageNet normalisation is baked in).
 */
class ImagePreprocessor(private val inputSize: Int, private val evalResize: Int) {

    /** Decodes with EXIF rotation applied and bounded memory, then returns the model input tensor. */
    fun prepare(file: File): FloatArray = prepare(decodeOriented(file, minShortEdge = evalResize))

    fun prepare(src: Bitmap): FloatArray {
        val scale = evalResize.toFloat() / minOf(src.width, src.height)
        val w = (src.width * scale).roundToInt().coerceAtLeast(inputSize)
        val h = (src.height * scale).roundToInt().coerceAtLeast(inputSize)
        val scaled = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        val left = (w - inputSize) / 2
        val top = (h - inputSize) / 2
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, left, top, inputSize, inputSize)
        if (scaled !== src) scaled.recycle()
        val out = FloatArray(inputSize * inputSize * 3)
        var o = 0
        for (p in pixels) {
            out[o++] = ((p shr 16) and 0xFF).toFloat()
            out[o++] = ((p shr 8) and 0xFF).toFloat()
            out[o++] = (p and 0xFF).toFloat()
        }
        return out
    }

    companion object {
        /** Decodes a JPEG/PNG file with inSampleSize chosen so the shorter edge stays >= minShortEdge, EXIF-rotated. */
        fun decodeOriented(file: File, minShortEdge: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minShortEdge) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmap = BitmapFactory.decodeFile(file.path, opts) ?: error("Could not decode ${file.name}")
            val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        }
    }
}
