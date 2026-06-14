package com.ice.iwaramanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "iwara_tag",
    indices = [
        Index(value = ["namespace", "name"], unique = true)
    ]
)
data class IwaraTagEntity(
    @PrimaryKey
    val key: String,
    val namespace: String,
    val name: String
)
