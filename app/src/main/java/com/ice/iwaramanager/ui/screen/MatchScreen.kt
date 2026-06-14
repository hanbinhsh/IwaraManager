package com.ice.iwaramanager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.iwaramanager.MatchUiState
import com.ice.iwaramanager.data.model.IwaraVideoMeta
import com.ice.iwaramanager.data.model.VideoItem
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    state: MatchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onIdMatch: () -> Unit,
    onOpenWeb: () -> Unit,
    onToggleSearchDiagnosticRaw: () -> Unit,
    onBind: (IwaraVideoMeta) -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("匹配 Iwara 元数据") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MatchVideoHeader(
                state = state,
                onSearch = onSearch,
                onIdMatch = onIdMatch,
                onOpenWeb = onOpenWeb,
                onPlay = onPlay
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("搜索标题 / Iwara ID") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            if (state.isSearching) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text("正在搜索候选...")
                }
            }

            if (state.isBinding) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text("正在绑定并补全元数据...")
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (state.searchDiagnosticSummary != null) {
                SearchDiagnostic(
                    summary = state.searchDiagnosticSummary,
                    raw = state.searchDiagnosticRaw,
                    showRaw = state.showSearchDiagnosticRaw,
                    onToggleRaw = onToggleSearchDiagnosticRaw
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.candidates) { meta ->
                    CandidateItem(
                        meta = meta,
                        localDurationMs = state.video?.durationMs,
                        enabled = !state.isSearching && !state.isBinding,
                        onBind = { onBind(meta) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchVideoHeader(
    state: MatchUiState,
    onSearch: () -> Unit,
    onIdMatch: () -> Unit,
    onOpenWeb: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    val video = state.video ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            MatchVideoPreview(video = video)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = video.displayName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID：${video.sourceVideoId ?: "未解析"} · 本地时长：${formatDuration(video.durationMs)} · 已匹配：${video.matchedIwaraId ?: "无"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalIconButton(
                onClick = { onPlay(video) },
                enabled = !state.isSearching && !state.isBinding
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
            }
            FilledTonalIconButton(
                onClick = onSearch,
                enabled = !state.isSearching && !state.isBinding
            ) {
                Icon(Icons.Filled.Search, contentDescription = "搜索")
            }
            OutlinedButton(
                onClick = onIdMatch,
                enabled = !state.isSearching && !state.isBinding
            ) {
                Text("ID 匹配")
            }
            OutlinedButton(
                onClick = onOpenWeb,
                enabled = !state.isSearching && !state.isBinding && state.query.isNotBlank()
            ) {
                Icon(Icons.Filled.Public, contentDescription = "打开网页")
            }
        }
    }
}

@Composable
private fun MatchVideoPreview(video: VideoItem) {
    val coverPath = video.coverFilePath
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = video.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(132.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(74.dp)
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
private fun CandidateItem(
    meta: IwaraVideoMeta,
    localDurationMs: Long?,
    enabled: Boolean,
    onBind: () -> Unit
) {
    val context = LocalContext.current
    val durationText = durationCompareText(localDurationMs, meta.durationSeconds)
    val durationMatched = durationMatched(localDurationMs, meta.durationSeconds)
    ListItem(
        headlineContent = {
            Text(meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                val author = authorLabel(meta.authorName, meta.authorUsername)
                val info = listOfNotNull(
                    author,
                    meta.rating,
                    meta.createdAt,
                    "ID:${meta.id}"
                ).joinToString(" · ")
                Text(info, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = durationText,
                    color = if (durationMatched == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    enabled = enabled,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.iwara.tv/video/${meta.id}")
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "在浏览器中打开"
                    )
                }

                FilledTonalIconButton(
                    onClick = onBind,
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "绑定"
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onBind)
    )
}

private fun authorLabel(
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

private fun durationCompareText(
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

private fun durationMatched(
    localDurationMs: Long?,
    remoteDurationSeconds: Long?
): Boolean? {
    val localSeconds = localDurationMs?.takeIf { it > 0L }?.div(1000L) ?: return null
    val remoteSeconds = remoteDurationSeconds?.takeIf { it > 0L } ?: return null
    return abs(localSeconds - remoteSeconds) <= 2L
}

@Composable
private fun SearchDiagnostic(
    summary: String,
    raw: String?,
    showRaw: Boolean,
    onToggleRaw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (!raw.isNullOrBlank()) {
            TextButton(onClick = onToggleRaw) {
                Text(if (showRaw) "收起详情" else "展开详情")
            }
            if (showRaw) {
                SelectionContainer {
                    Text(
                        text = raw,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}
