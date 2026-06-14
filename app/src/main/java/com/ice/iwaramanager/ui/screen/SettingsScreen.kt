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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ice.iwaramanager.SettingsUiState
import com.ice.iwaramanager.data.model.IwaraMatchMode
import com.ice.iwaramanager.data.model.LibraryLayoutMode
import com.ice.iwaramanager.data.model.LibrarySource
import com.ice.iwaramanager.data.model.LibrarySourceType
import com.ice.iwaramanager.data.model.RemoteIndexMode
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
    onExportDatabase: () -> Unit,
    onImportDatabase: () -> Unit,
    onWebDavNameChange: (String) -> Unit,
    onWebDavBaseUrlChange: (String) -> Unit,
    onWebDavRootPathChange: (String) -> Unit,
    onWebDavUsernameChange: (String) -> Unit,
    onWebDavPasswordChange: (String) -> Unit,
    onWebDavIndexModeChange: (RemoteIndexMode) -> Unit,
    onWebDavConnectTimeoutSecondsChange: (String) -> Unit,
    onWebDavReadTimeoutSecondsChange: (String) -> Unit,
    onTestWebDav: () -> Unit,
    onAddWebDav: () -> Unit,
    onDeleteSource: (String) -> Unit,
    onScanSource: (String) -> Unit,
    onRemoteProxyIdleTimeoutSecondsChange: (String) -> Unit,
    onRemoteProxyReadTimeoutSecondsChange: (String) -> Unit,
    onShowRemotePlaybackDiagnosticsChange: (Boolean) -> Unit,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onShowRematchButtonInListChange: (Boolean) -> Unit,
    onVideoOpenModeChange: (VideoOpenMode) -> Unit,
    onExternalVideoPlayerChange: (String, String) -> Unit,
    onResetVideoOpenMode: () -> Unit,
    onIwaraMatchModeChange: (IwaraMatchMode) -> Unit,
    onMatchSearchTimeoutSecondsChange: (String) -> Unit,
    onApiProbeTimeoutSecondsChange: (String) -> Unit,
    onApiEndpointTemplatesChange: (String) -> Unit,
    onResetApiEndpointTemplates: () -> Unit,
    onAllowPageFallbackChange: (Boolean) -> Unit,
    onFetchSearchResultDetailsWithApiChange: (Boolean) -> Unit,
    onMaxSearchApiDetailsChange: (String) -> Unit,
    onBatchMatchThreadsChange: (String) -> Unit,
    onAutoMatchSingleHighConfidenceChange: (Boolean) -> Unit,
    onAutoMatchTitleSimilarityThresholdChange: (String) -> Unit,
    onAutoMatchDurationToleranceSecondsChange: (String) -> Unit,
    onAutoMatchSkipNoIdChange: (Boolean) -> Unit
) {
    var showClearDatabaseDialog by remember { mutableStateOf(false) }
    var showClearFolderDialog by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("目录", "网络盘", "显示", "播放", "匹配", "数据库")

    ConfirmDialog(
        visible = showClearDatabaseDialog,
        title = "确认清空数据库？",
        text = "这只会清空软件记录，不会删除手机里的视频文件。",
        confirmText = "确认清空",
        onDismiss = { showClearDatabaseDialog = false },
        onConfirm = {
            showClearDatabaseDialog = false
            onClearDatabase()
        }
    )
    ConfirmDialog(
        visible = showClearFolderDialog,
        title = "确认清除当前来源范围？",
        text = "这会移除当前选择范围内的来源记录，不会删除视频文件。",
        confirmText = "确认清除",
        onDismiss = { showClearFolderDialog = false },
        onConfirm = {
            showClearFolderDialog = false
            onClearFolder()
        }
    )

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
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DirectorySettings(
                    state = state,
                    onPickFolder = onPickFolder,
                    onRescan = onRescan,
                    onClearFolder = { showClearFolderDialog = true },
                    onScanSource = onScanSource,
                    onDeleteSource = onDeleteSource
                )
                1 -> WebDavSettings(
                    state = state,
                    onWebDavNameChange = onWebDavNameChange,
                    onWebDavBaseUrlChange = onWebDavBaseUrlChange,
                    onWebDavRootPathChange = onWebDavRootPathChange,
                    onWebDavUsernameChange = onWebDavUsernameChange,
                    onWebDavPasswordChange = onWebDavPasswordChange,
                    onWebDavIndexModeChange = onWebDavIndexModeChange,
                    onWebDavConnectTimeoutSecondsChange = onWebDavConnectTimeoutSecondsChange,
                    onWebDavReadTimeoutSecondsChange = onWebDavReadTimeoutSecondsChange,
                    onTestWebDav = onTestWebDav,
                    onAddWebDav = onAddWebDav,
                    onScanSource = onScanSource,
                    onDeleteSource = onDeleteSource
                )
                2 -> DisplaySettings(
                    state = state,
                    onLayoutModeChange = onLayoutModeChange,
                    onGridColumnsChange = onGridColumnsChange,
                    onShowRematchButtonInListChange = onShowRematchButtonInListChange
                )
                3 -> PlaybackSettings(
                    state = state,
                    videoPlayerApps = videoPlayerApps,
                    onVideoOpenModeChange = onVideoOpenModeChange,
                    onExternalVideoPlayerChange = onExternalVideoPlayerChange,
                    onResetVideoOpenMode = onResetVideoOpenMode,
                    onRemoteProxyIdleTimeoutSecondsChange = onRemoteProxyIdleTimeoutSecondsChange,
                    onRemoteProxyReadTimeoutSecondsChange = onRemoteProxyReadTimeoutSecondsChange,
                    onShowRemotePlaybackDiagnosticsChange = onShowRemotePlaybackDiagnosticsChange
                )
                4 -> MatchSettings(
                    state = state,
                    onIwaraMatchModeChange = onIwaraMatchModeChange,
                    onMatchSearchTimeoutSecondsChange = onMatchSearchTimeoutSecondsChange,
                    onApiProbeTimeoutSecondsChange = onApiProbeTimeoutSecondsChange,
                    onApiEndpointTemplatesChange = onApiEndpointTemplatesChange,
                    onResetApiEndpointTemplates = onResetApiEndpointTemplates,
                    onAllowPageFallbackChange = onAllowPageFallbackChange,
                    onFetchSearchResultDetailsWithApiChange = onFetchSearchResultDetailsWithApiChange,
                    onMaxSearchApiDetailsChange = onMaxSearchApiDetailsChange,
                    onBatchMatchThreadsChange = onBatchMatchThreadsChange,
                    onAutoMatchSingleHighConfidenceChange = onAutoMatchSingleHighConfidenceChange,
                    onAutoMatchTitleSimilarityThresholdChange = onAutoMatchTitleSimilarityThresholdChange,
                    onAutoMatchDurationToleranceSecondsChange = onAutoMatchDurationToleranceSecondsChange,
                    onAutoMatchSkipNoIdChange = onAutoMatchSkipNoIdChange
                )
                else -> DatabaseSettings(
                    state = state,
                    onExportDatabase = onExportDatabase,
                    onImportDatabase = onImportDatabase,
                    onClearDatabase = { showClearDatabaseDialog = true }
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            state.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    visible: Boolean,
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DirectorySettings(
    state: SettingsUiState,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onClearFolder: () -> Unit,
    onScanSource: (String) -> Unit,
    onDeleteSource: (String) -> Unit
) {
    SectionTitle("本地视频目录")
    ListItem(
        headlineContent = { Text("添加本地目录") },
        supportingContent = { Text("可添加多个本地 Iwara 视频目录，主页来源下拉中会分别显示。") },
        trailingContent = { Button(onClick = onPickFolder) { Text("添加") } }
    )
    val localSources = state.librarySources.filter { it.type == LibrarySourceType.LocalSaf }
    if (localSources.isEmpty()) {
        ListItem(
            headlineContent = { Text("尚未添加本地目录") },
            supportingContent = { Text("添加后可以单独扫描，也可以在主页选择全部来源。") }
        )
    } else {
        localSources.forEach {
            SourceListItem(it, state.isScanning, onScanSource, onDeleteSource)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    ListItem(
        headlineContent = { Text("重新扫描当前来源范围") },
        supportingContent = { Text("按主页当前选择的来源范围重新扫描。") },
        trailingContent = {
            Button(
                onClick = onRescan,
                enabled = state.librarySources.isNotEmpty() && !state.isScanning
            ) { Text(if (state.isScanning) "扫描中" else "扫描") }
        }
    )
    ListItem(
        headlineContent = { Text("清除当前来源范围") },
        supportingContent = { Text("移除当前选择范围内的来源记录，不会删除视频文件。") },
        trailingContent = {
            TextButton(
                onClick = onClearFolder,
                enabled = state.librarySources.isNotEmpty()
            ) { Text("清除") }
        }
    )
}

@Composable
private fun WebDavSettings(
    state: SettingsUiState,
    onWebDavNameChange: (String) -> Unit,
    onWebDavBaseUrlChange: (String) -> Unit,
    onWebDavRootPathChange: (String) -> Unit,
    onWebDavUsernameChange: (String) -> Unit,
    onWebDavPasswordChange: (String) -> Unit,
    onWebDavIndexModeChange: (RemoteIndexMode) -> Unit,
    onWebDavConnectTimeoutSecondsChange: (String) -> Unit,
    onWebDavReadTimeoutSecondsChange: (String) -> Unit,
    onTestWebDav: () -> Unit,
    onAddWebDav: () -> Unit,
    onScanSource: (String) -> Unit,
    onDeleteSource: (String) -> Unit
) {
    SectionTitle("WebDAV 网络盘")
    val form = state.webDavForm
    SettingsTextField("名称", form.name, onWebDavNameChange, "NAS / WebDAV")
    SettingsTextField("服务器地址", form.baseUrl, onWebDavBaseUrlChange, "https://example.com/dav")
    SettingsTextField("根路径", form.rootPath, onWebDavRootPathChange, "/")
    SettingsTextField("账号", form.username, onWebDavUsernameChange, "可留空")
    SettingsTextField("密码", form.password, onWebDavPasswordChange, "保存时会加密存储")
    ListItem(
        headlineContent = { Text("索引方式") },
        supportingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = form.indexMode == RemoteIndexMode.Light,
                    onClick = { onWebDavIndexModeChange(RemoteIndexMode.Light) },
                    label = { Text("轻索引") }
                )
                FilterChip(
                    selected = form.indexMode == RemoteIndexMode.Full,
                    onClick = { onWebDavIndexModeChange(RemoteIndexMode.Full) },
                    label = { Text("完整索引") }
                )
            }
        }
    )
    SettingsNumberField("连接超时秒数", form.connectTimeoutSecondsText, onWebDavConnectTimeoutSecondsChange, "5-120")
    SettingsNumberField("读取超时秒数", form.readTimeoutSecondsText, onWebDavReadTimeoutSecondsChange, "5-180")
    ListItem(
        headlineContent = { Text("连接测试与添加") },
        supportingContent = { Text("第一版仅支持 HTTPS WebDAV 和 Basic/无认证。连接失败会显示 HTTP 状态或证书/超时原因。") },
        trailingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTestWebDav, enabled = !state.isScanning) { Text("测试") }
                Button(onClick = onAddWebDav, enabled = !state.isScanning) { Text("添加") }
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SectionTitle("已添加 WebDAV")
    val webDavSources = state.librarySources.filter { it.type == LibrarySourceType.WebDav }
    if (webDavSources.isEmpty()) {
        ListItem(
            headlineContent = { Text("暂无 WebDAV 来源") },
            supportingContent = { Text("添加后会出现在主页来源下拉中。") }
        )
    } else {
        webDavSources.forEach { SourceListItem(it, state.isScanning, onScanSource, onDeleteSource) }
    }
}

@Composable
private fun DisplaySettings(
    state: SettingsUiState,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onShowRematchButtonInListChange: (Boolean) -> Unit
) {
    SectionTitle("主页显示")
    ListItem(
        headlineContent = { Text("布局模式") },
        supportingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(state.layoutMode == LibraryLayoutMode.Grid, { onLayoutModeChange(LibraryLayoutMode.Grid) }, label = { Text("网格") })
                FilterChip(state.layoutMode == LibraryLayoutMode.List, { onLayoutModeChange(LibraryLayoutMode.List) }, label = { Text("列表") })
                FilterChip(state.layoutMode == LibraryLayoutMode.Directory, { onLayoutModeChange(LibraryLayoutMode.Directory) }, label = { Text("目录") })
            }
        }
    )
    ListItem(
        headlineContent = { Text("网格列数") },
        supportingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    ListItem(
        headlineContent = { Text("列表显示重匹配按钮") },
        supportingContent = { Text("关闭后，列表模式下已匹配视频不显示重匹配按钮。") },
        trailingContent = { Switch(state.showRematchButtonInList, onShowRematchButtonInListChange) }
    )
}

@Composable
private fun PlaybackSettings(
    state: SettingsUiState,
    videoPlayerApps: List<VideoPlayerApp>,
    onVideoOpenModeChange: (VideoOpenMode) -> Unit,
    onExternalVideoPlayerChange: (String, String) -> Unit,
    onResetVideoOpenMode: () -> Unit,
    onRemoteProxyIdleTimeoutSecondsChange: (String) -> Unit,
    onRemoteProxyReadTimeoutSecondsChange: (String) -> Unit,
    onShowRemotePlaybackDiagnosticsChange: (Boolean) -> Unit
) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.videoOpenMode == VideoOpenMode.InApp,
                    onClick = { onVideoOpenModeChange(VideoOpenMode.InApp) },
                    label = { Text("应用内播放器") }
                )
                videoPlayerApps.forEach { player ->
                    FilterChip(
                        selected = state.videoOpenMode == VideoOpenMode.ExternalPlayer &&
                            state.selectedVideoPlayerComponentName == player.componentName,
                        onClick = { onExternalVideoPlayerChange(player.componentName, player.label) },
                        label = { Text(player.label) }
                    )
                }
            }
        }
    )
    SettingsNumberField("远程播放代理空闲秒数", state.remoteProxyIdleTimeoutSecondsText, onRemoteProxyIdleTimeoutSecondsChange, "30-3600")
    SettingsNumberField("远程播放读取超时秒数", state.remoteProxyReadTimeoutSecondsText, onRemoteProxyReadTimeoutSecondsChange, "10-300")
    ListItem(
        headlineContent = { Text("显示远程播放诊断") },
        supportingContent = { Text("WebDAV 播放失败时显示认证、代理或网络错误提示。") },
        trailingContent = { Switch(state.showRemotePlaybackDiagnostics, onShowRemotePlaybackDiagnosticsChange) }
    )
}

@Composable
private fun MatchSettings(
    state: SettingsUiState,
    onIwaraMatchModeChange: (IwaraMatchMode) -> Unit,
    onMatchSearchTimeoutSecondsChange: (String) -> Unit,
    onApiProbeTimeoutSecondsChange: (String) -> Unit,
    onApiEndpointTemplatesChange: (String) -> Unit,
    onResetApiEndpointTemplates: () -> Unit,
    onAllowPageFallbackChange: (Boolean) -> Unit,
    onFetchSearchResultDetailsWithApiChange: (Boolean) -> Unit,
    onMaxSearchApiDetailsChange: (String) -> Unit,
    onBatchMatchThreadsChange: (String) -> Unit,
    onAutoMatchSingleHighConfidenceChange: (Boolean) -> Unit,
    onAutoMatchTitleSimilarityThresholdChange: (String) -> Unit,
    onAutoMatchDurationToleranceSecondsChange: (String) -> Unit,
    onAutoMatchSkipNoIdChange: (Boolean) -> Unit
) {
    SectionTitle("匹配")
    ListItem(
        headlineContent = { Text("匹配模式") },
        supportingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    SettingsNumberField("搜索总超时秒数", state.matchSearchTimeoutSecondsText, onMatchSearchTimeoutSecondsChange, "10-180")
    SettingsNumberField("API 探测超时秒数", state.apiProbeTimeoutSecondsText, onApiProbeTimeoutSecondsChange, "5-120")
    ListItem(
        headlineContent = { Text("API 端点模板") },
        supportingContent = {
            OutlinedTextField(
                value = state.apiEndpointTemplatesText,
                onValueChange = onApiEndpointTemplatesChange,
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.iwara.tv/video/{id}") }
            )
        },
        trailingContent = { TextButton(onClick = onResetApiEndpointTemplates) { Text("恢复默认") } }
    )
    SwitchItem("标题搜索后按 ID 获取详情", "搜索页只负责提取候选 ID，详情字段从 ID/API 链路获取。", state.fetchSearchResultDetailsWithApi, onFetchSearchResultDetailsWithApiChange)
    SettingsNumberField("标题搜索详情数量", state.maxSearchApiDetailsText, onMaxSearchApiDetailsChange, "1-30")
    SwitchItem("API 失败时允许页面兜底", "关闭后，详情字段不会再用不可靠 DOM 页面猜测。", state.allowPageFallback, onAllowPageFallbackChange)
    SettingsNumberField("批量匹配线程", state.batchMatchThreadsText, onBatchMatchThreadsChange, "1-4")
    SwitchItem(
        "唯一候选自动绑定",
        "开启后，仅当没有本地ID、候选唯一、标题达标且时长一致时自动绑定；有本地ID时只按ID完全一致自动绑定。",
        state.autoMatchSingleHighConfidence,
        onAutoMatchSingleHighConfidenceChange
    )
    SwitchItem("无 ID 自动跳过", "开启后，批量自动匹配遇到未解析出 Iwara ID 的视频会直接标记跳过；手动匹配不受影响。", state.autoMatchSkipNoId, onAutoMatchSkipNoIdChange)
    SettingsNumberField("自动绑定标题相似度阈值", state.autoMatchTitleSimilarityThresholdText, onAutoMatchTitleSimilarityThresholdChange, "1-100")
    SettingsNumberField("自动绑定时长容差秒数", state.autoMatchDurationToleranceSecondsText, onAutoMatchDurationToleranceSecondsChange, "0-60")
}

@Composable
private fun DatabaseSettings(
    state: SettingsUiState,
    onExportDatabase: () -> Unit,
    onImportDatabase: () -> Unit,
    onClearDatabase: () -> Unit
) {
    SectionTitle("数据库")
    ListItem(
        headlineContent = { Text("当前记录数") },
        supportingContent = { Text("${state.videoCount} 个视频") }
    )
    ListItem(
        headlineContent = { Text("导出数据库") },
        supportingContent = { Text("保存当前 Room SQLite 数据库文件。") },
        trailingContent = { Button(onClick = onExportDatabase) { Text("导出") } }
    )
    ListItem(
        headlineContent = { Text("导入数据库") },
        supportingContent = { Text("从 .db 文件覆盖当前数据库。导入后会重新加载数据。") },
        trailingContent = { Button(onClick = onImportDatabase) { Text("导入") } }
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SectionTitle("危险操作")
    ListItem(
        headlineContent = { Text("清空数据库") },
        supportingContent = { Text("删除所有视频记录、匹配信息、来源和任务，不会删除视频文件。") },
        trailingContent = { Button(onClick = onClearDatabase) { Text("清空") } }
    )
}

@Composable
private fun SettingsTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) }
            )
        }
    )
}

@Composable
private fun SettingsNumberField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(placeholder) }
            )
        }
    )
}

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
private fun SourceListItem(
    source: LibrarySource,
    isScanning: Boolean,
    onScanSource: (String) -> Unit,
    onDeleteSource: (String) -> Unit
) {
    ListItem(
        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = when (source.type) {
                    LibrarySourceType.LocalSaf -> source.rootUriString
                    LibrarySourceType.WebDav -> listOfNotNull(source.webDavBaseUrl, source.webDavRootPath).joinToString(" ")
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onScanSource(source.id) }, enabled = !isScanning) { Text("扫描") }
                TextButton(onClick = { onDeleteSource(source.id) }, enabled = !isScanning) { Text("删除") }
            }
        }
    )
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
