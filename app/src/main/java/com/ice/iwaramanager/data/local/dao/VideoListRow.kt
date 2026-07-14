package com.ice.iwaramanager.data.local.dao

/**
 * 列表/网格/搜索用的轻量投影行：只包含渲染与点击操作所需的列，
 * 不读取 remoteRawJson、remoteDescription 等大字段，显著降低大库的内存与查询开销。
 * 详情页所需的完整数据由 VideoRepository.findVideoByUri 按需获取。
 */
data class VideoListRow(
    val uriString: String,
    val libraryRootUriString: String,
    val sourceId: String,
    val relativePath: String?,
    val parentPath: String?,
    val displayName: String,
    val fileSize: Long,
    val lastModified: Long,
    val title: String?,
    val sourceVideoId: String?,
    val quality: String?,
    val extension: String?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val coverFilePath: String?,
    val matchedIwaraId: String?,
    val matchStatus: String
)
