package com.ice.iwaramanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "match_candidate",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["iwaraId"])
    ]
)
data class MatchCandidateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val iwaraId: String,
    val title: String,
    val authorName: String?,
    val authorUsername: String?,
    val thumbnailUrl: String?,
    val rating: String?,
    val createdAtText: String?,
    val durationSeconds: Long?,
    val viewCount: Int?,
    val likeCount: Int?,
    val selected: Boolean = false,
    val rawJson: String? = null
)
