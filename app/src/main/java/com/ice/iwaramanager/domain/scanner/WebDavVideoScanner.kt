package com.ice.iwaramanager.domain.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import com.ice.iwaramanager.data.model.LibrarySource
import com.ice.iwaramanager.data.model.RemoteIndexMode
import com.ice.iwaramanager.data.remote.WebDavClient
import com.ice.iwaramanager.playback.RemotePlaybackProxyService
import com.ice.iwaramanager.domain.util.NaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class WebDavVideoScanner(
    private val context: Context,
    private val client: WebDavClient = WebDavClient()
) {
    /**
     * 扫描远程目录。每发现一个视频就通过 [onVideo] 回调立即交给上层写入数据库（边扫边存），
     * 避免扫描中途失败/闪退时丢失已扫描数据。对于在 [known] 中已存在且文件大小与修改时间未变、
     * 且已有时长和封面的视频，跳过昂贵的远程元数据/封面读取，实现增量扫描。
     * 返回扫描到的目录列表（目录数据较轻，统一在最后写入）。
     */
    suspend fun scan(
        source: LibrarySource,
        password: String?,
        known: Map<String, KnownVideo>,
        onProgress: (ScanProgress) -> Unit = {},
        onVideo: suspend (ScannedVideo) -> Unit
    ): List<ScannedFolder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<ScannedFolder>()
        val queue = ArrayDeque<RemoteFolder>()
        queue.add(RemoteFolder(displayPath = "", url = client.buildUrl(source, "")))
        val visitedUrls = mutableSetOf<String>()
        var done = 0
        onProgress(ScanProgress(0, 0, source.name))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visitedUrls.add(current.url.trimEnd('/'))) continue
            val entries = try {
                client.listUrl(source, password, current.url)
            } catch (error: Exception) {
                if (current.displayPath.isBlank()) throw error
                onProgress(ScanProgress(done, 0, "跳过目录：${current.displayPath.ifBlank { source.name }}；${error.message.orEmpty().lineSequence().firstOrNull().orEmpty()}"))
                continue
            }
            for (entry in entries.sortedWith { left, right ->
                NaturalOrder.compare(left.name, right.name)
            }) {
                val childPath = VideoMediaSupport.joinPath(current.displayPath, entry.name)
                if (entry.isDirectory) {
                    folders += ScannedFolder(
                        path = childPath,
                        parentPath = current.displayPath.ifBlank { null },
                        name = entry.name
                    )
                    queue.add(RemoteFolder(displayPath = childPath, url = entry.url))
                    continue
                }
                if (!VideoMediaSupport.isVideoFile(entry.name)) continue
                done += 1
                onProgress(ScanProgress(done, 0, childPath))
                val knownVideo = known[entry.url]
                val metadata = if (source.remoteIndexMode == RemoteIndexMode.Full && needsMetadata(knownVideo, entry.size, entry.lastModified)) {
                    val headers = client.authHeaders(source, password)
                    val metadataUrl = runCatching {
                        RemotePlaybackProxyService.createProxyUri(
                            context = context,
                            remoteUrl = entry.url,
                            headers = headers,
                            mimeType = VideoMediaSupport.mimeType(entry.name),
                            readTimeoutSeconds = source.readTimeoutSeconds,
                            idleTimeoutSeconds = source.readTimeoutSeconds,
                            allowInsecureTls = source.webDavAllowInsecureTls
                        ).toString()
                    }.getOrDefault(entry.url)
                    readRemoteMetadata(
                        url = metadataUrl,
                        headers = if (metadataUrl == entry.url) headers else emptyMap(),
                        timeoutSeconds = source.readTimeoutSeconds,
                        coverKey = entry.url,
                        coverDisplayName = entry.name,
                        existingCoverPath = knownVideo?.coverFilePath
                    )
                } else {
                    // 未变化或非完整索引模式：交给上层复用数据库中已有的时长与封面
                    RemoteMetadata(null, null, null, null)
                }
                onVideo(
                    ScannedVideo(
                        displayName = entry.name,
                        uriString = entry.url,
                        fileSize = entry.size,
                        lastModified = entry.lastModified,
                        durationMs = metadata.durationMs,
                        width = metadata.width,
                        height = metadata.height,
                        coverFilePath = metadata.coverPath,
                        relativePath = childPath,
                        parentPath = current.displayPath.ifBlank { null }
                    )
                )
            }
        }
        folders.distinctBy { it.path }
    }

    private fun needsMetadata(known: KnownVideo?, fileSize: Long, lastModified: Long): Boolean {
        if (known == null) return true
        val unchanged = known.fileSize == fileSize && known.lastModified == lastModified
        val coverOk = VideoMediaSupport.usableCoverPath(known.coverFilePath) != null
        return !(unchanged && known.hasDuration && coverOk)
    }

    suspend fun ensureCover(
        source: LibrarySource,
        password: String?,
        remoteUrl: String,
        displayName: String,
        existingCoverPath: String?
    ): String? = withContext(Dispatchers.IO) {
        VideoMediaSupport.usableCoverPath(existingCoverPath)?.let { return@withContext it }
        VideoMediaSupport.usableCoverPath(
            VideoMediaSupport.coverFile(context, remoteUrl, displayName).absolutePath
        )?.let { return@withContext it }

        val headers = client.authHeaders(source, password)
        val metadataUrl = runCatching {
            RemotePlaybackProxyService.createProxyUri(
                context = context,
                remoteUrl = remoteUrl,
                headers = headers,
                mimeType = VideoMediaSupport.mimeType(displayName),
                readTimeoutSeconds = source.readTimeoutSeconds,
                idleTimeoutSeconds = source.readTimeoutSeconds,
                allowInsecureTls = source.webDavAllowInsecureTls
            ).toString()
        }.getOrDefault(remoteUrl)

        readRemoteMetadata(
            url = metadataUrl,
            headers = if (metadataUrl == remoteUrl) headers else emptyMap(),
            timeoutSeconds = source.readTimeoutSeconds,
            coverKey = remoteUrl,
            coverDisplayName = displayName,
            existingCoverPath = null
        ).coverPath
    }

    private fun readRemoteMetadata(
        url: String,
        headers: Map<String, String>,
        timeoutSeconds: Int,
        coverKey: String,
        coverDisplayName: String,
        existingCoverPath: String?
    ): RemoteMetadata {
        val retriever = MediaMetadataRetriever()
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "iwara-webdav-metadata").apply { isDaemon = true }
        }
        val future = executor.submit<RemoteMetadata> {
            try {
                retriever.setDataSource(url, headers)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                val coverPath = extractCoverToCache(
                    retriever = retriever,
                    coverKey = coverKey,
                    displayName = coverDisplayName,
                    existingCoverPath = existingCoverPath
                )
                RemoteMetadata(durationMs, width, height, coverPath)
            } catch (_: Exception) {
                RemoteMetadata(null, null, null, null)
            } finally {
                runCatching { retriever.release() }
            }
        }
        return try {
            future.get(timeoutSeconds.coerceIn(5, 300).toLong(), TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            runCatching { retriever.release() }
            future.cancel(true)
            RemoteMetadata(null, null, null, null)
        } catch (_: Exception) {
            RemoteMetadata(null, null, null, null)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun extractCoverToCache(
        retriever: MediaMetadataRetriever,
        coverKey: String,
        displayName: String,
        existingCoverPath: String?
    ): String? {
        // 封面存放在 filesDir（持久化目录）而非 cacheDir，避免系统在存储紧张时清理缓存导致封面丢失，
        // 从而保证断开 WebDAV 后封面仍可离线显示。
        VideoMediaSupport.usableCoverPath(existingCoverPath)?.let { return it }
        val coverFile = VideoMediaSupport.coverFile(
            context = context,
            key = coverKey,
            displayName = displayName
        )
        VideoMediaSupport.usableCoverPath(coverFile.absolutePath)?.let { return it }
        return VideoMediaSupport.extractCover(retriever, coverFile)
    }

    private data class RemoteFolder(
        val displayPath: String,
        val url: String
    )

    private data class RemoteMetadata(
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val coverPath: String?
    )
}
