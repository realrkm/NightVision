package com.nightvision.app.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves rendered night-vision bitmaps as JPEGs into the device's Pictures/NightVision
 * collection via MediaStore (API 29+ scoped storage, with API 26-28 legacy fallback).
 */
object GallerySaver {

    private const val ALBUM_NAME = "NightVision"
    private const val MIME_TYPE_JPEG = "image/jpeg"

    /**
     * Compresses [bitmap] as JPEG and inserts it into [MediaStore.Images.Media.EXTERNAL_CONTENT_URI].
     *
     * @return [Result.success] with the created MediaStore [Uri], or [Result.failure] on I/O error.
     */
    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        quality: Int = 95
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val displayName = "NVG_$timestamp.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveMediaStoreQPlus(context, bitmap, displayName, quality)
            } else {
                saveLegacyExternal(context, bitmap, displayName, quality)
            }
        }
    }

    private fun saveMediaStoreQPlus(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        quality: Int
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_JPEG)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Failed to create MediaStore entry for $displayName")

        try {
            val outputStream = resolver.openOutputStream(uri)
                ?: throw IOException("Failed to open output stream for $uri")
            outputStream.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IOException("Bitmap JPEG compression failed")
                }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacyExternal(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        quality: Int
    ): Uri {
        val picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val albumDir = File(picturesDir, ALBUM_NAME)
        if (!albumDir.exists() && !albumDir.mkdirs()) {
            throw IOException("Unable to create directory: ${albumDir.absolutePath}")
        }

        val imageFile = File(albumDir, displayName)
        FileOutputStream(imageFile).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                throw IOException("Bitmap JPEG compression failed")
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_JPEG)
            put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )

        MediaScannerConnection.scanFile(
            context,
            arrayOf(imageFile.absolutePath),
            arrayOf(MIME_TYPE_JPEG),
            null
        )

        return uri ?: Uri.fromFile(imageFile)
    }
}
