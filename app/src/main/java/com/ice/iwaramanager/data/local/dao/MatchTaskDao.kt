package com.ice.iwaramanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ice.iwaramanager.data.local.entity.MatchCandidateEntity
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import kotlinx.coroutines.flow.Flow

data class MatchTaskStatusCount(
    val status: String,
    val count: Int
)

@Dao
interface MatchTaskDao {
    @Query("SELECT * FROM match_task WHERE libraryRootUriString = :libraryRootUriString ORDER BY updatedAt DESC, id DESC")
    fun observeTasks(libraryRootUriString: String): Flow<List<MatchTaskEntity>>

    @Query("SELECT * FROM match_task WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds)) ORDER BY updatedAt DESC, id DESC")
    fun observeTasksForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<MatchTaskEntity>>

    @Query("SELECT * FROM match_task WHERE libraryRootUriString = :libraryRootUriString AND status IN (:statuses) ORDER BY updatedAt DESC, id DESC")
    fun observeTasksByStatuses(libraryRootUriString: String, statuses: List<String>): Flow<List<MatchTaskEntity>>

    @Query("SELECT * FROM match_task WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds)) AND status IN (:statuses) ORDER BY updatedAt DESC, id DESC")
    fun observeTasksByStatusesForSourceIds(sourceIds: List<String>, sourceCount: Int, statuses: List<String>): Flow<List<MatchTaskEntity>>

    @Query("SELECT * FROM match_task WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds)) AND status IN (:statuses) ORDER BY updatedAt DESC, id DESC")
    suspend fun getTasksByStatusesForSourceIds(sourceIds: List<String>, sourceCount: Int, statuses: List<String>): List<MatchTaskEntity>

    @Query("SELECT status, COUNT(*) AS count FROM match_task WHERE libraryRootUriString = :libraryRootUriString GROUP BY status")
    fun observeStatusCounts(libraryRootUriString: String): Flow<List<MatchTaskStatusCount>>

    @Query("SELECT status, COUNT(*) AS count FROM match_task WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds)) GROUP BY status")
    fun observeStatusCountsForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<MatchTaskStatusCount>>

    @Insert
    suspend fun insertTask(task: MatchTaskEntity): Long

    @Update
    suspend fun updateTask(task: MatchTaskEntity)

    @Query("UPDATE match_task SET status = :failedStatus, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE status = :runningStatus")
    suspend fun markRunningTasksAsFailed(runningStatus: String, failedStatus: String, errorMessage: String, updatedAt: Long)

    @Query("SELECT * FROM match_task WHERE id = :taskId LIMIT 1")
    suspend fun getTask(taskId: Long): MatchTaskEntity?

    @Query("DELETE FROM match_task WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    @Query("SELECT * FROM match_task WHERE id = :taskId LIMIT 1")
    fun observeTask(taskId: Long): Flow<MatchTaskEntity?>

    @Query("SELECT * FROM match_task WHERE videoUriString = :videoUriString ORDER BY updatedAt DESC")
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

    @Query("DELETE FROM match_candidate WHERE taskId = :taskId")
    suspend fun deleteCandidatesForTask(taskId: Long)

    @Query("DELETE FROM match_candidate WHERE taskId IN (SELECT id FROM match_task WHERE videoUriString IN (:videoUriStrings))")
    suspend fun deleteCandidatesForVideos(videoUriStrings: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<MatchCandidateEntity>)

    @Query("SELECT * FROM match_candidate WHERE taskId = :taskId ORDER BY id ASC")
    fun observeCandidatesForTask(taskId: Long): Flow<List<MatchCandidateEntity>>

    @Query("SELECT * FROM match_candidate WHERE taskId = :taskId ORDER BY id ASC")
    suspend fun getCandidatesForTask(taskId: Long): List<MatchCandidateEntity>

    @Query(
        """
        SELECT * FROM match_task
        WHERE libraryRootUriString = :libraryRootUriString
        AND status = :status
        AND (updatedAt < :currentUpdatedAt OR (updatedAt = :currentUpdatedAt AND id < :currentTaskId))
        ORDER BY updatedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getNextTaskByStatusAfterCursor(
        libraryRootUriString: String,
        status: String,
        currentUpdatedAt: Long,
        currentTaskId: Long
    ): MatchTaskEntity?

    @Query(
        """
        SELECT * FROM match_task
        WHERE libraryRootUriString = :libraryRootUriString
        AND status = :status
        AND id != :currentTaskId
        ORDER BY updatedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getFirstTaskByStatus(libraryRootUriString: String, status: String, currentTaskId: Long): MatchTaskEntity?

    @Query("DELETE FROM match_task WHERE libraryRootUriString = :sourceId")
    suspend fun deleteTasksBySource(sourceId: String)

    @Query("DELETE FROM match_task WHERE videoUriString IN (:videoUriStrings)")
    suspend fun deleteTasksForVideos(videoUriStrings: List<String>)

    @Query("DELETE FROM match_task")
    suspend fun clearTasks()
}
