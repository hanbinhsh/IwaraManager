package com.ice.iwaramanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ice.iwaramanager.data.local.dao.CountItem
import com.ice.iwaramanager.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        ORDER BY updatedAt DESC
        """
    )
    fun observeVideos(
        libraryRootUriString: String
    ): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
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
        ORDER BY updatedAt DESC
        """
    )
    fun observeVideosBySearch(
        libraryRootUriString: String,
        query: String
    ): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (:authorKeyCount = 0 OR COALESCE(remoteAuthorUsername, remoteAuthorId, remoteAuthorName, '') IN (:authorKeys))
        AND (:qualityCount = 0 OR COALESCE(quality, '') IN (:qualities))
        AND (:tagCount = 0 OR uriString IN (
            SELECT videoUriString
            FROM video_tag
            WHERE tagKey IN (:tagKeys)
            GROUP BY videoUriString
            HAVING COUNT(DISTINCT tagKey) = :tagCount
        ))
        ORDER BY updatedAt DESC
        """
    )
    fun observeVideosByFilters(
        libraryRootUriString: String,
        tagKeys: List<String>,
        tagCount: Int,
        authorKeys: List<String>,
        authorKeyCount: Int,
        qualities: List<String>,
        qualityCount: Int
    ): Flow<List<VideoEntity>>

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
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (
            SELECT videoUriString FROM match_task WHERE libraryRootUriString = :libraryRootUriString
        )
        ORDER BY updatedAt DESC
        """
    )
    fun observeUnqueuedUnmatchedVideos(libraryRootUriString: String): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (
            SELECT videoUriString FROM match_task WHERE libraryRootUriString = :libraryRootUriString
        )
        """
    )
    fun observeUnqueuedUnmatchedVideoCount(libraryRootUriString: String): Flow<Int>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (matchedIwaraId IS NULL OR matchedIwaraId = '')
        AND uriString NOT IN (
            SELECT videoUriString FROM match_task WHERE libraryRootUriString = :libraryRootUriString
        )
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getUnqueuedUnmatchedVideos(libraryRootUriString: String): List<VideoEntity>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND matchedIwaraId IS NOT NULL
        AND matchedIwaraId != ''
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getMatchedVideos(libraryRootUriString: String): List<VideoEntity>

    @Query(
        """
        SELECT * FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND (
            (matchedIwaraId IS NOT NULL AND matchedIwaraId != '')
            OR (sourceVideoId IS NOT NULL AND sourceVideoId != '')
        )
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getRematchableVideos(libraryRootUriString: String): List<VideoEntity>

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

    @Query("SELECT * FROM video WHERE libraryRootUriString = :libraryRootUriString")
    suspend fun findAllByRoot(
        libraryRootUriString: String
    ): List<VideoEntity>

    @Query("SELECT * FROM video WHERE uriString = :uriString LIMIT 1")
    suspend fun findByUri(
        uriString: String
    ): VideoEntity?

    @Upsert
    suspend fun upsertAll(
        videos: List<VideoEntity>
    )

    @Query("DELETE FROM video WHERE libraryRootUriString = :libraryRootUriString")
    suspend fun deleteByRoot(
        libraryRootUriString: String
    )

    @Query(
        """
        DELETE FROM video
        WHERE libraryRootUriString = :libraryRootUriString
        AND uriString NOT IN (:existingUris)
        """
    )
    suspend fun deleteMissingByRoot(
        libraryRootUriString: String,
        existingUris: List<String>
    )

    @Query("DELETE FROM video")
    suspend fun clearAll()
}
