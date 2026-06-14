package com.ice.iwaramanager.data.model

data class VideoItem(
    val uriString: String,
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

    val sourceId: String = "",
    val relativePath: String? = null,
    val parentPath: String? = null,

    val matchedIwaraId: String? = null,
    val remoteTitle: String? = null,
    val remoteDescription: String? = null,
    val remoteAuthorId: String? = null,
    val remoteAuthorName: String? = null,
    val remoteAuthorUsername: String? = null,
    val remoteAuthorAvatarUrl: String? = null,
    val remoteThumbnailUrl: String? = null,
    val remoteRating: String? = null,
    val remoteVisibility: String? = null,
    val remoteCreatedAt: String? = null,
    val remoteUpdatedAt: String? = null,
    val remoteDurationSeconds: Long? = null,
    val remoteLikeCount: Int? = null,
    val remoteViewCount: Int? = null,
    val remoteCommentCount: Int? = null,
    val remoteRawJson: String? = null,
    val matchStatus: String = "unmatched"
)
