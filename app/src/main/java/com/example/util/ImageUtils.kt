package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object ImageUtils {

    fun createCameraPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(dir, "haven_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 960,
        maxBase64Length: Int = 260_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

            var sampleSize = 1
            while (srcWidth / sampleSize > maxDimension * 2 || srcHeight / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val scaled = scaleBitmapDown(sampled, maxDimension)
            if (scaled !== sampled) sampled.recycle()

            var quality = 82
            var encoded: String
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                quality -= 12
            } while (encoded.length > maxBase64Length && quality >= 30)

            scaled.recycle()
            if (encoded.length > maxBase64Length) null else encoded
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = maxOf(width, height)
        if (maxSide <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val matrix = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    fun decodeBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}

fun formatRelativeTime(
    timestamp: Timestamp?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val date = timestamp?.toDate() ?: return "just now"
    val diffMs = (nowMillis - date.time).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)

    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days == 1L -> "yesterday"
        days < 7L -> "${days}d ago"
        days < 28L -> "${days / 7L}w ago"
        else -> {
            val postCal = Calendar.getInstance().apply { time = date }
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val pattern = if (postCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
                "MMM d"
            } else {
                "MMM d, yyyy"
            }
            SimpleDateFormat(pattern, Locale.US).format(date)
        }
    }
}
