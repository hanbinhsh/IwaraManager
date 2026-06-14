package com.ice.iwaramanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ice.iwaramanager.data.local.entity.IwaraTagEntity
import com.ice.iwaramanager.data.local.entity.VideoTagEntity
import kotlinx.coroutines.flow.Flow

data class CountItem(
    val itemKey: String,
    val label: String,
    val count: Int
) {
    val key: String
        get() = itemKey
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(tags: List<IwaraTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoTags(videoTags: List<VideoTagEntity>)

    @Query("DELETE FROM video_tag WHERE videoUriString = :videoUriString")
    suspend fun deleteTagsForVideo(videoUriString: String)

    @Query(
        """
        SELECT t.`key` AS itemKey, t.name AS label, COUNT(*) AS count
        FROM iwara_tag t
        INNER JOIN video_tag vt ON vt.tagKey = t.key
        INNER JOIN video v ON v.uriString = vt.videoUriString
        WHERE v.libraryRootUriString = :libraryRootUriString
        GROUP BY t.key, t.name
        ORDER BY count DESC, t.name ASC
        """
    )
    fun observeTagCounts(libraryRootUriString: String): Flow<List<CountItem>>
}
