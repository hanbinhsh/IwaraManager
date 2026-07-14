package com.ice.iwaramanager.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ice.iwaramanager.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video WHERE libraryRootUriString = :libraryRootUriString ORDER BY updatedAt DESC")
    fun observeVideos(libraryRootUriString: String): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM video
        WHERE sourceId = :sourceId
        AND COALESCE(parentPath, '') = :parentPath
        ORDER BY
            CASE WHEN :sortMode = 'NameAsc' THEN COALESCE(title, displayName) END COLLATE NOCASE ASC,
            CASE WHEN :sortMode = 'NameDesc' THEN COALESCE(title, displayName) END COLLATE NOCASE DESC,
            CASE WHEN :sortMode = 'FileTimeDesc' THEN COALESCE(lastModified, 0) END DESC,
            CASE WHEN :sortMode = 'FileTimeAsc' THEN COALESCE(lastModified, 0) END ASC,
            CASE WHEN :sortMode = 'DurationDesc' THEN COALESCE(durationMs, 0) END DESC,
            CASE WHEN :sortMode = 'DurationAsc' THEN COALESCE(durationMs, 0) END ASC,
            CASE WHEN :sortMode = 'FileSizeDesc' THEN COALESCE(fileSize, 0) END DESC,
            CASE WHEN :sortMode = 'FileSizeAsc' THEN COALESCE(fileSize, 0) END ASC,
            COALESCE(title, displayName) COLLATE NOCASE ASC
        """
    )
    fun observeVideosInFolder(sourceId: String, parentPath: String, sortMode: String): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT uriString, libraryRootUriString, sourceId, relativePath, parentPath, displayName,
               fileSize, lastModified, title, sourceVideoId, quality, extension, durationMs,
               width, height, coverFilePath, matchedIwaraId, matchStatus
        FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND (:authorKeyCount = 0 OR COALESCE(remoteAuthorUsername, remoteAuthorId, remoteAuthorName, '') IN (:authorKeys))
        AND (:qualityCount = 0 OR COALESCE(quality, '') IN (:qualities))
        AND (:tagCount = 0 OR uriString IN (
            SELECT videoUriString FROM video_tag WHERE tagKey IN (:tagKeys)
            GROUP BY videoUriString HAVING COUNT(DISTINCT tagKey) = :tagCount
        ))
        ORDER BY
            CASE WHEN :sortMode = 'NameAsc' THEN COALESCE(title, displayName) END COLLATE NOCASE ASC,
            CASE WHEN :sortMode = 'NameDesc' THEN COALESCE(title, displayName) END COLLATE NOCASE DESC,
            CASE WHEN :sortMode = 'FileTimeDesc' THEN COALESCE(lastModified, 0) END DESC,
            CASE WHEN :sortMode = 'FileTimeAsc' THEN COALESCE(lastModified, 0) END ASC,
            CASE WHEN :sortMode = 'DurationDesc' THEN COALESCE(durationMs, 0) END DESC,
            CASE WHEN :sortMode = 'DurationAsc' THEN COALESCE(durationMs, 0) END ASC,
            CASE WHEN :sortMode = 'FileSizeDesc' THEN COALESCE(fileSize, 0) END DESC,
            CASE WHEN :sortMode = 'FileSizeAsc' THEN COALESCE(fileSize, 0) END ASC,
            COALESCE(title, displayName) COLLATE NOCASE ASC
        """
    )
    fun pagingVideosByFiltersForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        tagKeys: List<String>,
        tagCount: Int,
        authorKeys: List<String>,
        authorKeyCount: Int,
        qualities: List<String>,
        qualityCount: Int,
        sortMode: String
    ): PagingSource<Int, VideoListRow>

    @Query(
        """
        SELECT COUNT(*) FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND (:authorKeyCount = 0 OR COALESCE(remoteAuthorUsername, remoteAuthorId, remoteAuthorName, '') IN (:authorKeys))
        AND (:qualityCount = 0 OR COALESCE(quality, '') IN (:qualities))
        AND (:tagCount = 0 OR uriString IN (
            SELECT videoUriString FROM video_tag WHERE tagKey IN (:tagKeys)
            GROUP BY videoUriString HAVING COUNT(DISTINCT tagKey) = :tagCount
        ))
        """
    )
    fun observeVideoCountByFiltersForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        tagKeys: List<String>,
        tagCount: Int,
        authorKeys: List<String>,
        authorKeyCount: Int,
        qualities: List<String>,
        qualityCount: Int
    ): Flow<Int>

    @Query(
        """
        SELECT uriString, libraryRootUriString, sourceId, relativePath, parentPath, displayName,
               fileSize, lastModified, title, sourceVideoId, quality, extension, durationMs,
               width, height, coverFilePath, matchedIwaraId, matchStatus
        FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND (
            displayName LIKE '%' || :query || '%'
            OR title LIKE '%' || :query || '%'
            OR sourceVideoId LIKE '%' || :query || '%'
            OR matchedIwaraId LIKE '%' || :query || '%'
            OR remoteTitle LIKE '%' || :query || '%'
            OR remoteAuthorName LIKE '%' || :query || '%'
            OR remoteAuthorUsername LIKE '%' || :query || '%'
            OR quality LIKE '%' || :query || '%'
        )
        ORDER BY
            CASE WHEN :sortMode = 'NameAsc' THEN COALESCE(title, displayName) END COLLATE NOCASE ASC,
            CASE WHEN :sortMode = 'NameDesc' THEN COALESCE(title, displayName) END COLLATE NOCASE DESC,
            CASE WHEN :sortMode = 'FileTimeDesc' THEN COALESCE(lastModified, 0) END DESC,
            CASE WHEN :sortMode = 'FileTimeAsc' THEN COALESCE(lastModified, 0) END ASC,
            CASE WHEN :sortMode = 'DurationDesc' THEN COALESCE(durationMs, 0) END DESC,
            CASE WHEN :sortMode = 'DurationAsc' THEN COALESCE(durationMs, 0) END ASC,
            CASE WHEN :sortMode = 'FileSizeDesc' THEN COALESCE(fileSize, 0) END DESC,
            CASE WHEN :sortMode = 'FileSizeAsc' THEN COALESCE(fileSize, 0) END ASC,
            COALESCE(title, displayName) COLLATE NOCASE ASC
        """
    )
    fun pagingVideosBySearchForSourceIds(sourceIds: List<String>, sourceCount: Int, query: String, sortMode: String): PagingSource<Int, VideoListRow>

    @Query(
        """
        SELECT COUNT(*) FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND (
            displayName LIKE '%' || :query || '%'
            OR title LIKE '%' || :query || '%'
            OR sourceVideoId LIKE '%' || :query || '%'
            OR matchedIwaraId LIKE '%' || :query || '%'
            OR remoteTitle LIKE '%' || :query || '%'
            OR remoteAuthorName LIKE '%' || :query || '%'
            OR remoteAuthorUsername LIKE '%' || :query || '%'
            OR quality LIKE '%' || :query || '%'
        )
        """
    )
    fun observeVideoCountBySearchForSourceIds(sourceIds: List<String>, sourceCount: Int, query: String): Flow<Int>

    @Query(
        """
        SELECT COALESCE(remoteAuthorUsername, remoteAuthorId, remoteAuthorName, '') AS itemKey,
               COALESCE(remoteAuthorName, remoteAuthorUsername, remoteAuthorId, '') AS label,
               COUNT(*) AS count
        FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND matchedIwaraId IS NOT NULL
        AND COALESCE(remoteAuthorUsername, remoteAuthorId, remoteAuthorName, '') != ''
        GROUP BY itemKey, label
        ORDER BY count DESC, label ASC
        """
    )
    fun observeAuthorCounts(libraryRootUriString: String): Flow<List<CountItem>>

    @Query(
        """
        SELECT COALESCE(remoteAuthorUsername, remoteAuthorId, remoteAuthorName, '') AS itemKey,
               COALESCE(remoteAuthorName, remoteAuthorUsername, remoteAuthorId, '') AS label,
               COUNT(*) AS count
        FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND matchedIwaraId IS NOT NULL
        AND COALESCE(remoteAuthorUsername, remoteAuthorId, remoteAuthorName, '') != ''
        GROUP BY itemKey, label
        ORDER BY count DESC, label ASC
        """
    )
    fun observeAuthorCountsForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<CountItem>>

    @Query(
        """
        SELECT COALESCE(quality, '未知') AS itemKey,
               COALESCE(quality, '未知') AS label,
               COUNT(*) AS count
        FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        GROUP BY itemKey, label
        ORDER BY count DESC, label ASC
        """
    )
    fun observeQualityCounts(libraryRootUriString: String): Flow<List<CountItem>>

    @Query(
        """
        SELECT COALESCE(quality, '未知') AS itemKey,
               COALESCE(quality, '未知') AS label,
               COUNT(*) AS count
        FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        GROUP BY itemKey, label
        ORDER BY count DESC, label ASC
        """
    )
    fun observeQualityCountsForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<CountItem>>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (SELECT videoUriString FROM match_task WHERE libraryRootUriString = :libraryRootUriString)
        ORDER BY updatedAt DESC
        """
    )
    fun observeUnqueuedUnmatchedVideos(libraryRootUriString: String): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (SELECT videoUriString FROM match_task WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds)))
        ORDER BY updatedAt DESC
        """
    )
    fun observeUnqueuedUnmatchedVideosForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (SELECT videoUriString FROM match_task WHERE libraryRootUriString = :libraryRootUriString)
        """
    )
    fun observeUnqueuedUnmatchedVideoCount(libraryRootUriString: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (SELECT videoUriString FROM match_task WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds)))
        """
    )
    fun observeUnqueuedUnmatchedVideoCountForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<Int>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (SELECT videoUriString FROM match_task WHERE libraryRootUriString = :libraryRootUriString)
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getUnqueuedUnmatchedVideos(libraryRootUriString: String): List<VideoEntity>

    @Query(
        """
        SELECT * FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (SELECT videoUriString FROM match_task WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds)))
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getUnqueuedUnmatchedVideosForSourceIds(sourceIds: List<String>, sourceCount: Int): List<VideoEntity>

    @Query("SELECT * FROM video WHERE libraryRootUriString = :libraryRootUriString AND matchedIwaraId IS NOT NULL AND matchedIwaraId != '' ORDER BY updatedAt DESC")
    suspend fun getMatchedVideos(libraryRootUriString: String): List<VideoEntity>

    @Query(
        """
        SELECT * FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND matchedIwaraId IS NOT NULL AND matchedIwaraId != ''
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getMatchedVideosForSourceIds(sourceIds: List<String>, sourceCount: Int): List<VideoEntity>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND ((matchedIwaraId IS NOT NULL AND matchedIwaraId != '') OR (sourceVideoId IS NOT NULL AND sourceVideoId != ''))
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getRematchableVideos(libraryRootUriString: String): List<VideoEntity>

    @Query(
        """
        SELECT * FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND ((matchedIwaraId IS NOT NULL AND matchedIwaraId != '') OR (sourceVideoId IS NOT NULL AND sourceVideoId != ''))
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getRematchableVideosForSourceIds(sourceIds: List<String>, sourceCount: Int): List<VideoEntity>

    @Query(
        """
        SELECT * FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        AND matchedIwaraId IS NOT NULL AND matchedIwaraId != ''
        AND (
            remoteDurationSeconds IS NULL
            OR remoteDurationSeconds <= 0
            OR (
                durationMs IS NOT NULL
                AND durationMs > 0
                AND ABS(((durationMs + 500) / 1000) - remoteDurationSeconds) > :toleranceSeconds
            )
        )
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getMatchedVideosWithDurationIssuesForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        toleranceSeconds: Int
    ): List<VideoEntity>

    @Query(
        """
        UPDATE video SET
            matchedIwaraId = :matchedIwaraId,
            remoteTitle = :remoteTitle,
            remoteDescription = :remoteDescription,
            remoteAuthorId = :remoteAuthorId,
            remoteAuthorName = :remoteAuthorName,
            remoteAuthorUsername = :remoteAuthorUsername,
            remoteAuthorAvatarUrl = :remoteAuthorAvatarUrl,
            remoteThumbnailUrl = :remoteThumbnailUrl,
            remoteRating = :remoteRating,
            remoteVisibility = :remoteVisibility,
            remoteCreatedAt = :remoteCreatedAt,
            remoteUpdatedAt = :remoteUpdatedAt,
            remoteDurationSeconds = :remoteDurationSeconds,
            remoteLikeCount = :remoteLikeCount,
            remoteViewCount = :remoteViewCount,
            remoteCommentCount = :remoteCommentCount,
            remoteRawJson = :remoteRawJson,
            matchStatus = :matchStatus,
            updatedAt = :updatedAt
        WHERE uriString = :uriString
        """
    )
    suspend fun updateIwaraMeta(
        uriString: String,
        matchedIwaraId: String?,
        remoteTitle: String?,
        remoteDescription: String?,
        remoteAuthorId: String?,
        remoteAuthorName: String?,
        remoteAuthorUsername: String?,
        remoteAuthorAvatarUrl: String?,
        remoteThumbnailUrl: String?,
        remoteRating: String?,
        remoteVisibility: String?,
        remoteCreatedAt: String?,
        remoteUpdatedAt: String?,
        remoteDurationSeconds: Long?,
        remoteLikeCount: Int?,
        remoteViewCount: Int?,
        remoteCommentCount: Int?,
        remoteRawJson: String?,
        matchStatus: String,
        updatedAt: Long
    )

    @Query(
        """
        SELECT COUNT(*) FROM video
        WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
        """
    )
    fun observeVideoCountForSourceIds(sourceIds: List<String>, sourceCount: Int): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT * FROM video WHERE libraryRootUriString = :libraryRootUriString")
    suspend fun findAllByRoot(libraryRootUriString: String): List<VideoEntity>

    @Query(
        """
        SELECT uriString
        FROM video
        WHERE sourceId = :sourceId
        AND :lastCompletedScanAt IS NOT NULL
        AND (lastSeenAt IS NULL OR lastSeenAt < :lastCompletedScanAt)
        """
    )
    suspend fun getVideoUrisMissingFromLastScan(sourceId: String, lastCompletedScanAt: Long?): List<String>

    @Query(
        """
        SELECT uriString
        FROM video
        WHERE :sourceCount > 0
        AND sourceId NOT IN (:sourceIds)
        """
    )
    suspend fun getVideoUrisOutsideSourceIds(sourceIds: List<String>, sourceCount: Int): List<String>

    @Query("SELECT uriString FROM video")
    suspend fun getAllVideoUris(): List<String>

    @Query("SELECT * FROM video WHERE uriString = :uriString LIMIT 1")
    suspend fun findByUri(uriString: String): VideoEntity?

    @Query(
        """
        SELECT * FROM video
        WHERE displayName = :displayName
        AND fileSize = :fileSize
        AND uriString != :uriString
        ORDER BY
            CASE WHEN matchedIwaraId IS NULL OR matchedIwaraId = '' THEN 1 ELSE 0 END,
            updatedAt DESC
        """
    )
    suspend fun findReusableMovedVideos(
        displayName: String,
        fileSize: Long,
        uriString: String
    ): List<VideoEntity>

    @Upsert
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Query("DELETE FROM video WHERE libraryRootUriString = :libraryRootUriString")
    suspend fun deleteByRoot(libraryRootUriString: String)

    @Query("DELETE FROM video WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM video WHERE uriString IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("UPDATE video SET coverFilePath = REPLACE(coverFilePath, :oldPrefix, :newPrefix) WHERE coverFilePath LIKE :oldLike")
    suspend fun updateCoverDir(oldPrefix: String, newPrefix: String, oldLike: String)

    @Query("DELETE FROM video")
    suspend fun clearAll()
}
