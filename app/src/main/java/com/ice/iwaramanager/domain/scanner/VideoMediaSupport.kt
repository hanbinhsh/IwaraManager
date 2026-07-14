package com.ice.iwaramanager.domain.scanner

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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

    fun extractCover(
        retriever: MediaMetadataRetriever,
        coverFile: File
    ): String? = try {
        val bitmap = retriever.getFrameAtTime(
            1_000_000L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        ) ?: retriever.frameAtTime ?: return null
        FileOutputStream(coverFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        }
        bitmap.recycle()
        coverFile.absolutePath
    } catch (_: Exception) {
        null
    }
}
