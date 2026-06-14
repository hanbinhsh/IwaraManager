package com.ice.iwaramanager.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
import com.ice.iwaramanager.data.model.LibraryFolderNode
import com.ice.iwaramanager.data.model.LibrarySource
import com.ice.iwaramanager.data.model.LibrarySourceType
import com.ice.iwaramanager.data.model.MatchTaskFilter
import com.ice.iwaramanager.data.model.MatchTaskStatus
import com.ice.iwaramanager.data.model.RemoteIndexMode
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.remote.WebDavClient
import com.ice.iwaramanager.domain.parser.IwaraFilenameParser
import com.ice.iwaramanager.domain.scanner.ScanProgress
import com.ice.iwaramanager.domain.scanner.ScannedLibrary
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
        indexMode: RemoteIndexMode,
        connectTimeoutSeconds: Int,
        readTimeoutSeconds: Int
    ): LibrarySource {
        val fixedBaseUrl = baseUrl.trim().trimEnd('/')
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

    suspend fun testWebDavConnection(
        name: String,
        baseUrl: String,
        rootPath: String,
        username: String,
        password: String,
        indexMode: RemoteIndexMode,
        connectTimeoutSeconds: Int,
        readTimeoutSeconds: Int
    ): String = withContext(Dispatchers.IO) {
        val source = LibrarySource(
            id = "test",
            name = name.ifBlank { "WebDAV" },
            type = LibrarySourceType.WebDav,
            rootUriString = "test",
            webDavBaseUrl = baseUrl.trim().trimEnd('/'),
            webDavRootPath = rootPath.trim().ifBlank { "/" },
            webDavUsername = username.trim().ifBlank { null },
            remoteIndexMode = indexMode,
            connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(5, 120),
            readTimeoutSeconds = readTimeoutSeconds.coerceIn(5, 180),
            createdAt = 0L,
            updatedAt = 0L
        )
        webDavClient.testConnection(source, password)
    }

    suspend fun deleteSource(sourceId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            videoDao.deleteBySource(sourceId)
            matchTaskDao.deleteTasksBySource(sourceId)
            sourceDao.deleteFoldersForSource(sourceId)
            sourceDao.deleteSource(sourceId)
        }
        credentialStore.deletePassword(sourceId)
    }

    fun observeVideos(libraryRootUriString: String): Flow<List<VideoItem>> {
        return videoDao.observeVideos(libraryRootUriString).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeVideosBySourceIds(sourceIds: List<String>): Flow<List<VideoItem>> {
        return videoDao.observeVideosBySourceIds(sourceIds, sourceIds.size).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeVideosInFolder(sourceId: String, parentPath: String): Flow<List<VideoItem>> {
        return videoDao.observeVideosInFolder(sourceId, parentPath).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeChildFolders(sourceId: String, parentPath: String): Flow<List<LibraryFolderNode>> {
        return sourceDao.observeChildFolders(sourceId, parentPath).map { list -> list.map { it.toNode() } }
    }

    fun observeFoldersForSourceIds(sourceIds: List<String>): Flow<List<LibraryFolderNode>> {
        return sourceDao.observeFoldersForSourceIds(sourceIds, sourceIds.size).map { list -> list.map { it.toNode() } }
    }

    fun observeVideosBySearch(libraryRootUriString: String, query: String): Flow<List<VideoItem>> {
        return videoDao.observeVideosBySearch(libraryRootUriString, query).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeVideosBySearchForSourceIds(sourceIds: List<String>, query: String): Flow<List<VideoItem>> {
        return videoDao.observeVideosBySearchForSourceIds(sourceIds, sourceIds.size, query).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeVideosByFilters(
        libraryRootUriString: String,
        tagKeys: Set<String>,
        authorKeys: Set<String>,
        qualities: Set<String>
    ): Flow<List<VideoItem>> {
        return videoDao.observeVideosByFilters(
            libraryRootUriString = libraryRootUriString,
            tagKeys = tagKeys.toList(),
            tagCount = tagKeys.size,
            authorKeys = authorKeys.toList(),
            authorKeyCount = authorKeys.size,
            qualities = qualities.toList(),
            qualityCount = qualities.size
        ).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeVideosByFiltersForSourceIds(
        sourceIds: List<String>,
        tagKeys: Set<String>,
        authorKeys: Set<String>,
        qualities: Set<String>
    ): Flow<List<VideoItem>> {
        return videoDao.observeVideosByFiltersForSourceIds(
            sourceIds = sourceIds,
            sourceCount = sourceIds.size,
            tagKeys = tagKeys.toList(),
            tagCount = tagKeys.size,
            authorKeys = authorKeys.toList(),
            authorKeyCount = authorKeys.size,
            qualities = qualities.toList(),
            qualityCount = qualities.size
        ).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeTagCounts(libraryRootUriString: String): Flow<List<CountItem>> = tagDao.observeTagCounts(libraryRootUriString)
    fun observeTagCountsForSourceIds(sourceIds: List<String>): Flow<List<CountItem>> = tagDao.observeTagCountsForSourceIds(sourceIds, sourceIds.size)
    fun observeAuthorCounts(libraryRootUriString: String): Flow<List<CountItem>> = videoDao.observeAuthorCounts(libraryRootUriString)
    fun observeAuthorCountsForSourceIds(sourceIds: List<String>): Flow<List<CountItem>> = videoDao.observeAuthorCountsForSourceIds(sourceIds, sourceIds.size)
    fun observeQualityCounts(libraryRootUriString: String): Flow<List<CountItem>> = videoDao.observeQualityCounts(libraryRootUriString)
    fun observeQualityCountsForSourceIds(sourceIds: List<String>): Flow<List<CountItem>> = videoDao.observeQualityCountsForSourceIds(sourceIds, sourceIds.size)

    fun observeUnqueuedUnmatchedVideos(libraryRootUriString: String): Flow<List<VideoItem>> {
        return videoDao.observeUnqueuedUnmatchedVideos(libraryRootUriString).map { list -> list.map { it.toVideoItem() } }
    }

    fun observeUnqueuedUnmatchedVideosForSourceIds(sourceIds: List<String>): Flow<List<VideoItem>> {
        return videoDao.observeUnqueuedUnmatchedVideosForSourceIds(sourceIds, sourceIds.size).map { list -> list.map { it.toVideoItem() } }
    }

    suspend fun getUnqueuedUnmatchedVideos(libraryRootUriString: String): List<VideoItem> {
        return videoDao.getUnqueuedUnmatchedVideos(libraryRootUriString).map { it.toVideoItem() }
    }

    suspend fun getUnqueuedUnmatchedVideosForSourceIds(sourceIds: List<String>): List<VideoItem> {
        return videoDao.getUnqueuedUnmatchedVideosForSourceIds(sourceIds, sourceIds.size).map { it.toVideoItem() }
    }

    suspend fun getMatchedVideos(libraryRootUriString: String): List<VideoItem> {
        return videoDao.getMatchedVideos(libraryRootUriString).map { it.toVideoItem() }
    }

    suspend fun getMatchedVideosForSourceIds(sourceIds: List<String>): List<VideoItem> {
        return videoDao.getMatchedVideosForSourceIds(sourceIds, sourceIds.size).map { it.toVideoItem() }
    }

    suspend fun getRematchableVideos(libraryRootUriString: String): List<VideoItem> {
        return videoDao.getRematchableVideos(libraryRootUriString).map { it.toVideoItem() }
    }

    suspend fun getRematchableVideosForSourceIds(sourceIds: List<String>): List<VideoItem> {
        return videoDao.getRematchableVideosForSourceIds(sourceIds, sourceIds.size).map { it.toVideoItem() }
    }

    fun observeMatchTasksByStatuses(libraryRootUriString: String, statuses: List<String>): Flow<List<MatchTaskEntity>> {
        return if (statuses.isEmpty()) matchTaskDao.observeTasks(libraryRootUriString) else matchTaskDao.observeTasksByStatuses(libraryRootUriString, statuses)
    }

    fun observeMatchTasksByStatusesForSourceIds(sourceIds: List<String>, statuses: List<String>): Flow<List<MatchTaskEntity>> {
        return if (statuses.isEmpty()) {
            matchTaskDao.observeTasksForSourceIds(sourceIds, sourceIds.size)
        } else {
            matchTaskDao.observeTasksByStatusesForSourceIds(sourceIds, sourceIds.size, statuses)
        }
    }

    fun observeMatchTaskFilterCounts(libraryRootUriString: String): Flow<Map<MatchTaskFilter, Int>> {
        return combine(
            matchTaskDao.observeStatusCounts(libraryRootUriString),
            videoDao.observeUnqueuedUnmatchedVideoCount(libraryRootUriString)
        ) { statusCounts, unqueuedCount -> buildTaskCounts(statusCounts, unqueuedCount) }
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

    suspend fun scanAndSync(treeUri: Uri, onProgress: (ScanProgress) -> Unit = {}) {
        val source = addOrUpdateLocalSource(treeUri)
        scanSource(source.id, onProgress)
    }

    suspend fun scanSource(sourceId: String, onProgress: (ScanProgress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val source = sourceDao.getSource(sourceId)?.toLibrarySource() ?: error("来源不存在")
        val scanned = when (source.type) {
            LibrarySourceType.LocalSaf -> scanner.scanLibrary(Uri.parse(source.rootUriString), onProgress)
            LibrarySourceType.WebDav -> webDavScanner.scan(source, credentialStore.loadPassword(source.id), onProgress)
        }
        syncScanResult(source, scanned)
    }

    suspend fun scanSources(sourceIds: List<String>, onProgress: (ScanProgress) -> Unit = {}) {
        val sources = if (sourceIds.isEmpty()) getSources() else getSources().filter { it.id in sourceIds }
        sources.forEach { source -> scanSource(source.id, onProgress) }
    }

    private suspend fun syncScanResult(source: LibrarySource, scanned: ScannedLibrary) {
        val now = System.currentTimeMillis()
        val oldEntities = videoDao.findAllByRoot(source.id).associateBy { it.uriString }
        val newEntities = scanned.videos.map { scannedVideo ->
            val parsed = IwaraFilenameParser.parse(scannedVideo.displayName)
            val old = oldEntities[scannedVideo.uriString]
            VideoEntity(
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
                createdAt = old?.createdAt ?: now,
                updatedAt = now
            )
        }
        db.withTransaction {
            sourceDao.deleteFoldersForSource(source.id)
            val folderEntities = scanned.folders.map {
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
            if (newEntities.isEmpty()) {
                videoDao.deleteBySource(source.id)
            } else {
                videoDao.upsertAll(newEntities)
                videoDao.deleteMissingBySource(source.id, newEntities.map { it.uriString })
            }
            sourceDao.upsertSource(source.copy(updatedAt = now).toEntity())
        }
    }

    suspend fun findVideoByUri(uriString: String): VideoItem? = videoDao.findByUri(uriString)?.toVideoItem()

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

    suspend fun getNextNeedReviewAfter(task: MatchTaskEntity): MatchTaskEntity? {
        return matchTaskDao.getNextTaskByStatusAfterCursor(
            libraryRootUriString = task.libraryRootUriString,
            status = MatchTaskStatus.NeedReview,
            currentUpdatedAt = task.updatedAt,
            currentTaskId = task.id
        ) ?: matchTaskDao.getFirstTaskByStatus(
            libraryRootUriString = task.libraryRootUriString,
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

    suspend fun clearRoot(libraryRootUriString: String) {
        videoDao.deleteByRoot(libraryRootUriString)
    }

    suspend fun exportDatabaseTo(uri: Uri) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        require(databaseFile.exists()) { "数据库文件不存在" }
        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            databaseFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("无法打开导出位置")
    }

    suspend fun importDatabaseFrom(uri: Uri) = withContext(Dispatchers.IO) {
        AppDatabase.closeCurrent()
        try {
            val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
            databaseFile.parentFile?.mkdirs()
            deleteDatabaseSidecarFiles(databaseFile)
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                databaseFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法打开导入文件")
        } finally {
            reopenDatabase()
        }
    }

    fun webDavPlaybackHeaders(source: LibrarySource): Map<String, String> {
        return webDavClient.authHeaders(source, credentialStore.loadPassword(source.id))
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
        listOf(databaseFile, File(databaseFile.path + "-wal"), File(databaseFile.path + "-shm")).forEach { file ->
            if (file.exists()) file.delete()
        }
    }

    private companion object {
        const val DATABASE_NAME = "iwara_manager.db"
    }
}
