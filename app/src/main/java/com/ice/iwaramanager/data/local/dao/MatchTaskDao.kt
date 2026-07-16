package com.ice.iwaramanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ice.iwaramanager.data.local.entity.MatchCandidateEntity
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import kotlinx.coroutines.flow.Flow

private const val ACTIVE_TASK_VIDEO_FILTER = """
AND (
    NOT EXISTS (SELECT 1 FROM library_source)
    OR EXISTS (
        SELECT 1
        FROM video AS active_video
        INNER JOIN library_source AS active_source ON active_source.id = active_video.sourceId
        WHERE active_video.uriString = match_task.videoUriString
        AND (
            active_source.lastCompletedScanAt IS NULL
            OR active_video.lastSeenAt >= active_source.lastCompletedScanAt
        )
    )
)
"""

private const val MATCH_TASK_WITH_CURRENT_COVER = """
SELECT
    match_task.id AS id,
    match_task.libraryRootUriString AS libraryRootUriString,
    match_task.videoUriString AS videoUriString,
    match_task.displayName AS displayName,
    COALESCE(
        NULLIF((
            SELECT current_video.coverFilePath
            FROM video AS current_video
            WHERE current_video.uriString = match_task.videoUriString
            LIMIT 1
        ), ''),
        match_task.coverFilePath
    ) AS coverFilePath,
    match_task.`query` AS `query`,
    match_task.localDurationMs AS localDurationMs,
    match_task.status AS status,
    match_task.matchedIwaraId AS matchedIwaraId,
    match_task.candidateCount AS candidateCount,
    match_task.errorMessage AS errorMessage,
    match_task.createdAt AS createdAt,
    match_task.updatedAt AS updatedAt
FROM match_task
"""

data class MatchTaskStatusCount(
    val status: String,
    val count: Int
)

@Dao
interface MatchTaskDao {
    @Query(
        MATCH_TASK_WITH_CURRENT_COVER + """
        WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
        """ + ACTIVE_TASK_VIDEO_FILTER + """
        ORDER BY updatedAt DESC, id DESC
        """
    )
    fun observeTasksForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<MatchTaskEntity>>

    @Query(
        MATCH_TASK_WITH_CURRENT_COVER + """
        WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
        """ + ACTIVE_TASK_VIDEO_FILTER + """
        AND status IN (:statuses)
        ORDER BY updatedAt DESC, id DESC
        """
    )
    fun observeTasksByStatusesForSourceIds(sourceIds: List<String>, sourceCount: Int, statuses: List<String>): Flow<List<MatchTaskEntity>>

    @Query(
        MATCH_TASK_WITH_CURRENT_COVER + """
        WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
        """ + ACTIVE_TASK_VIDEO_FILTER + """
        AND status IN (:statuses)
        ORDER BY updatedAt DESC, id DESC
        """
    )
    suspend fun getTasksByStatusesForSourceIds(sourceIds: List<String>, sourceCount: Int, statuses: List<String>): List<MatchTaskEntity>

    @Query(
        """
        SELECT status, COUNT(*) AS count FROM match_task
        WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
        """ + ACTIVE_TASK_VIDEO_FILTER + """
        GROUP BY status
        """
    )
    fun observeStatusCountsForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<MatchTaskStatusCount>>

    @Insert
    suspend fun insertTask(task: MatchTaskEntity): Long

    @Update
    suspend fun updateTask(task: MatchTaskEntity)

    @Query("UPDATE match_task SET status = :failedStatus, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE status = :runningStatus")
    suspend fun markRunningTasksAsFailed(runningStatus: String, failedStatus: String, errorMessage: String, updatedAt: Long)

    @Query(MATCH_TASK_WITH_CURRENT_COVER + " WHERE match_task.id = :taskId LIMIT 1")
    suspend fun getTask(taskId: Long): MatchTaskEntity?

    @Query("DELETE FROM match_task WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    @Query(MATCH_TASK_WITH_CURRENT_COVER + " WHERE match_task.id = :taskId LIMIT 1")
    fun observeTask(taskId: Long): Flow<MatchTaskEntity?>

    @Query(MATCH_TASK_WITH_CURRENT_COVER + " WHERE match_task.videoUriString = :videoUriString ORDER BY match_task.updatedAt DESC")
    suspend fun getTasksByVideoUri(videoUriString: String): List<MatchTaskEntity>

    @Query(
        """
        UPDATE match_task SET
            libraryRootUriString = :libraryRootUriString,
            videoUriString = :newUriString,
            displayName = :displayName,
            coverFilePath = COALESCE(:coverFilePath, coverFilePath),
            localDurationMs = COALESCE(:localDurationMs, localDurationMs),
            updatedAt = :updatedAt
        WHERE videoUriString = :oldUriString
        """
    )
    suspend fun migrateVideoUri(
        oldUriString: String,
        newUriString: String,
        libraryRootUriString: String,
        displayName: String,
        coverFilePath: String?,
        localDurationMs: Long?,
        updatedAt: Long
    )

    @Query("UPDATE match_task SET coverFilePath = :coverFilePath WHERE videoUriString = :videoUriString")
    suspend fun updateCoverForVideo(videoUriString: String, coverFilePath: String)

    @Query("DELETE FROM match_candidate WHERE taskId = :taskId")
    suspend fun deleteCandidatesForTask(taskId: Long)

    @Query("DELETE FROM match_candidate WHERE taskId IN (SELECT id FROM match_task WHERE videoUriString IN (:videoUriStrings))")
    suspend fun deleteCandidatesForVideos(videoUriStrings: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<MatchCandidateEntity>)

    @Query("SELECT * FROM match_candidate WHERE taskId = :taskId ORDER BY id ASC")
    fun observeCandidatesForTask(taskId: Long): Flow<List<MatchCandidateEntity>>

    @Query(
        MATCH_TASK_WITH_CURRENT_COVER + """
        WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
        """ + ACTIVE_TASK_VIDEO_FILTER + """
        AND status = :status
        AND (updatedAt < :currentUpdatedAt OR (updatedAt = :currentUpdatedAt AND id < :currentTaskId))
        ORDER BY updatedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getNextTaskByStatusAfterCursor(
        sourceIds: List<String>,
        sourceCount: Int,
        status: String,
        currentUpdatedAt: Long,
        currentTaskId: Long
    ): MatchTaskEntity?

    @Query(
        MATCH_TASK_WITH_CURRENT_COVER + """
        WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
        """ + ACTIVE_TASK_VIDEO_FILTER + """
        AND status = :status
        AND id != :currentTaskId
        ORDER BY updatedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getFirstTaskByStatus(
        sourceIds: List<String>,
        sourceCount: Int,
        status: String,
        currentTaskId: Long
    ): MatchTaskEntity?

    @Query("DELETE FROM match_task WHERE videoUriString IN (:videoUriStrings)")
    suspend fun deleteTasksForVideos(videoUriStrings: List<String>)

    @Query("DELETE FROM match_task")
    suspend fun clearTasks()
}
