package com.ice.iwaramanager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.iwaramanager.VideoDetailUiState
import com.ice.iwaramanager.data.model.VideoItem
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    state: VideoDetailUiState,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onMatch: (VideoItem) -> Unit,
    onTagClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    val video = state.video
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                title = { Text("视频详情") }
            )
        }
    ) { paddingValues ->
        if (video == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("没有选中的视频。")
            }
            return@Scaffold
        }

        val remoteTags = remoteTags(video.remoteRawJson)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderCard(video)

            ActionRow(
                video = video,
                onPlay = onPlay,
                onMatch = onMatch,
                onOpenWeb = {
                    val id = video.matchedIwaraId ?: video.sourceVideoId
                    if (!id.isNullOrBlank()) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.iwara.tv/video/$id")
                            )
                        )
                    }
                }
            )

            VideoInfoTabsCard(video)

            AuthorCard(
                video = video,
                onAuthorClick = onAuthorClick,
                onOpenAuthorWeb = { username ->
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.iwara.tv/profile/$username")
                        )
                    )
                }
            )

            TagSectionCard(
                tags = remoteTags,
                onTagClick = onTagClick
            )

            if (!video.remoteDescription.isNullOrBlank()) {
                DescriptionCard(video.remoteDescription)
            }
        }
    }
}

@Composable
private fun HeaderCard(
    video: VideoItem
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            VideoPreview(
                video = video,
                modifier = Modifier
                    .width(148.dp)
                    .height(83.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = video.remoteTitle ?: video.title ?: video.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "本地时长：${formatDuration(video.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(
    video: VideoItem,
    onPlay: (VideoItem) -> Unit,
    onMatch: (VideoItem) -> Unit,
    onOpenWeb: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = { onPlay(video) }
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放"
            )
        }

        if (video.matchedIwaraId == null) {
            FilledTonalButton(
                onClick = { onMatch(video) }
            ) {
                Text("匹配元数据")
            }
        } else {
            FilledTonalIconButton(
                onClick = { onMatch(video) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "重新匹配"
                )
            }

            FilledTonalIconButton(
                onClick = onOpenWeb
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = "在浏览器中打开"
                )
            }
        }
    }
}

@Composable
private fun VideoInfoTabsCard(
    video: VideoItem
) {
    var selectedTab by remember(video.uriString) { mutableStateOf(0) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "信息",
                style = MaterialTheme.typography.titleSmall
            )
            InfoModeSelector(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it }
            )

            if (selectedTab == 0) {
                LocalInfoContent(video)
            } else {
                RemoteInfoContent(video)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoModeSelector(
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { onSelect(0) },
                label = { Text("本地文件") },
                modifier = Modifier.height(32.dp)
            )
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FilterChip(
                selected = selectedTab == 1,
                onClick = { onSelect(1) },
                label = { Text("远程元数据") },
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
private fun LocalInfoContent(
    video: VideoItem
) {
    InfoLine("标题", video.title ?: "未解析")
    InfoLine("文件名", video.displayName)
    InfoLine("Iwara ID", video.sourceVideoId ?: "未解析")
    InfoLine("质量", video.quality ?: "未知")
    InfoLine("时长", formatDuration(video.durationMs))
    InfoLine(
        "分辨率",
        if (video.width != null && video.height != null) {
            "${video.width}x${video.height}"
        } else {
            "未知"
        }
    )
    InfoLine("大小", formatFileSize(video.fileSize))
    InfoLine("修改时间", formatDateTime(video.lastModified))
}

@Composable
private fun RemoteInfoContent(
    video: VideoItem
) {
    InfoLine("状态", matchStatusLabel(video.matchStatus))
    InfoLine("Iwara ID", video.matchedIwaraId ?: "未匹配")
    InfoLine("标题", video.remoteTitle ?: "未匹配")
    InfoLine(
        "远程时长",
        video.remoteDurationSeconds?.let { formatDuration(it * 1000L) } ?: "未知"
    )
    InfoLine("分级", video.remoteRating ?: "未知")
    InfoLine("可见性", video.remoteVisibility ?: "未知")
    if (video.remoteViewCount != null || video.remoteLikeCount != null || video.remoteCommentCount != null) {
        InfoLine(
            "统计",
            listOfNotNull(
                video.remoteViewCount?.let { "播放 $it" },
                video.remoteLikeCount?.let { "点赞 $it" },
                video.remoteCommentCount?.let { "评论 $it" }
            ).joinToString(" · ")
        )
    }
    InfoLine("发布时间", formatRemoteDateTime(video.remoteCreatedAt))
    InfoLine("更新时间", formatRemoteDateTime(video.remoteUpdatedAt))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorCard(
    video: VideoItem,
    onAuthorClick: (String) -> Unit,
    onOpenAuthorWeb: (String) -> Unit
) {
    val username = video.remoteAuthorUsername?.takeIf { it.isNotBlank() }
    val authorId = video.remoteAuthorId
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { id ->
            username != null && id.equals(username, ignoreCase = true)
        }
    val authorLabel = authorLabel(video)
    val authorKey = username
        ?: authorId
        ?: video.remoteAuthorName?.takeIf { it.isNotBlank() }

    InfoCard(title = null) {
        if (authorKey != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthorAvatar(
                    avatarUrl = video.remoteAuthorAvatarUrl,
                    label = authorLabel
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        FilterChip(
                            selected = false,
                            onClick = { onAuthorClick(authorKey) },
                            label = {
                                Text(
                                    text = authorLabel,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    Text(
                        text = username ?: "用户名未知",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = { username?.let(onOpenAuthorWeb) },
                    enabled = username != null
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "打开作者主页"
                    )
                }
            }
        } else {
            Text(
                text = "未知",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSectionCard(
    tags: List<RemoteTag>,
    onTagClick: (String) -> Unit
) {
    InfoCard(title = "标签") {
        if (tags.isEmpty()) {
            Text(
                text = "暂无标签。重新匹配后会尝试补全。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tags.forEach { tag ->
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        FilterChip(
                            selected = false,
                            onClick = { onTagClick(tag.key) },
                            label = {
                                Text(
                                    text = tag.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DescriptionCard(
    description: String
) {
    var expanded by remember(description) { mutableStateOf(false) }
    var canExpand by remember(description) { mutableStateOf(false) }

    InfoCard(title = "简介") {
        Text(
            text = description,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) {
                    canExpand = result.hasVisualOverflow
                }
            }
        )
        if (canExpand || expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            content()
        }
    }
}

@Composable
private fun AuthorAvatar(
    avatarUrl: String?,
    label: String
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun VideoPreview(
    video: VideoItem,
    modifier: Modifier
) {
    val coverPath = video.coverFilePath

    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = video.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "无预览",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label：",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class RemoteTag(
    val key: String,
    val label: String
)

private fun remoteTags(rawJson: String?): List<RemoteTag> {
    if (rawJson.isNullOrBlank()) return emptyList()
    return runCatching {
        val root = JSONObject(rawJson)
        val seen = linkedMapOf<String, RemoteTag>()

        fun collectTags(array: org.json.JSONArray?) {
            if (array == null) return
            for (index in 0 until array.length()) {
                val tag = array.optJSONObject(index) ?: continue
                val namespace = tag.optString("namespace").ifBlank { "tag" }
                val name = tag.optString("name")
                    .ifBlank { tag.optString("slug") }
                    .ifBlank { tag.optString("id") }
                    .trim()
                if (name.isBlank()) continue
                val key = "$namespace:$name".lowercase()
                seen[key] = RemoteTag(key = key, label = name)
            }
        }

        root.optJSONArray("results")?.let { results ->
            for (i in 0 until results.length()) {
                collectTags(results.optJSONObject(i)?.optJSONArray("tags"))
            }
        }
        collectTags(root.optJSONObject("pageMeta")?.optJSONArray("tags"))
        seen.values.toList()
    }.getOrDefault(emptyList())
}

private fun authorLabel(video: VideoItem): String {
    return formatAuthorLabel(video.remoteAuthorName, video.remoteAuthorUsername)
        ?: video.remoteAuthorId?.takeIf { it.isNotBlank() }
        ?: "未知"
}

private fun matchStatusLabel(status: String): String {
    return when (status) {
        "manual_matched" -> "手动匹配"
        "auto_matched" -> "自动匹配"
        "unmatched" -> "未匹配"
        else -> status
    }
}
