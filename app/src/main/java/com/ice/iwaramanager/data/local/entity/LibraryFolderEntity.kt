package com.ice.iwaramanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "library_folder",
    primaryKeys = ["sourceId", "path"],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["sourceId", "parentPath"])
    ]
)
data class LibraryFolderEntity(
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val parentPath: String?,
    val name: String,
    val updatedAt: Long
)
