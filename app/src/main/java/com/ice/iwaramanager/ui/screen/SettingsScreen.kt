package com.ice.iwaramanager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ice.iwaramanager.SettingsUiState
import com.ice.iwaramanager.data.model.IwaraMatchMode
import com.ice.iwaramanager.data.model.LibraryLayoutMode
import com.ice.iwaramanager.data.model.VideoOpenMode
import com.ice.iwaramanager.data.model.VideoPlayerApp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    videoPlayerApps: List<VideoPlayerApp>,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onClearDatabase: () -> Unit,
    onClearFolder: () -> Unit,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onVideoOpenModeChange: (VideoOpenMode) -> Unit,
    onExternalVideoPlayerChange: (String, String) -> Unit,
    onResetVideoOpenMode: () -> Unit,
    onIwaraMatchModeChange: (IwaraMatchMode) -> Unit,
    onMatchSearchTimeoutSecondsChange: (String) -> Unit,
    onBatchMatchThreadsChange: (String) -> Unit
) {
    var showClearDatabaseDialog by remember { mutableStateOf(false) }
    var showClearFolderDialog by remember { mutableStateOf(false) }

    if (showClearDatabaseDialog) {
        AlertDialog(
            onDismissRequest = { showClearDatabaseDialog = false },
            title = { Text("确认清空数据库？") },
            text = { Text("这只会清空软件记录，不会删除手机里的视频文件。") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDatabaseDialog = false
                        onClearDatabase()
                    }
                ) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDatabaseDialog = false }) { Text("取消") }
            }
        )
    }

    if (showClearFolderDialog) {
        AlertDialog(
            onDismissRequest = { showClearFolderDialog = false },
            title = { Text("确认清除当前目录？") },
            text = { Text("这会移除当前目录设置，并清空该目录对应的视频记录，不会删除视频文件。") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearFolderDialog = false
                        onClearFolder()
                    }
                ) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearFolderDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("视频目录")
            ListItem(
                headlineContent = { Text("当前目录") },
                supportingContent = {
                    Text(
                        text = state.folderUriString ?: "尚未选择",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            ListItem(
                headlineContent = { Text("选择本地视频目录") },
                supportingContent = { Text("选择存放 Iwara 视频文件的目录。") },
                trailingContent = {
                    Button(onClick = onPickFolder) {
                        Text(if (state.folderUriString == null) "选择" else "更换")
                    }
                }
            )
            ListItem(
                headlineContent = { Text("重新扫描") },
                supportingContent = { Text("重新读取当前目录并更新视频记录。") },
                trailingContent = {
                    Button(
                        onClick = onRescan,
                        enabled = state.folderUriString != null && !state.isScanning
                    ) {
                        Text(if (state.isScanning) "扫描中" else "扫描")
                    }
                }
            )
            ListItem(
                headlineContent = { Text("清除当前目录") },
                supportingContent = { Text("移除当前目录设置，并清空该目录对应的视频记录。") },
                trailingContent = {
                    TextButton(
                        onClick = { showClearFolderDialog = true },
                        enabled = state.folderUriString != null
                    ) { Text("清除") }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("主页显示")
            ListItem(
                headlineContent = { Text("布局模式") },
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = state.layoutMode == LibraryLayoutMode.Grid,
                            onClick = { onLayoutModeChange(LibraryLayoutMode.Grid) },
                            label = { Text("网格") }
                        )
                        FilterChip(
                            selected = state.layoutMode == LibraryLayoutMode.List,
                            onClick = { onLayoutModeChange(LibraryLayoutMode.List) },
                            label = { Text("列表") }
                        )
                    }
                }
            )
            ListItem(
                headlineContent = { Text("网格列数") },
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 3, 4, 5, 6).forEach { columns ->
                            FilterChip(
                                selected = state.gridColumns == columns,
                                onClick = { onGridColumnsChange(columns) },
                                label = { Text("$columns") }
                            )
                        }
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("播放")
            ListItem(
                headlineContent = { Text("视频打开方式") },
                supportingContent = { Text(selectedVideoOpenModeText(state)) },
                trailingContent = {
                    TextButton(
                        onClick = onResetVideoOpenMode,
                        enabled = state.videoOpenMode != VideoOpenMode.InApp
                    ) { Text("恢复默认") }
                }
            )
            ListItem(
                headlineContent = { Text("选择默认播放器") },
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = state.videoOpenMode == VideoOpenMode.InApp,
                            onClick = { onVideoOpenModeChange(VideoOpenMode.InApp) },
                            label = { Text("应用内播放器") }
                        )
                        videoPlayerApps.forEach { player ->
                            FilterChip(
                                selected = state.videoOpenMode == VideoOpenMode.ExternalPlayer &&
                                    state.selectedVideoPlayerComponentName == player.componentName,
                                onClick = {
                                    onExternalVideoPlayerChange(player.componentName, player.label)
                                },
                                label = { Text(player.label) }
                            )
                        }
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("匹配")
            ListItem(
                headlineContent = { Text("匹配模式") },
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IwaraMatchMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.iwaraMatchMode == mode,
                                onClick = { onIwaraMatchModeChange(mode) },
                                label = { Text(matchModeLabel(mode)) }
                            )
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("搜索超时秒数") },
                supportingContent = {
                    OutlinedTextField(
                        value = state.matchSearchTimeoutSecondsText,
                        onValueChange = onMatchSearchTimeoutSecondsChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("10-180") }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("批量匹配线程") },
                supportingContent = {
                    OutlinedTextField(
                        value = state.batchMatchThreadsText,
                        onValueChange = onBatchMatchThreadsChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("1-4") }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("数据库")
            ListItem(
                headlineContent = { Text("当前记录数") },
                supportingContent = { Text("${state.videoCount} 个视频") }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("危险操作")
            ListItem(
                headlineContent = { Text("清空数据库") },
                supportingContent = { Text("删除所有视频记录、匹配信息和任务，不会删除手机上的视频文件。") },
                trailingContent = {
                    Button(onClick = { showClearDatabaseDialog = true }) { Text("清空") }
                }
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

private fun selectedVideoOpenModeText(state: SettingsUiState): String {
    return when (state.videoOpenMode) {
        VideoOpenMode.InApp -> "当前默认：应用内播放器"
        VideoOpenMode.ExternalPlayer -> "当前默认：${state.selectedVideoPlayerLabel ?: "未选择外部播放器"}"
    }
}

private fun matchModeLabel(mode: IwaraMatchMode): String {
    return when (mode) {
        IwaraMatchMode.IdThenTitle -> "ID优先+标题"
        IwaraMatchMode.IdOnly -> "只按ID"
        IwaraMatchMode.TitleOnly -> "只按标题"
    }
}
