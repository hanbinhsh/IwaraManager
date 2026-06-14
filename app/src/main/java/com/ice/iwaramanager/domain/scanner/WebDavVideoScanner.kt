package com.ice.iwaramanager.domain.scanner

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.ice.iwaramanager.data.model.LibrarySource
import com.ice.iwaramanager.data.model.RemoteIndexMode
import com.ice.iwaramanager.data.remote.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.absoluteValue

class WebDavVideoScanner(
    private val context: Context,
    private val client: WebDavClient = WebDavClient()
) {
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "m4v")

    suspend fun scan(
        source: LibrarySource,
        password: String?,
        onProgress: (ScanProgress) -> Unit = {}
    ): ScannedLibrary = withContext(Dispatchers.IO) {
        val videos = mutableListOf<ScannedVideo>()
        val folders = mutableListOf<ScannedFolder>()
        val queue = ArrayDeque<String>()
        queue.add("")
        var done = 0
        onProgress(ScanProgress(0, 0, source.name))

        while (queue.isNotEmpty()) {
            val currentPath = queue.removeFirst()
            val entries = client.list(source, password, currentPath)
            for (entry in entries) {
                val childPath = joinPath(currentPath, entry.name)
                if (entry.isDirectory) {
                    folders += ScannedFolder(
                        path = childPath,
                        parentPath = currentPath.ifBlank { null },
                        name = entry.name
                    )
                    queue.add(childPath)
                    continue
                }
                val ext = entry.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                if (ext !in videoExtensions) continue
                done += 1
                onProgress(ScanProgress(done, 0, childPath))
                val metadata = if (source.remoteIndexMode == RemoteIndexMode.Full) {
                    readRemoteMetadata(entry.url, client.authHeaders(source, password))
                } else {
                    RemoteMetadata(null, null, null, null)
                }
                videos += ScannedVideo(
                    displayName = entry.name,
                    uriString = entry.url,
                    fileSize = entry.size,
                    lastModified = entry.lastModified,
                    durationMs = metadata.durationMs,
                    width = metadata.width,
                    height = metadata.height,
                    coverFilePath = metadata.coverPath,
                    relativePath = childPath,
                    parentPath = currentPath.ifBlank { null }
                )
            }
        }
        ScannedLibrary(videos = videos.sortedBy { it.relativePath?.lowercase() }, folders = folders.distinctBy { it.path })
    }

    private fun readRemoteMetadata(url: String, headers: Map<String, String>): RemoteMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, headers)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val coverPath = extractCoverToCache(retriever, url)
            RemoteMetadata(durationMs, width, height, coverPath)
        } catch (_: Exception) {
            RemoteMetadata(null, null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractCoverToCache(retriever: MediaMetadataRetriever, url: String): String? {
        val coverDir = File(context.cacheDir, "video_covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val safeName = url.substringAfterLast('/').replace(Regex("""[^\w.\-]+"""), "_").take(80)
        val coverFile = File(coverDir, "${url.hashCode().absoluteValue}_${safeName}.jpg")
        if (coverFile.exists() && coverFile.length() > 0L) return coverFile.absolutePath
        return try {
            val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime ?: return null
            FileOutputStream(coverFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out) }
            bitmap.recycle()
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun joinPath(parent: String, name: String): String = if (parent.isBlank()) name else "$parent/$name"

    private data class RemoteMetadata(
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val coverPath: String?
    )
}
