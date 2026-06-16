package com.ice.iwaramanager.domain.scanner

data class ScannedVideo(
    val displayName: String,
    val uriString: String,
    val fileSize: Long,
    val lastModified: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val coverFilePath: String?,
    val relativePath: String? = null,
    val parentPath: String? = null
)

/**
 * 数据库中已存在的视频快照，用于增量扫描：
 * 若文件大小与修改时间未变化且已有时长和封面，则跳过昂贵的元数据/封面读取。
 */
data class KnownVideo(
    val fileSize: Long,
    val lastModified: Long,
    val hasDuration: Boolean,
    val coverFilePath: String?
)
