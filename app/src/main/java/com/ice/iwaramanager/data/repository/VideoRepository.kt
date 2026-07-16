package com.ice.iwaramanager.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.ice.iwaramanager.data.local.AppDatabase
import com.ice.iwaramanager.data.local.dao.CountItem
import com.ice.iwaramanager.data.local.dao.MatchTaskStatusCount
import com.ice.iwaramanager.data.local.entity.IwaraTagEntity
import com.ice.iwaramanager.data.local.entity.LibraryFolderEntity
import com.ice.iwaramanager.data.local.entity.MatchCandidateEntity
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import com.ice.iwaramanager.data.local.entity.VideoEntity
import com.ice.iwaramanager.data.local.entity.VideoTagEntity
import com.ice.iwaramanager.data.local.entity.toEntity
import com.ice.iwaramanager.data.local.entity.toLibrarySource
import com.ice.iwaramanager.data.local.entity.toVideoItem
import com.ice.iwaramanager.data.model.IwaraVideoMeta
import com.ice.iwaramanager.data.model.CleanupResult
import com.ice.iwaramanager.data.model.DatabaseImportResult
import com.ice.iwaramanager.data.model.LibraryFolderNode
import com.ice.iwaramanager.data.model.LibrarySource
import com.ice.iwaramanager.data.model.LibrarySourceType
import com.ice.iwaramanager.data.model.MatchTaskFilter
import com.ice.iwaramanager.data.model.MatchTaskStatus
import com.ice.iwaramanager.data.model.RemoteIndexMode
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.VideoSortMode
import com.ice.iwaramanager.data.remote.WebDavClient
import com.ice.iwaramanager.domain.parser.IwaraFilenameParser
import com.ice.iwaramanager.domain.scanner.KnownVideo
import com.ice.iwaramanager.domain.scanner.ScanProgress
import com.ice.iwaramanager.domain.scanner.ScannedVideo
import com.ice.iwaramanager.domain.scanner.VideoMediaSupport
import com.ice.iwaramanager.domain.scanner.VideoScanner
import com.ice.iwaramanager.domain.scanner.WebDavVideoScanner
import com.ice.iwaramanager.domain.security.WebDavCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VideoRepository(context: Context) {
    private val appContext = context.applicationContext
    private var db = AppDatabase.get(appContext)
    private var videoDao = db.videoDao()
    private var tagDao = db.tagDao()
    private var matchTaskDao = db.matchTaskDao()
    private var sourceDao = db.librarySourceDao()
    private val scanner = VideoScanner(appContext)
    private val webDavClient = WebDavClient()
    private val webDavScanner = WebDavVideoScanner(appContext, webDavClient)
    private val credentialStore = WebDavCredentialStore(appContext)

    private fun reopenDatabase() {
        db = AppDatabase.get(appContext)
        videoDao = db.videoDao()
        tagDao = db.tagDao()
        matchTaskDao = db.matchTaskDao()
        sourceDao = db.librarySourceDao()
    }

    fun observeSources(): Flow<List<LibrarySource>> {
        return sourceDao.observeSources().map { list -> list.map { it.toLibrarySource() } }
    }

    suspend fun getSources(): List<LibrarySource> {
        return sourceDao.getSources().map { it.toLibrarySource() }
    }

    suspend fun getSource(sourceId: String): LibrarySource? {
        return sourceDao.getSource(sourceId)?.toLibrarySource()
    }

    suspend fun ensureLegacyLocalSource(rootUriString: String?) {
        if (rootUriString.isNullOrBlank()) return
        val existing = sourceDao.getSourceByRoot(rootUriString)
        if (existing != null) return
        val now = System.currentTimeMillis()
        val name = localDisplayName(Uri.parse(rootUriString))
        sourceDao.upsertSource(
            LibrarySource(
                id = rootUriString,
                name = name,
                type = LibrarySourceType.LocalSaf,
                rootUriString = rootUriString,
                remoteIndexMode = RemoteIndexMode.Full,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        )
    }

    suspend fun addOrUpdateLocalSource(uri: Uri): LibrarySource {
        val uriString = uri.toString()
        val now = System.currentTimeMillis()
        val old = sourceDao.getSourceByRoot(uriString)?.toLibrarySource()
        val source = LibrarySource(
            id = old?.id ?: uriString,
            name = old?.name ?: localDisplayName(uri),
            type = LibrarySourceType.LocalSaf,
            rootUriString = uriString,
            remoteIndexMode = RemoteIndexMode.Full,
            createdAt = old?.createdAt ?: now,
            updatedAt = now
        )
        sourceDao.upsertSource(source.toEntity())
        return source
    }

    suspend fun addWebDavSource(
        name: String,
        baseUrl: String,
        rootPath: String,
        username: String,
        password: String,
        allowInsecureTls: Boolean,
        indexMode: RemoteIndexMode,
        connectTimeoutSeconds: Int,
        readTimeoutSeconds: Int
    ): LibrarySource {
        val fixedBaseUrl = webDavClient.normalizeBaseUrl(baseUrl)
        val fixedRootPath = rootPath.trim().ifBlank { "/" }
        val now = System.currentTimeMillis()
        val id = "webdav:${UUID.randomUUID()}"
        val source = LibrarySource(
            id = id,
            name = name.trim().ifBlank { fixedBaseUrl.substringAfter("//") },
            type = LibrarySourceType.WebDav,
            rootUriString = id,
            webDavBaseUrl = fixedBaseUrl,
            webDavRootPath = fixedRootPath,
            webDavUsername = username.trim().ifBlank { null },
            webDavAllowInsecureTls = allowInsecureTls,
            remoteIndexMode = indexMode,
            connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(5, 120),
            readTimeoutSeconds = readTimeoutSeconds.coerceIn(5, 180),
            createdAt = now,
            updatedAt = now
        )
        webDavClient.validateSource(source)
        if (source.webDavUsername != null && password.isBlank()) {
            error("已填写用户名，但密码为空")
        }
        sourceDao.upsertSource(source.toEntity())
        credentialStore.savePassword(id, password)
        return source
    }

    suspend fun updateWebDavSource(
        sourceId: String,
        name: String,
        baseUrl: String,
        rootPath: String,
        username: String,
        password: String,
        allowInsecureTls: Boolean,
        indexMode: RemoteIndexMode,
        connectTimeoutSeconds: Int,
        readTimeoutSeconds: Int
    ): LibrarySource {
        val old = sourceDao.getSource(sourceId)?.toLibrarySource() ?: error("WebDAV 来源不存在")
        require(old.type == LibrarySourceType.WebDav) { "只能编辑 WebDAV 来源" }
        val fixedBaseUrl = webDavClient.normalizeBaseUrl(baseUrl)
        val fixedRootPath = rootPath.trim().ifBlank { "/" }
        val source = old.copy(
            name = name.trim().ifBlank { fixedBaseUrl.substringAfter("//") },
            webDavBaseUrl = fixedBaseUrl,
            webDavRootPath = fixedRootPath,
            webDavUsername = username.trim().ifBlank { null },
            webDavAllowInsecureTls = allowInsecureTls,
            remoteIndexMode = indexMode,
            connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(5, 120),
            readTimeoutSeconds = readTimeoutSeconds.coerceIn(5, 180),
            updatedAt = System.currentTimeMillis()
        )
        webDavClient.validateSource(source)
        val oldPassword = credentialStore.loadPassword(sourceId)
        if (source.webDavUsername != null && password.isBlank() && oldPassword.isNullOrBlank()) {
            error("已填写用户名，但密码为空")
        }
        sourceDao.upsertSource(source.toEntity())
        if (password.isNotBlank()) credentialStore.savePassword(sourceId, password)
        return source
    }

    suspend fun renameSource(sourceId: String, name: String): LibrarySource = withContext(Dispatchers.IO) {
        val old = sourceDao.getSource(sourceId)?.toLibrarySource() ?: error("来源不存在")
        val fixedName = name.trim()
        require(fixedName.isNotBlank()) { "来源名称不能为空" }
        val source = old.copy(
            name = fixedName,
            updatedAt = System.currentTimeMillis()
        )
        sourceDao.upsertSource(source.toEntity())
        source
    }

    suspend fun testWebDavConnection(
        name: String,
        baseUrl: String,
        rootPath: String,
        username: String,
        password: String,
        allowInsecureTls: Boolean,
        indexMode: RemoteIndexMode,
        connectTimeoutSeconds: Int,
        readTimeoutSeconds: Int
    ): String = withContext(Dispatchers.IO) {
        val source = LibrarySource(
            id = "test",
            name = name.ifBlank { "WebDAV" },
            type = LibrarySourceType.WebDav,
            rootUriString = "test",
            webDavBaseUrl = webDavClient.normalizeBaseUrl(baseUrl),
            webDavRootPath = rootPath.trim().ifBlank { "/" },
            webDavUsername = username.trim().ifBlank { null },
            webDavAllowInsecureTls = allowInsecureTls,
            remoteIndexMode = indexMode,
            connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(5, 120),
            readTimeoutSeconds = readTimeoutSeconds.coerceIn(5, 180),
            createdAt = 0L,
            updatedAt = 0L
        )
        webDavClient.testConnection(source, password)
    }

    suspend fun removeSourceConfiguration(sourceId: String) = withContext(Dispatchers.IO) {
        sourceDao.deleteSource(sourceId)
        credentialStore.deletePassword(sourceId)
    }

    fun observeVideoCountForSourceIds(sourceIds: List<String>): Flow<Int> {
        return videoDao.observeVideoCountForSourceIds(sourceIds, sourceIds.size)
    }

    fun pagedVideosByFilters(
        sourceIds: List<String>,
        tagKeys: Set<String>,
        authorKeys: Set<String>,
        qualities: Set<String>,
        sortMode: VideoSortMode
    ): Flow<PagingData<VideoItem>> {
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            videoDao.pagingVideosByFiltersForSourceIds(
                sourceIds = sourceIds,
                sourceCount = sourceIds.size,
                tagKeys = tagKeys.toList(),
                tagCount = tagKeys.size,
                authorKeys = authorKeys.toList(),
                authorKeyCount = authorKeys.size,
                qualities = qualities.toList(),
                qualityCount = qualities.size,
                sortMode = sortMode.name
            )
        }.flow.map { pagingData -> pagingData.map { it.toVideoItem() } }
    }

    fun observeFilteredVideoCount(
        sourceIds: List<String>,
        tagKeys: Set<String>,
        authorKeys: Set<String>,
        qualities: Set<String>
    ): Flow<Int> {
        return videoDao.observeVideoCountByFiltersForSourceIds(
            sourceIds = sourceIds,
            sourceCount = sourceIds.size,
            tagKeys = tagKeys.toList(),
            tagCount = tagKeys.size,
            authorKeys = authorKeys.toList(),
            authorKeyCount = authorKeys.size,
            qualities = qualities.toList(),
            qualityCount = qualities.size
        )
    }

    fun pagedVideosBySearch(
        sourceIds: List<String>,
        query: String,
        sortMode: VideoSortMode
    ): Flow<PagingData<VideoItem>> {
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            videoDao.pagingVideosBySearchForSourceIds(sourceIds, sourceIds.size, query, sortMode.name)
        }.flow.map { pagingData -> pagingData.map { it.toVideoItem() } }
    }

    fun observeSearchVideoCount(sourceIds: List<String>, query: String): Flow<Int> {
        return videoDao.observeVideoCountBySearchForSourceIds(sourceIds, sourceIds.size, query)
    }

    fun observeVideosInFolder(
        sourceId: String,
        parentPath: String,
        sortMode: VideoSortMode
    ): Flow<List<VideoItem>> {
        return videoDao.observeVideosInFolder(sourceId, parentPath, sortMode.name).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeChildFolders(sourceId: String, parentPath: String): Flow<List<LibraryFolderNode>> {
        return sourceDao.observeChildFolders(sourceId, parentPath).map { list -> list.map { it.toNode() } }
    }

    fun observeFoldersForSourceIds(sourceIds: List<String>): Flow<List<LibraryFolderNode>> {
        return sourceDao.observeFoldersForSourceIds(sourceIds, sourceIds.size).map { list -> list.map { it.toNode() } }
    }

    fun observeTagCountsForSourceIds(sourceIds: List<String>): Flow<List<CountItem>> = tagDao.observeTagCountsForSourceIds(sourceIds, sourceIds.size)
    fun observeAuthorCountsForSourceIds(sourceIds: List<String>): Flow<List<CountItem>> = videoDao.observeAuthorCountsForSourceIds(sourceIds, sourceIds.size)
    fun observeQualityCountsForSourceIds(sourceIds: List<String>): Flow<List<CountItem>> = videoDao.observeQualityCountsForSourceIds(sourceIds, sourceIds.size)

    fun observeUnqueuedUnmatchedVideosForSourceIds(sourceIds: List<String>): Flow<List<VideoItem>> {
        return videoDao.observeUnqueuedUnmatchedVideosForSourceIds(sourceIds, sourceIds.size).map { list -> list.map { it.toVideoItem() } }
    }

    suspend fun getUnqueuedUnmatchedVideosForSourceIds(sourceIds: List<String>): List<VideoItem> {
        return videoDao.getUnqueuedUnmatchedVideosForSourceIds(sourceIds, sourceIds.size).map { it.toVideoItem() }
    }

    suspend fun getMatchedVideosForSourceIds(sourceIds: List<String>): List<VideoItem> {
        return videoDao.getMatchedVideosForSourceIds(sourceIds, sourceIds.size).map { it.toVideoItem() }
    }

    suspend fun getRematchableVideosForSourceIds(sourceIds: List<String>): List<VideoItem> {
        return videoDao.getRematchableVideosForSourceIds(sourceIds, sourceIds.size).map { it.toVideoItem() }
    }

    suspend fun getMatchedVideosWithDurationIssuesForSourceIds(
        sourceIds: List<String>,
        toleranceSeconds: Int
    ): List<VideoItem> {
        return videoDao.getMatchedVideosWithDurationIssuesForSourceIds(
            sourceIds = sourceIds,
            sourceCount = sourceIds.size,
            toleranceSeconds = toleranceSeconds
        ).map { it.toVideoItem() }
    }

    fun observeMatchTasksByStatusesForSourceIds(sourceIds: List<String>, statuses: List<String>): Flow<List<MatchTaskEntity>> {
        return if (statuses.isEmpty()) {
            matchTaskDao.observeTasksForSourceIds(sourceIds, sourceIds.size)
        } else {
            matchTaskDao.observeTasksByStatusesForSourceIds(sourceIds, sourceIds.size, statuses)
        }
    }

    fun observeMatchTaskFilterCountsForSourceIds(sourceIds: List<String>): Flow<Map<MatchTaskFilter, Int>> {
        return combine(
            matchTaskDao.observeStatusCountsForSourceIds(sourceIds, sourceIds.size),
            videoDao.observeUnqueuedUnmatchedVideoCountForSourceIds(sourceIds, sourceIds.size)
        ) { statusCounts, unqueuedCount -> buildTaskCounts(statusCounts, unqueuedCount) }
    }

    private fun buildTaskCounts(statusCounts: List<MatchTaskStatusCount>, unqueuedCount: Int): Map<MatchTaskFilter, Int> {
        val byStatus = statusCounts.associate { it.status to it.count }
        val running = listOf(MatchTaskStatus.Pending, MatchTaskStatus.Running).sumOf { byStatus[it] ?: 0 }
        return mapOf(
            MatchTaskFilter.All to byStatus.values.sum(),
            MatchTaskFilter.Running to running,
            MatchTaskFilter.Success to (byStatus[MatchTaskStatus.AutoMatched] ?: 0),
            MatchTaskFilter.NeedReview to (byStatus[MatchTaskStatus.NeedReview] ?: 0),
            MatchTaskFilter.Failed to (byStatus[MatchTaskStatus.Failed] ?: 0),
            MatchTaskFilter.Skipped to (byStatus[MatchTaskStatus.Skipped] ?: 0),
            MatchTaskFilter.Unqueued to unqueuedCount
        )
    }

    /**
     * 将历史封面从 cacheDir 迁移到 filesDir（持久化目录），并修正数据库中的封面路径。
     * 旧版本把封面写在 cacheDir，可能被系统在存储紧张时清理；迁移后断开 WebDAV 也能离线显示封面。
     * 幂等：cacheDir 中无残留封面时直接返回，不做任何操作。
     */
    suspend fun migrateCoversToPersistentDir() = withContext(Dispatchers.IO) {
        val oldDir = File(appContext.cacheDir, "video_covers")
        val files = oldDir.listFiles()?.filter { it.isFile } ?: return@withContext
        if (files.isEmpty()) return@withContext
        val newDir = File(appContext.filesDir, "video_covers")
        if (!newDir.exists()) newDir.mkdirs()
        var migrated = false
        for (file in files) {
            val target = File(newDir, file.name)
            val ok = when {
                target.exists() && target.length() > 0L -> file.delete().let { true }
                file.renameTo(target) -> true
                else -> runCatching { file.copyTo(target, overwrite = true); file.delete(); true }.getOrDefault(false)
            }
            if (ok) migrated = true
        }
        if (migrated) {
            val oldPrefix = oldDir.absolutePath + File.separator
            val newPrefix = newDir.absolutePath + File.separator
            videoDao.updateCoverDir(oldPrefix, newPrefix, "$oldPrefix%")
        }
    }

    suspend fun scanSource(sourceId: String, onProgress: (ScanProgress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val source = sourceDao.getSource(sourceId)?.toLibrarySource() ?: error("来源不存在")
        val now = System.currentTimeMillis()

        // 预读数据库中已有的视频，用于增量扫描（跳过未变化文件的元数据/封面读取）
        // 以及合并已匹配的远程元数据、判断哪些文件已被删除。
        val oldEntities = videoDao.findAllByRoot(source.id).associateBy { it.uriString }
        val known = oldEntities.mapValues { (_, old) ->
            KnownVideo(
                fileSize = old.fileSize,
                lastModified = old.lastModified,
                hasDuration = old.durationMs != null,
                coverFilePath = old.coverFilePath
            )
        }

        val buffer = mutableListOf<VideoEntity>()
        val processedUris = hashSetOf<String>()

        // 边扫边存：每累积一批就立即写入数据库，扫描中途失败/闪退也能保留已扫描的数据。
        suspend fun flush() {
            if (buffer.isEmpty()) return
            videoDao.upsertAll(buffer.toList())
            buffer.clear()
        }

        val onVideo: suspend (ScannedVideo) -> Unit = { scannedVideo ->
            val oldBySameUri = oldEntities[scannedVideo.uriString]
                ?: videoDao.findByUri(scannedVideo.uriString)

            if (oldBySameUri != null) {
                buffer += mergeScannedVideo(scannedVideo, oldBySameUri, source, now)
                processedUris += scannedVideo.uriString
                if (buffer.size >= SCAN_FLUSH_BATCH_SIZE) flush()
            } else {
                val movedOld = videoDao.findReusableMovedVideos(
                    displayName = scannedVideo.displayName,
                    fileSize = scannedVideo.fileSize,
                    uriString = scannedVideo.uriString
                ).filterNot { it.uriString in processedUris }
                    .singleOrNull()

                if (movedOld == null) {
                    buffer += mergeScannedVideo(scannedVideo, null, source, now)
                    if (buffer.size >= SCAN_FLUSH_BATCH_SIZE) flush()
                } else {
                    val migrated = mergeScannedVideo(scannedVideo, movedOld, source, now)
                    db.withTransaction {
                        videoDao.upsertAll(listOf(migrated))
                        tagDao.migrateVideoUri(movedOld.uriString, scannedVideo.uriString)
                        matchTaskDao.migrateVideoUri(
                            oldUriString = movedOld.uriString,
                            newUriString = scannedVideo.uriString,
                            libraryRootUriString = source.id,
                            displayName = scannedVideo.displayName,
                            coverFilePath = migrated.coverFilePath,
                            localDurationMs = migrated.durationMs,
                            updatedAt = now
                        )
                        videoDao.deleteByUris(listOf(movedOld.uriString))
                    }
                }
                processedUris += scannedVideo.uriString
            }
        }

        val folders = when (source.type) {
            LibrarySourceType.LocalSaf ->
                scanner.scanLibrary(Uri.parse(source.rootUriString), known, onProgress, onVideo)
            LibrarySourceType.WebDav ->
                webDavScanner.scan(source, credentialStore.loadPassword(source.id), known, onProgress, onVideo)
        }
        flush()

        // 扫描成功完成后只更新目录树和来源扫描时间；缺失记录由用户手动清理。
        db.withTransaction {
            sourceDao.deleteFoldersForSource(source.id)
            val folderEntities = folders.map {
                LibraryFolderEntity(
                    sourceId = source.id,
                    sourceName = source.name,
                    path = it.path,
                    parentPath = it.parentPath,
                    name = it.name,
                    updatedAt = now
                )
            }
            if (folderEntities.isNotEmpty()) sourceDao.upsertFolders(folderEntities)
            sourceDao.upsertSource(
                source.copy(
                    updatedAt = now,
                    lastCompletedScanAt = now
                ).toEntity()
            )
        }
    }

    suspend fun scanSources(sourceIds: List<String>, onProgress: (ScanProgress) -> Unit = {}) {
        val sources = if (sourceIds.isEmpty()) getSources() else getSources().filter { it.id in sourceIds }
        sources.forEach { source -> scanSource(source.id, onProgress) }
    }

    private fun mergeScannedVideo(
        scannedVideo: ScannedVideo,
        old: VideoEntity?,
        source: LibrarySource,
        now: Long
    ): VideoEntity {
        val parsed = IwaraFilenameParser.parse(scannedVideo.displayName)
        return VideoEntity(
            uriString = scannedVideo.uriString,
            libraryRootUriString = source.id,
            sourceId = source.id,
            relativePath = scannedVideo.relativePath,
            parentPath = scannedVideo.parentPath,
            displayName = scannedVideo.displayName,
            fileSize = scannedVideo.fileSize,
            lastModified = scannedVideo.lastModified,
            title = parsed.titleFromFilename,
            sourceVideoId = parsed.videoId,
            quality = parsed.quality,
            extension = parsed.extension,
            durationMs = scannedVideo.durationMs ?: old?.durationMs,
            width = scannedVideo.width ?: old?.width,
            height = scannedVideo.height ?: old?.height,
            coverFilePath = scannedVideo.coverFilePath ?: old?.coverFilePath,
            matchedIwaraId = old?.matchedIwaraId,
            remoteTitle = old?.remoteTitle,
            remoteDescription = old?.remoteDescription,
            remoteAuthorId = old?.remoteAuthorId,
            remoteAuthorName = old?.remoteAuthorName,
            remoteAuthorUsername = old?.remoteAuthorUsername,
            remoteAuthorAvatarUrl = old?.remoteAuthorAvatarUrl,
            remoteThumbnailUrl = old?.remoteThumbnailUrl,
            remoteRating = old?.remoteRating,
            remoteVisibility = old?.remoteVisibility,
            remoteCreatedAt = old?.remoteCreatedAt,
            remoteUpdatedAt = old?.remoteUpdatedAt,
            remoteDurationSeconds = old?.remoteDurationSeconds,
            remoteLikeCount = old?.remoteLikeCount,
            remoteViewCount = old?.remoteViewCount,
            remoteCommentCount = old?.remoteCommentCount,
            remoteRawJson = old?.remoteRawJson,
            matchStatus = old?.matchStatus ?: "unmatched",
            lastSeenAt = now,
            createdAt = old?.createdAt ?: now,
            updatedAt = old?.updatedAt ?: now
        )
    }

    suspend fun findVideoByUri(uriString: String): VideoItem? = videoDao.findByUri(uriString)?.toVideoItem()

    suspend fun ensureVideoCover(uriString: String): VideoItem = withContext(Dispatchers.IO) {
        val video = videoDao.findByUri(uriString) ?: error("视频记录不存在")
        VideoMediaSupport.usableCoverPath(video.coverFilePath)?.let {
            return@withContext video.toVideoItem()
        }

        val source = sourceDao.getSource(video.sourceId)?.toLibrarySource()
        val coverPath = when (source?.type) {
            LibrarySourceType.WebDav -> webDavScanner.ensureCover(
                source = source,
                password = credentialStore.loadPassword(source.id),
                remoteUrl = video.uriString,
                displayName = video.displayName,
                existingCoverPath = video.coverFilePath
            )

            LibrarySourceType.LocalSaf, null -> {
                if (!video.uriString.startsWith("content://")) {
                    error("远程来源配置已被移除，无法补全预览图")
                }
                scanner.ensureCover(
                    uri = Uri.parse(video.uriString),
                    videoName = video.displayName,
                    existingCoverPath = video.coverFilePath
                )
            }
        }

        val validCoverPath = VideoMediaSupport.usableCoverPath(coverPath)
            ?: error("无法从该视频提取预览图")
        db.withTransaction {
            videoDao.updateCover(video.uriString, validCoverPath)
            matchTaskDao.updateCoverForVideo(video.uriString, validCoverPath)
        }
        videoDao.findByUri(video.uriString)?.toVideoItem()
            ?: error("预览图已生成，但视频记录不存在")
    }

    suspend fun createMatchTask(video: VideoItem, libraryRootUriString: String, query: String): Long {
        val now = System.currentTimeMillis()
        return matchTaskDao.insertTask(
            MatchTaskEntity(
                libraryRootUriString = libraryRootUriString,
                videoUriString = video.uriString,
                displayName = video.displayName,
                coverFilePath = video.coverFilePath,
                query = query,
                localDurationMs = video.durationMs,
                status = MatchTaskStatus.Pending,
                matchedIwaraId = null,
                candidateCount = 0,
                errorMessage = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateMatchTask(task: MatchTaskEntity) = matchTaskDao.updateTask(task)

    suspend fun markInterruptedMatchTasksAsFailed() {
        matchTaskDao.markRunningTasksAsFailed(
            runningStatus = MatchTaskStatus.Running,
            failedStatus = MatchTaskStatus.Failed,
            errorMessage = "应用关闭或任务中断，已自动标记为失败，可重试",
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun getMatchTask(taskId: Long): MatchTaskEntity? = matchTaskDao.getTask(taskId)
    suspend fun deleteMatchTask(taskId: Long) = matchTaskDao.deleteTask(taskId)
    fun observeMatchTask(taskId: Long): Flow<MatchTaskEntity?> = matchTaskDao.observeTask(taskId)
    fun observeCandidatesForTask(taskId: Long): Flow<List<MatchCandidateEntity>> = matchTaskDao.observeCandidatesForTask(taskId)
    suspend fun getTasksByVideoUri(videoUriString: String): List<MatchTaskEntity> = matchTaskDao.getTasksByVideoUri(videoUriString)
    suspend fun getMatchTasksByStatusesForSourceIds(
        sourceIds: List<String>,
        statuses: List<String>
    ): List<MatchTaskEntity> {
        return matchTaskDao.getTasksByStatusesForSourceIds(sourceIds, sourceIds.size, statuses)
    }

    suspend fun getNextNeedReviewAfter(
        task: MatchTaskEntity,
        sourceIds: List<String>
    ): MatchTaskEntity? {
        return matchTaskDao.getNextTaskByStatusAfterCursor(
            sourceIds = sourceIds,
            sourceCount = sourceIds.size,
            status = MatchTaskStatus.NeedReview,
            currentUpdatedAt = task.updatedAt,
            currentTaskId = task.id
        ) ?: matchTaskDao.getFirstTaskByStatus(
            sourceIds = sourceIds,
            sourceCount = sourceIds.size,
            status = MatchTaskStatus.NeedReview,
            currentTaskId = task.id
        )
    }

    suspend fun replaceCandidatesForTask(taskId: Long, candidates: List<IwaraVideoMeta>, selectedIwaraId: String? = null) {
        matchTaskDao.deleteCandidatesForTask(taskId)
        val entities = candidates.map { meta ->
            MatchCandidateEntity(
                taskId = taskId,
                iwaraId = meta.id,
                title = meta.title,
                authorName = meta.authorName,
                authorUsername = meta.authorUsername,
                thumbnailUrl = meta.thumbnailUrl,
                rating = meta.rating,
                createdAtText = meta.createdAt,
                durationSeconds = meta.durationSeconds,
                viewCount = meta.viewCount,
                likeCount = meta.likeCount,
                selected = selectedIwaraId == meta.id,
                rawJson = meta.rawJson
            )
        }
        if (entities.isNotEmpty()) matchTaskDao.insertCandidates(entities)
    }

    suspend fun bindIwaraMeta(videoUriString: String, meta: IwaraVideoMeta, matchStatus: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            videoDao.updateIwaraMeta(
                uriString = videoUriString,
                matchedIwaraId = meta.id,
                remoteTitle = meta.title,
                remoteDescription = meta.description,
                remoteAuthorId = meta.authorId,
                remoteAuthorName = meta.authorName,
                remoteAuthorUsername = meta.authorUsername,
                remoteAuthorAvatarUrl = meta.authorAvatarUrl,
                remoteThumbnailUrl = meta.thumbnailUrl,
                remoteRating = meta.rating,
                remoteVisibility = meta.visibility,
                remoteCreatedAt = meta.createdAt,
                remoteUpdatedAt = meta.updatedAt,
                remoteDurationSeconds = meta.durationSeconds,
                remoteLikeCount = meta.likeCount,
                remoteViewCount = meta.viewCount,
                remoteCommentCount = meta.commentCount,
                remoteRawJson = meta.rawJson,
                matchStatus = matchStatus,
                updatedAt = now
            )
            tagDao.deleteTagsForVideo(videoUriString)
            val tags = meta.tags.distinctBy { it.key }.map { IwaraTagEntity(key = it.key, namespace = it.namespace, name = it.name) }
            if (tags.isNotEmpty()) {
                tagDao.upsertTags(tags)
                tagDao.insertVideoTags(tags.map { VideoTagEntity(videoUriString = videoUriString, tagKey = it.key) })
            }
        }
    }

    suspend fun clearAll() {
        videoDao.clearAll()
        matchTaskDao.clearTasks()
        sourceDao.clearFolders()
        getSources().filter { it.type == LibrarySourceType.WebDav }.forEach { credentialStore.deletePassword(it.id) }
        getSources().forEach { sourceDao.deleteSource(it.id) }
    }

    suspend fun cleanupOrphanedRecords(sourceIds: List<String>): CleanupResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            val configuredSources = sourceDao.getSources().map { it.toLibrarySource() }
            val configuredIds = configuredSources.map { it.id }
            val cleanupScope = resolveCleanupScope(configuredIds, sourceIds)
            val coversAllConfiguredSources = cleanupScope.coversAllConfiguredSources
            val targetSources = configuredSources.filter { it.id in cleanupScope.targetSourceIds }

            val mediaUris = buildList {
                targetSources.forEach { source ->
                    addAll(
                        videoDao.getVideoUrisMissingFromLastScan(
                            sourceId = source.id,
                            lastCompletedScanAt = source.lastCompletedScanAt
                        )
                    )
                }
                if (coversAllConfiguredSources) {
                    addAll(
                        if (configuredIds.isEmpty()) {
                            videoDao.getAllVideoUris()
                        } else {
                            videoDao.getVideoUrisOutsideSourceIds(
                                sourceIds = configuredIds,
                                sourceCount = configuredIds.size
                            )
                        }
                    )
                }
            }.distinct()
            val deletedCount = deleteVideoRecordsInTransaction(mediaUris)
            val deletedFolders = if (coversAllConfiguredSources) {
                if (configuredIds.isEmpty()) {
                    sourceDao.clearFolders()
                } else {
                    sourceDao.deleteFoldersOutsideSourceIds(configuredIds, configuredIds.size)
                }
            } else {
                0
            }

            CleanupResult(
                mediaRecordsDeleted = deletedCount,
                folderRecordsDeleted = deletedFolders
            )
        }
    }

    private suspend fun deleteVideoRecordsInTransaction(uriStrings: List<String>): Int {
        uriStrings.chunked(300).forEach { chunk ->
            tagDao.deleteTagsForVideos(chunk)
            matchTaskDao.deleteCandidatesForVideos(chunk)
            matchTaskDao.deleteTasksForVideos(chunk)
            videoDao.deleteByUris(chunk)
        }
        return uriStrings.size
    }

    suspend fun exportDatabaseTo(uri: Uri) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
            while (it.moveToNext()) Unit
        }
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        require(databaseFile.exists()) { "数据库文件不存在" }
        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            databaseFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("无法打开导出位置")
    }

    suspend fun importDatabaseFrom(uri: Uri): DatabaseImportResult = withContext(Dispatchers.IO) {
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        val tempFile = File(databaseFile.parentFile, "iwara_manager_import_tmp.db")
        val backupFile = File(databaseFile.parentFile, "iwara_manager_import_backup.db")

        tempFile.delete()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法打开导入文件")
        val importedBytes = tempFile.length()
        val databaseVersion = validateDatabaseFile(tempFile)

        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
            while (it.moveToNext()) Unit
        }
        AppDatabase.closeCurrent()
        try {
            databaseFile.parentFile?.mkdirs()
            backupFile.delete()
            if (databaseFile.exists()) {
                databaseFile.copyTo(backupFile, overwrite = true)
            }
            deleteDatabaseSidecarFiles(databaseFile)
            tempFile.copyTo(databaseFile, overwrite = true)
            deleteDatabaseSidecarFiles(databaseFile)
            reopenDatabase()
            db.openHelper.writableDatabase
            backupFile.delete()
            DatabaseImportResult(
                importedBytes = importedBytes,
                databaseVersion = databaseVersion
            )
        } catch (e: Exception) {
            AppDatabase.closeCurrent()
            deleteDatabaseSidecarFiles(databaseFile)
            if (backupFile.exists()) {
                backupFile.copyTo(databaseFile, overwrite = true)
                backupFile.delete()
            } else {
                databaseFile.delete()
            }
            reopenDatabase()
            throw e
        } finally {
            tempFile.delete()
        }
    }

    private fun validateDatabaseFile(file: File): Int {
        require(file.exists() && file.length() > 0L) { "导入文件为空" }
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA integrity_check", emptyArray()).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "导入文件不是有效的 SQLite 数据库"
                }
            }
            database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'video' LIMIT 1",
                emptyArray()
            ).use { cursor ->
                require(cursor.moveToFirst()) { "所选文件不是 IwaraManager 数据库" }
            }
            database.rawQuery("PRAGMA user_version", emptyArray()).use { cursor ->
                require(cursor.moveToFirst()) { "无法读取数据库版本" }
                cursor.getInt(0)
            }
        }
    }

    fun webDavPlaybackHeaders(source: LibrarySource): Map<String, String> {
        return webDavClient.authHeaders(source, credentialStore.loadPassword(source.id))
    }

    fun webDavSavedPassword(sourceId: String): String? {
        return credentialStore.loadPassword(sourceId)
    }

    private fun localDisplayName(uri: Uri): String {
        return DocumentFile.fromTreeUri(appContext, uri)?.name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
            ?: "本地目录"
    }

    private fun LibraryFolderEntity.toNode(): LibraryFolderNode {
        return LibraryFolderNode(
            sourceId = sourceId,
            sourceName = sourceName,
            path = path,
            parentPath = parentPath,
            name = name
        )
    }

    private fun deleteDatabaseSidecarFiles(databaseFile: File) {
        listOf(File(databaseFile.path + "-wal"), File(databaseFile.path + "-shm")).forEach { file ->
            if (file.exists()) file.delete()
        }
    }

    private companion object {
        const val DATABASE_NAME = "iwara_manager.db"

        // 边扫边存的批量大小：每扫描到这么多视频就写入一次数据库。
        const val SCAN_FLUSH_BATCH_SIZE = 25

        // Paging 每页大小。
        const val PAGE_SIZE = 60
    }
}
