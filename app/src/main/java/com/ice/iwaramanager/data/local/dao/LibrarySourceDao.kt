package com.ice.iwaramanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ice.iwaramanager.data.local.entity.LibraryFolderEntity
import com.ice.iwaramanager.data.local.entity.LibrarySourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibrarySourceDao {
    @Query("SELECT * FROM library_source ORDER BY type ASC, name COLLATE NOCASE ASC")
    fun observeSources(): Flow<List<LibrarySourceEntity>>

    @Query("SELECT * FROM library_source ORDER BY type ASC, name COLLATE NOCASE ASC")
    suspend fun getSources(): List<LibrarySourceEntity>

    @Query("SELECT * FROM library_source WHERE id = :sourceId LIMIT 1")
    suspend fun getSource(sourceId: String): LibrarySourceEntity?

    @Query("SELECT * FROM library_source WHERE rootUriString = :rootUriString LIMIT 1")
    suspend fun getSourceByRoot(rootUriString: String): LibrarySourceEntity?

    @Upsert
    suspend fun upsertSource(source: LibrarySourceEntity)

    @Query("DELETE FROM library_source WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: String)

    @Query("SELECT * FROM library_folder WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds)) ORDER BY sourceName COLLATE NOCASE ASC, path COLLATE NOCASE ASC")
    fun observeFoldersForSourceIds(sourceIds: List<String>, sourceCount: Int): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folder WHERE sourceId = :sourceId AND COALESCE(parentPath, '') = :parentPath ORDER BY name COLLATE NOCASE ASC")
    fun observeChildFolders(sourceId: String, parentPath: String): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folder WHERE sourceId = :sourceId AND COALESCE(parentPath, '') = :parentPath ORDER BY name COLLATE NOCASE ASC")
    suspend fun getChildFolders(sourceId: String, parentPath: String): List<LibraryFolderEntity>

    @Query("DELETE FROM library_folder WHERE sourceId = :sourceId")
    suspend fun deleteFoldersForSource(sourceId: String)

    @Upsert
    suspend fun upsertFolders(folders: List<LibraryFolderEntity>)

    @Query("DELETE FROM library_folder")
    suspend fun clearFolders()
}
