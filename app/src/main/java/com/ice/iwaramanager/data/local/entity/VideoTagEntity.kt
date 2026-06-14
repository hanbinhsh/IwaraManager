package com.ice.iwaramanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "video_tag",
    primaryKeys = ["videoUriString", "tagKey"],
    indices = [
        Index(value = ["videoUriString"]),
        Index(value = ["tagKey"])
    ]
)
data class VideoTagEntity(
    val videoUriString: String,
    val tagKey: String
)
