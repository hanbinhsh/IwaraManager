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
    private val unsafeNameChars = Regex("""[^\w.\-]+""")

    /**
     * 扫描本地目录。每发现一个视频就通过 [onVideo] 回调立即交给上层写入数据库（边扫边存），
     * 避免扫描中途失败/闪退时丢失已扫描数据。对于在 [known] 中已存在且文件大小与修改时间未变、
     * 且已有时长和封面的视频，跳过元数据/封面读取，实现增量扫描。
     * 返回扫描到的目录列表。
     */
    suspend fun scanLibrary(
        treeUri: Uri,
        known: Map<String, KnownVideo>,
        onProgress: (ScanProgress) -> Unit = {},
        onVideo: suspend (ScannedVideo) -> Unit
    ): List<ScannedFolder> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
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

        sortedFiles.forEachIndexed { index, (file, parentPath) ->
            val name = file.name.orEmpty()
            val uriString = file.uri.toString()
            val fileSize = file.length()
            val lastModified = file.lastModified()
            onProgress(ScanProgress(done = index, total = sortedFiles.size, currentName = name))
            val needsMetadata = needsMetadata(known[uriString], fileSize, lastModified)
            val metadata = if (needsMetadata) readMetadataAndCover(file.uri, name) else ScanMetadata(null, null, null, null)
            onVideo(
                ScannedVideo(
                    displayName = name,
                    uriString = uriString,
                    fileSize = fileSize,
                    lastModified = lastModified,
                    durationMs = metadata.durationMs,
                    width = metadata.width,
                    height = metadata.height,
                    coverFilePath = metadata.coverPath,
                    relativePath = joinPath(parentPath, name),
                    parentPath = parentPath.ifBlank { null }
                )
            )
            onProgress(ScanProgress(done = index + 1, total = sortedFiles.size, currentName = name))
        }

        folders.distinctBy { it.path }
    }

    private fun needsMetadata(known: KnownVideo?, fileSize: Long, lastModified: Long): Boolean {
        if (known == null) return true
        val unchanged = known.fileSize == fileSize && known.lastModified == lastModified
        val coverOk = !known.coverFilePath.isNullOrBlank() && File(known.coverFilePath).exists()
        return !(unchanged && known.hasDuration && coverOk)
    }

    /**
     * 单次打开 MediaMetadataRetriever，一并读取时长/宽高并抽取封面，避免对同一文件 setDataSource 两次。
     * 封面文件已存在则跳过抽帧。
     */
    private fun readMetadataAndCover(uri: Uri, videoName: String): ScanMetadata {
        val coverFile = coverFileFor(uri, videoName)
        val existingCover = coverFile.takeIf { it.exists() && it.length() > 0L }?.absolutePath
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val coverPath = existingCover ?: extractCover(retriever, coverFile)
            ScanMetadata(durationMs, width, height, coverPath)
        } catch (_: Exception) {
            ScanMetadata(null, null, null, existingCover)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun coverFileFor(videoUri: Uri, videoName: String): File {
        // 封面存放在 filesDir（持久化目录）而非 cacheDir，避免系统在存储紧张时清理缓存导致封面丢失。
        val coverDir = File(context.filesDir, "video_covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val safeName = videoName.replace(unsafeNameChars, "_").take(80)
        val hash = videoUri.toString().hashCode().absoluteValue
        return File(coverDir, "${hash}_${safeName}.jpg")
    }

    private fun extractCover(retriever: MediaMetadataRetriever, coverFile: File): String? {
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

    private fun joinPath(parent: String, name: String): String {
        return if (parent.isBlank()) name else "$parent/$name"
    }

    private data class ScanMetadata(
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val coverPath: String?
    )

    private fun naturalCompare(a: String, b: String): Int = a.lowercase().compareTo(b.lowercase())
}
