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
    private val videoExtensions = setOf(
        "mp4",
        "mkv",
        "webm",
        "mov",
        "m4v"
    )

    suspend fun scan(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {}
    ): List<ScannedVideo> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList()

        val videoFiles = mutableListOf<DocumentFile>()

        fun walk(dir: DocumentFile) {
            val children = runCatching {
                dir.listFiles()
            }.getOrDefault(emptyArray())

            for (child in children) {
                if (child.isDirectory) {
                    walk(child)
                    continue
                }

                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase()

                if (ext in videoExtensions) {
                    videoFiles += child
                }
            }
        }

        walk(root)

        val sortedFiles = videoFiles.sortedWith { a, b ->
            naturalCompare(a.name.orEmpty(), b.name.orEmpty())
        }

        val total = sortedFiles.size

        onProgress(
            ScanProgress(
                done = 0,
                total = total,
                currentName = null
            )
        )

        val result = mutableListOf<ScannedVideo>()

        sortedFiles.forEachIndexed { index, file ->
            val name = file.name.orEmpty()

            onProgress(
                ScanProgress(
                    done = index,
                    total = total,
                    currentName = name
                )
            )

            val metadata = readVideoMetadata(file.uri)
            val coverPath = extractCoverToCache(
                videoUri = file.uri,
                videoName = name
            )

            result += ScannedVideo(
                displayName = name,
                uriString = file.uri.toString(),
                fileSize = file.length(),
                lastModified = file.lastModified(),
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                coverFilePath = coverPath
            )

            onProgress(
                ScanProgress(
                    done = index + 1,
                    total = total,
                    currentName = name
                )
            )
        }

        result
    }

    private fun readVideoMetadata(
        uri: Uri
    ): VideoMetadata {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()

            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()

            VideoMetadata(
                durationMs = durationMs,
                width = width,
                height = height
            )
        } catch (_: Exception) {
            VideoMetadata(
                durationMs = null,
                width = null,
                height = null
            )
        } finally {
            runCatching {
                retriever.release()
            }
        }
    }

    private fun extractCoverToCache(
        videoUri: Uri,
        videoName: String
    ): String? {
        val coverDir = File(context.cacheDir, "video_covers")
        if (!coverDir.exists()) {
            coverDir.mkdirs()
        }

        val safeName = videoName
            .replace(Regex("""[^\w.\-]+"""), "_")
            .take(80)

        val hash = videoUri.toString().hashCode().absoluteValue

        val coverFile = File(
            coverDir,
            "${hash}_${safeName}.jpg"
        )

        if (coverFile.exists() && coverFile.length() > 0L) {
            return coverFile.absolutePath
        }

        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, videoUri)

            val bitmap = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime ?: return null

            FileOutputStream(coverFile).use { out ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    82,
                    out
                )
            }

            bitmap.recycle()

            coverFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            runCatching {
                retriever.release()
            }
        }
    }

    private data class VideoMetadata(
        val durationMs: Long?,
        val width: Int?,
        val height: Int?
    )

    private fun naturalCompare(
        a: String,
        b: String
    ): Int {
        return a.lowercase().compareTo(b.lowercase())
    }
}