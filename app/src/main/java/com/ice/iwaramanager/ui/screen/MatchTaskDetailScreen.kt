package com.ice.iwaramanager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.ice.iwaramanager.MatchTaskDetailUiState
import com.ice.iwaramanager.data.local.entity.MatchCandidateEntity
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import com.ice.iwaramanager.data.model.MatchTaskStatus
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchTaskDetailScreen(
    state: MatchTaskDetailUiState,
    onBack: () -> Unit,
    onOpenMatchPage: (MatchTaskEntity) -> Unit,
    onBindCandidate: (MatchCandidateEntity) -> Unit,
    onSkipTask: (MatchTaskEntity) -> Unit,
    onPlayTask: (MatchTaskEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("匹配任务") }
            )
        }
    ) { paddingValues ->
        val task = state.task
        if (task == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("任务不存在")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TaskHeaderCard(
                    task = task,
                    isBinding = state.isBinding,
                    onOpenMatchPage = { onOpenMatchPage(task) },
                    onPlayTask = { onPlayTask(task) },
                    onSkipTask = { onSkipTask(task) }
                )
            }

            if (state.error != null) {
                item {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.isBinding) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text("正在绑定...")
                    }
                }
            }

            item {
                Text(
                    text = "候选列表",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.candidates.isEmpty()) {
                item {
                    Text(
                        text = "暂无候选。可以点击“打开匹配页”修改搜索词后重新搜索。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.candidates) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        localDurationMs = task.localDurationMs,
                        enabled = !state.isBinding,
                        onBind = { onBindCandidate(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskHeaderCard(
    task: MatchTaskEntity,
    isBinding: Boolean,
    onOpenMatchPage: () -> Unit,
    onPlayTask: () -> Unit,
    onSkipTask: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val coverPath = task.coverFilePath
                if (coverPath != null) {
                    AsyncImage(
                        model = File(coverPath),
                        contentDescription = task.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(112.dp)
                            .height(63.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .width(112.dp)
                            .height(63.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("无预览")
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = task.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("状态：${matchTaskStatusLabel(task.status)}")
                    Text("搜索词：${task.query}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("本地时长：${formatDuration(task.localDurationMs)}")
                    Text("候选数：${task.candidateCount}")
                    if (task.matchedIwaraId != null) {
                        Text("已匹配 ID：${task.matchedIwaraId}")
                    }
                    if (task.errorMessage != null) {
                        Text(
                            text = task.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPlayTask,
                    enabled = !isBinding
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
                }

                FilledTonalButton(
                    onClick = onOpenMatchPage,
                    enabled = !isBinding
                ) {
                    Text("打开匹配页")
                }

                OutlinedButton(
                    onClick = onSkipTask,
                    enabled = !isBinding && task.status != MatchTaskStatus.Skipped
                ) {
                    Text("标记跳过")
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: MatchCandidateEntity,
    localDurationMs: Long?,
    enabled: Boolean,
    onBind: () -> Unit
) {
    val context = LocalContext.current

    ListItem(
        headlineContent = {
            Text(
                text = candidate.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                val info = listOfNotNull(
                    authorLabel(candidate.authorName, candidate.authorUsername),
                    candidate.rating,
                    candidate.createdAtText,
                    "ID:${candidate.iwaraId}"
                ).joinToString(" · ")

                Text(
                    text = info,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val durationMatched = durationMatched(localDurationMs, candidate.durationSeconds)
                Text(
                    text = durationCompareText(localDurationMs, candidate.durationSeconds),
                    color = if (durationMatched == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (candidate.selected) {
                    Text(
                        text = "已选择",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.iwara.tv/video/${candidate.iwaraId}")
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "在浏览器中打开"
                    )
                }

                IconButton(
                    onClick = onBind,
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = if (candidate.selected) "已绑定" else "绑定",
                        tint = if (candidate.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
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

private fun matchTaskStatusLabel(status: String): String {
    return when (status) {
        MatchTaskStatus.Pending -> "等待中"
        MatchTaskStatus.Running -> "匹配中"
        MatchTaskStatus.AutoMatched -> "成功"
        MatchTaskStatus.NeedReview -> "需复核"
        MatchTaskStatus.Failed -> "失败"
        MatchTaskStatus.Skipped -> "跳过"
        else -> status
    }
}
