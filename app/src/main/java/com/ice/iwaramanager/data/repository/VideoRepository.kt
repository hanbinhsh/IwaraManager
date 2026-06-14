package com.ice.iwaramanager.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.ice.iwaramanager.data.local.AppDatabase
import com.ice.iwaramanager.data.local.dao.CountItem
import com.ice.iwaramanager.data.local.dao.MatchTaskStatusCount
import com.ice.iwaramanager.data.local.entity.IwaraTagEntity
import com.ice.iwaramanager.data.local.entity.MatchCandidateEntity
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import com.ice.iwaramanager.data.local.entity.VideoEntity
import com.ice.iwaramanager.data.local.entity.VideoTagEntity
import com.ice.iwaramanager.data.local.entity.toVideoItem
import com.ice.iwaramanager.data.model.IwaraVideoMeta
import com.ice.iwaramanager.data.model.MatchTaskFilter
import com.ice.iwaramanager.data.model.MatchTaskStatus
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.domain.parser.IwaraFilenameParser
import com.ice.iwaramanager.domain.scanner.ScanProgress
import com.ice.iwaramanager.domain.scanner.VideoScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class VideoRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val videoDao = db.videoDao()
    private val tagDao = db.tagDao()
    private val matchTaskDao = db.matchTaskDao()
    private val scanner = VideoScanner(appContext)

    fun observeVideos(
        libraryRootUriString: String
    ): Flow<List<VideoItem>> {
        return videoDao.observeVideos(libraryRootUriString)
            .map { list ->
                list.map { it.toVideoItem() }
            }
    }

    fun observeVideosBySearch(
        libraryRootUriString: String,
        query: String
    ): Flow<List<VideoItem>> {
        return videoDao.observeVideosBySearch(
            libraryRootUriString = libraryRootUriString,
            query = query
        ).map { list ->
            list.map { it.toVideoItem() }
        }
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
        ).map { list ->
            list.map { it.toVideoItem() }
        }
    }

    fun observeTagCounts(libraryRootUriString: String): Flow<List<CountItem>> {
        return tagDao.observeTagCounts(libraryRootUriString)
    }

    fun observeAuthorCounts(libraryRootUriString: String): Flow<List<CountItem>> {
        return videoDao.observeAuthorCounts(libraryRootUriString)
    }

    fun observeQualityCounts(libraryRootUriString: String): Flow<List<CountItem>> {
        return videoDao.observeQualityCounts(libraryRootUriString)
    }

    fun observeUnqueuedUnmatchedVideos(
        libraryRootUriString: String
    ): Flow<List<VideoItem>> {
        return videoDao.observeUnqueuedUnmatchedVideos(libraryRootUriString)
            .map { list -> list.map { it.toVideoItem() } }
    }

    suspend fun getUnqueuedUnmatchedVideos(
        libraryRootUriString: String
    ): List<VideoItem> {
        return videoDao.getUnqueuedUnmatchedVideos(libraryRootUriString)
            .map { it.toVideoItem() }
    }

    fun observeMatchTasksByStatuses(
        libraryRootUriString: String,
        statuses: List<String>
    ): Flow<List<MatchTaskEntity>> {
        return if (statuses.isEmpty()) {
            matchTaskDao.observeTasks(libraryRootUriString)
        } else {
            matchTaskDao.observeTasksByStatuses(libraryRootUriString, statuses)
        }
    }

    fun observeMatchTaskFilterCounts(
        libraryRootUriString: String
    ): Flow<Map<MatchTaskFilter, Int>> {
        return combine(
            matchTaskDao.observeStatusCounts(libraryRootUriString),
            videoDao.observeUnqueuedUnmatchedVideoCount(libraryRootUriString)
        ) { statusCounts, unqueuedCount ->
            buildTaskCounts(statusCounts, unqueuedCount)
        }
    }

    private fun buildTaskCounts(
        statusCounts: List<MatchTaskStatusCount>,
        unqueuedCount: Int
    ): Map<MatchTaskFilter, Int> {
        val byStatus = statusCounts.associate { it.status to it.count }
        val running = listOf(MatchTaskStatus.Pending, MatchTaskStatus.Running)
            .sumOf { byStatus[it] ?: 0 }
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

    suspend fun scanAndSync(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {}
    ) {
        val now = System.currentTimeMillis()
        val rootUriString = treeUri.toString()

        val scannedVideos = scanner.scan(
            treeUri = treeUri,
            onProgress = onProgress
        )

        val oldEntities = videoDao.findAllByRoot(rootUriString)
            .associateBy { it.uriString }

        val newEntities = scannedVideos.map { scanned ->
            val parsed = IwaraFilenameParser.parse(scanned.displayName)
            val old = oldEntities[scanned.uriString]

            VideoEntity(
                uriString = scanned.uriString,
                libraryRootUriString = rootUriString,

                displayName = scanned.displayName,
                fileSize = scanned.fileSize,
                lastModified = scanned.lastModified,

                title = parsed.titleFromFilename,
                sourceVideoId = parsed.videoId,
                quality = parsed.quality,
                extension = parsed.extension,

                durationMs = scanned.durationMs,
                width = scanned.width,
                height = scanned.height,
                coverFilePath = scanned.coverFilePath,
                matchedIwaraId = old?.matchedIwaraId,
                remoteTitle = old?.remoteTitle,
                remoteDescription = old?.remoteDescription,
                remoteAuthorId = old?.remoteAuthorId,
                remoteAuthorName = old?.remoteAuthorName,
                remoteAuthorUsername = old?.remoteAuthorUsername,
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

        if (newEntities.isEmpty()) {
            videoDao.deleteByRoot(rootUriString)
            return
        }

        videoDao.upsertAll(newEntities)

        videoDao.deleteMissingByRoot(
            libraryRootUriString = rootUriString,
            existingUris = newEntities.map { it.uriString }
        )
    }

    suspend fun findVideoByUri(
        uriString: String
    ): VideoItem? {
        return videoDao.findByUri(uriString)?.toVideoItem()
    }

    suspend fun createMatchTask(
        video: VideoItem,
        libraryRootUriString: String,
        query: String
    ): Long {
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

    suspend fun updateMatchTask(task: MatchTaskEntity) {
        matchTaskDao.updateTask(task)
    }

    suspend fun markInterruptedMatchTasksAsFailed() {
        matchTaskDao.markRunningTasksAsFailed(
            runningStatus = MatchTaskStatus.Running,
            failedStatus = MatchTaskStatus.Failed,
            errorMessage = "应用关闭或任务中断，已自动标记为失败，可重试",
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun getMatchTask(taskId: Long): MatchTaskEntity? {
        return matchTaskDao.getTask(taskId)
    }

    suspend fun deleteMatchTask(taskId: Long) {
        matchTaskDao.deleteTask(taskId)
    }

    fun observeMatchTask(taskId: Long): Flow<MatchTaskEntity?> {
        return matchTaskDao.observeTask(taskId)
    }

    fun observeCandidatesForTask(taskId: Long): Flow<List<MatchCandidateEntity>> {
        return matchTaskDao.observeCandidatesForTask(taskId)
    }

    suspend fun getTasksByVideoUri(videoUriString: String): List<MatchTaskEntity> {
        return matchTaskDao.getTasksByVideoUri(videoUriString)
    }

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

    suspend fun replaceCandidatesForTask(
        taskId: Long,
        candidates: List<IwaraVideoMeta>,
        selectedIwaraId: String? = null
    ) {
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
        if (entities.isNotEmpty()) {
            matchTaskDao.insertCandidates(entities)
        }
    }

    suspend fun bindIwaraMeta(
        videoUriString: String,
        meta: IwaraVideoMeta,
        matchStatus: String
    ) {
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
            val tags = meta.tags.distinctBy { it.key }.map {
                IwaraTagEntity(
                    key = it.key,
                    namespace = it.namespace,
                    name = it.name
                )
            }
            if (tags.isNotEmpty()) {
                tagDao.upsertTags(tags)
                tagDao.insertVideoTags(
                    tags.map {
                        VideoTagEntity(
                            videoUriString = videoUriString,
                            tagKey = it.key
                        )
                    }
                )
            }
        }
    }

    suspend fun clearAll() {
        videoDao.clearAll()
        matchTaskDao.clearTasks()
    }

    suspend fun clearRoot(
        libraryRootUriString: String
    ) {
        videoDao.deleteByRoot(libraryRootUriString)
    }
}
