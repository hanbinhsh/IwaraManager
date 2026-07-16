package com.ice.iwaramanager.domain.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import kotlin.math.absoluteValue

object VideoMediaSupport {
    val extensions = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "wmv")
    private val unsafeNameChars = Regex("""[^\w.\-]+""")

    fun isVideoFile(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in extensions
    }

    fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "wmv" -> "video/x-ms-wmv"
        else -> "video/*"
    }

    fun joinPath(parent: String, name: String): String {
        return if (parent.isBlank()) name else "$parent/$name"
    }

    fun coverFile(context: Context, key: String, displayName: String): File {
        val coverDir = File(context.filesDir, "video_covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val safeName = displayName.replace(unsafeNameChars, "_").take(80)
        return File(coverDir, "${key.hashCode().absoluteValue}_${safeName}.jpg")
    }

    fun usableCoverPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            File(path).takeIf { it.isFile && it.length() > 0L }?.absolutePath
        }.getOrNull()
    }

    fun extractCover(
        retriever: MediaMetadataRetriever,
        coverFile: File
    ): String? = try {
        val embedded = retriever.embeddedPicture?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val bitmap = embedded
            ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.frameAtTime
            ?: return null
        writeCover(bitmap, coverFile)
    } catch (_: Exception) {
        null
    }

    fun extractContentCover(
        context: Context,
        uri: Uri,
        coverFile: File
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val bitmap = context.contentResolver.loadThumbnail(
                uri,
                Size(640, 360),
                null
            )
            writeCover(bitmap, coverFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCover(bitmap: Bitmap, coverFile: File): String? {
        return try {
            FileOutputStream(coverFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output))
            }
            coverFile.absolutePath
        } catch (_: Exception) {
            coverFile.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }
}
