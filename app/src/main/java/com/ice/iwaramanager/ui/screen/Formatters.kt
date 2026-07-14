package com.ice.iwaramanager.ui.screen

import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.MatchTaskStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val DisplayDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

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
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date(millis))
}

fun formatRemoteDateTime(
    value: String?
): String {
    if (value.isNullOrBlank()) return "未知"
    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .format(DisplayDateTimeFormatter)
    }.recoverCatching {
        LocalDateTime.parse(value.substringBeforeLast("Z"))
            .atZone(ZoneId.systemDefault())
            .format(DisplayDateTimeFormatter)
    }.getOrElse {
        value.replace('T', ' ')
            .removeSuffix("Z")
            .substringBefore(".")
    }
}

fun buildVideoInfoLine(
    video: VideoItem
): String {
    return listOfNotNull(
        video.quality,
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

fun formatAuthorLabel(
    authorName: String?,
    authorUsername: String?
): String? {
    val name = authorName?.takeIf { it.isNotBlank() && it != authorUsername }
    val username = authorUsername?.takeIf { it.isNotBlank() }
    return when {
        name != null && username != null -> "$name $username"
        name != null -> name
        username != null -> username
        else -> null
    }
}

fun durationMatched(
    localDurationMs: Long?,
    remoteDurationSeconds: Long?,
    toleranceSeconds: Long = 2L
): Boolean? {
    val localSeconds = localDurationMs?.takeIf { it > 0L }?.div(1000L) ?: return null
    val remoteSeconds = remoteDurationSeconds?.takeIf { it > 0L } ?: return null
    return abs(localSeconds - remoteSeconds) <= toleranceSeconds
}

fun formatDurationComparison(
    localDurationMs: Long?,
    remoteDurationSeconds: Long?
): String {
    val local = localDurationMs?.takeIf { it > 0L }
    val remote = remoteDurationSeconds?.takeIf { it > 0L }?.times(1000L)
    return when {
        local != null && remote != null -> {
            val matched = durationMatched(localDurationMs, remoteDurationSeconds) == true
            "时长${if (matched) "一致" else "不一致"}：本地 ${formatDuration(local)} / 候选 ${formatDuration(remote)}"
        }
        local != null -> "本地时长：${formatDuration(local)} / 候选时长：未知"
        remote != null -> "本地时长：未知 / 候选 ${formatDuration(remote)}"
        else -> "时长：未知"
    }
}

fun matchTaskStatusLabel(status: String): String = when (status) {
    MatchTaskStatus.Pending -> "等待中"
    MatchTaskStatus.Running -> "匹配中"
    MatchTaskStatus.AutoMatched -> "成功"
    MatchTaskStatus.NeedReview -> "需复核"
    MatchTaskStatus.Failed -> "失败"
    MatchTaskStatus.Skipped -> "跳过"
    else -> status
}
