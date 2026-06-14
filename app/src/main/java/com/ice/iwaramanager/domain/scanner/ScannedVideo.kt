package com.ice.iwaramanager.domain.scanner

data class ScannedVideo(
    val displayName: String,
    val uriString: String,
    val fileSize: Long,
    val lastModified: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val coverFilePath: String?
)