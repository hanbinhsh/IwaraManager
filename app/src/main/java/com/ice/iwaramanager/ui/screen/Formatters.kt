package com.ice.iwaramanager.ui.screen

import com.ice.iwaramanager.data.model.VideoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(
    durationMs: Long?
): String {
    if (durationMs == null || durationMs <= 0L) {
        return "未知时长"
    }

    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60 % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun formatFileSize(
    bytes: Long
): String {
    val mb = bytes / 1024.0 / 1024.0
    val gb = mb / 1024.0

    return if (gb >= 1.0) {
        "%.2f GB".format(gb)
    } else {
        "%.1f MB".format(mb)
    }
}

fun formatDateTime(
    millis: Long
): String {
    if (millis <= 0L) {
        return "未知时间"
    }

    return SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.getDefault()
    ).format(Date(millis))
}

fun buildVideoInfoLine(
    video: VideoItem
): String {
    return listOfNotNull(
        video.quality,
        video.sourceVideoId?.let { "ID:$it" },
        formatDuration(video.durationMs).takeIf { it != "未知时长" },
        if (video.width != null && video.height != null) {
            "${video.width}x${video.height}"
        } else {
            null
        },
        formatFileSize(video.fileSize)
    ).joinToString(" · ").ifBlank {
        video.extension ?: "本地视频"
    }
}