package com.ice.iwaramanager.domain.scanner

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.absoluteValue

class VideoScanner(
    private val context: Context
) {
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "m4v")

    suspend fun scan(treeUri: Uri, onProgress: (ScanProgress) -> Unit = {}): List<ScannedVideo> {
        return scanLibrary(treeUri, onProgress).videos
    }

    suspend fun scanLibrary(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {}
    ): ScannedLibrary = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext ScannedLibrary(emptyList(), emptyList())
        val videoFiles = mutableListOf<Pair<DocumentFile, String>>()
        val folders = mutableListOf<ScannedFolder>()

        fun walk(dir: DocumentFile, relativeDir: String) {
            val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
            for (child in children) {
                val name = child.name ?: continue
                val childPath = joinPath(relativeDir, name)
                if (child.isDirectory) {
                    folders += ScannedFolder(
                        path = childPath,
                        parentPath = relativeDir.ifBlank { null },
                        name = name
                    )
                    walk(child, childPath)
                    continue
                }
                val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                if (ext in videoExtensions) {
                    videoFiles += child to relativeDir
                }
            }
        }

        walk(root, "")
        val sortedFiles = videoFiles.sortedWith { a, b -> naturalCompare(a.first.name.orEmpty(), b.first.name.orEmpty()) }
        onProgress(ScanProgress(done = 0, total = sortedFiles.size, currentName = null))

        val result = mutableListOf<ScannedVideo>()
        sortedFiles.forEachIndexed { index, (file, parentPath) ->
            val name = file.name.orEmpty()
            onProgress(ScanProgress(done = index, total = sortedFiles.size, currentName = name))
            val metadata = readVideoMetadata(file.uri)
            val coverPath = extractCoverToCache(videoUri = file.uri, videoName = name)
            result += ScannedVideo(
                displayName = name,
                uriString = file.uri.toString(),
                fileSize = file.length(),
                lastModified = file.lastModified(),
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                coverFilePath = coverPath,
                relativePath = joinPath(parentPath, name),
                parentPath = parentPath.ifBlank { null }
            )
            onProgress(ScanProgress(done = index + 1, total = sortedFiles.size, currentName = name))
        }

        ScannedLibrary(videos = result, folders = folders.distinctBy { it.path })
    }

    private fun readVideoMetadata(uri: Uri): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            VideoMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            )
        } catch (_: Exception) {
            VideoMetadata(null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractCoverToCache(videoUri: Uri, videoName: String): String? {
        val coverDir = File(context.cacheDir, "video_covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val safeName = videoName.replace(Regex("""[^\w.\-]+"""), "_").take(80)
        val hash = videoUri.toString().hashCode().absoluteValue
        val coverFile = File(coverDir, "${hash}_${safeName}.jpg")
        if (coverFile.exists() && coverFile.length() > 0L) return coverFile.absolutePath

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime ?: return null
            FileOutputStream(coverFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out) }
            bitmap.recycle()
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun joinPath(parent: String, name: String): String {
        return if (parent.isBlank()) name else "$parent/$name"
    }

    private data class VideoMetadata(val durationMs: Long?, val width: Int?, val height: Int?)

    private fun naturalCompare(a: String, b: String): Int = a.lowercase().compareTo(b.lowercase())
}
