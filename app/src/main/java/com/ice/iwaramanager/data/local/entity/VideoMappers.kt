package com.ice.iwaramanager.data.local.entity

import com.ice.iwaramanager.data.model.VideoItem

fun VideoEntity.toVideoItem(): VideoItem {
    return VideoItem(
        uriString = uriString,
        displayName = displayName,
        fileSize = fileSize,
        lastModified = lastModified,
        title = title,
        sourceVideoId = sourceVideoId,
        quality = quality,
        extension = extension,
        durationMs = durationMs,
        width = width,
        height = height,
        coverFilePath = coverFilePath,
        sourceId = sourceId.ifBlank { libraryRootUriString },
        relativePath = relativePath,
        parentPath = parentPath,
        matchedIwaraId = matchedIwaraId,
        remoteTitle = remoteTitle,
        remoteDescription = remoteDescription,
        remoteAuthorId = remoteAuthorId,
        remoteAuthorName = remoteAuthorName,
        remoteAuthorUsername = remoteAuthorUsername,
        remoteAuthorAvatarUrl = remoteAuthorAvatarUrl,
        remoteThumbnailUrl = remoteThumbnailUrl,
        remoteRating = remoteRating,
        remoteVisibility = remoteVisibility,
        remoteCreatedAt = remoteCreatedAt,
        remoteUpdatedAt = remoteUpdatedAt,
        remoteDurationSeconds = remoteDurationSeconds,
        remoteLikeCount = remoteLikeCount,
        remoteViewCount = remoteViewCount,
        remoteCommentCount = remoteCommentCount,
        remoteRawJson = remoteRawJson,
        matchStatus = matchStatus
    )
}
