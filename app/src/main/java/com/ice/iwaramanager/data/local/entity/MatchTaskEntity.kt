package com.ice.iwaramanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "match_task",
    indices = [
        Index(value = ["libraryRootUriString"]),
        Index(value = ["videoUriString"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class MatchTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val libraryRootUriString: String,
    val videoUriString: String,
    val displayName: String,
    val coverFilePath: String?,
    val query: String,
    val localDurationMs: Long?,
    val status: String,
    val matchedIwaraId: String?,
    val candidateCount: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
