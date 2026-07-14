package com.ice.iwaramanager.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.ice.iwaramanager.LibraryUiState
import com.ice.iwaramanager.SearchUiState
import com.ice.iwaramanager.data.local.dao.CountItem
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import com.ice.iwaramanager.data.model.LibraryFolderNode
import com.ice.iwaramanager.data.model.FilterTab
import com.ice.iwaramanager.data.model.LibraryLayoutMode
import com.ice.iwaramanager.data.model.MainTab
import com.ice.iwaramanager.data.model.MatchTaskFilter
import com.ice.iwaramanager.data.model.MatchTaskStatus
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.VideoSortMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class DirectoryScrollPosition(
    val index: Int,
    val offset: Int
)

private data class DirectoryRestoreRequest(
    val directoryKey: String,
    val position: DirectoryScrollPosition,
    val highlightFolderKey: String?,
    val minContentVersion: Long
)

private const val HighlightHoldMillis = 1000L
private const val HighlightFadeMillis = 450

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryState: LibraryUiState,
    searchState: SearchUiState,
    libraryItems: LazyPagingItems<VideoItem>,
    searchItems: LazyPagingItems<VideoItem>,
    selectedTab: MainTab,
    tabReselectTick: Long,
    showRematchButtonInList: Boolean,
    showGridCoverPlayButton: Boolean,
    highlightedVideoUri: String?,
    onHighlightedVideoConsumed: () -> Unit,
    onSelectTab: (MainTab) -> Unit,
    onSourceScopeChange: (String) -> Unit,
    onOpenDirectory: (LibraryFolderNode) -> Unit,
    onDirectoryUp: () -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onVideoSortModeChange: (VideoSortMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFilterTabChange: (FilterTab) -> Unit,
    onToggleTagFilter: (String) -> Unit,
    onToggleAuthorFilter: (String) -> Unit,
    onToggleQualityFilter: (String) -> Unit,
    onClearFilters: () -> Unit,
    onMatchTaskFilterChange: (MatchTaskFilter) -> Unit,
    onOpenMatchTask: (MatchTaskEntity) -> Unit,
    onSkipMatchTask: (MatchTaskEntity) -> Unit,
    onCancelSkippedMatchTask: (MatchTaskEntity) -> Unit,
    onSkipUnqueuedVideo: (VideoItem) -> Unit,
    onRetryMatchTask: (MatchTaskEntity) -> Unit,
    onRetryFailedTasks: () -> Unit,
    onQueueDurationIssuesForReview: () -> Unit,
    onRetryNeedReviewTasks: () -> Unit,
    onStartBatchMatch: () -> Unit,
    onStartBatchRematch: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onMatchVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit
) {
    val libraryListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val directoryListState = rememberLazyListState()
    val filterListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val searchGridState = rememberLazyGridState()
    val taskListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val directoryScrollPositions = remember { mutableMapOf<String, DirectoryScrollPosition>() }
    val currentDirectoryKey = directoryStateKey(libraryState)
    var pendingDirectoryRestore by remember { mutableStateOf<DirectoryRestoreRequest?>(null) }
    var highlightedDirectoryFolderKey by remember { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    fun rememberCurrentDirectoryPosition() {
        directoryScrollPositions[currentDirectoryKey] = DirectoryScrollPosition(
            index = directoryListState.firstVisibleItemIndex,
            offset = directoryListState.firstVisibleItemScrollOffset
        )
    }

    fun openDirectoryWithPositionMemory(folder: LibraryFolderNode) {
        rememberCurrentDirectoryPosition()
        onOpenDirectory(folder)
    }

    fun navigateDirectoryUpWithPositionRestore() {
        val parentKey = parentDirectoryStateKey(libraryState)
        if (parentKey != null) {
            pendingDirectoryRestore = DirectoryRestoreRequest(
                directoryKey = parentKey,
                position = directoryScrollPositions[parentKey] ?: DirectoryScrollPosition(0, 0),
                highlightFolderKey = currentDirectoryFolderKey(libraryState),
                minContentVersion = libraryState.directoryContentVersion
            )
        }
        onDirectoryUp()
    }

    suspend fun scrollCurrentTabToTop() {
        when (selectedTab) {
            MainTab.Library -> {
                if (libraryState.layoutMode == LibraryLayoutMode.Grid) {
                    libraryGridState.scrollToItem(0)
                } else if (libraryState.layoutMode == LibraryLayoutMode.Directory) {
                    directoryListState.scrollToItem(0)
                } else {
                    libraryListState.scrollToItem(0)
                }
            }

            MainTab.Filters -> filterListState.scrollToItem(0)
            MainTab.Search -> {
                if (libraryState.layoutMode == LibraryLayoutMode.Grid) {
                    searchGridState.scrollToItem(0)
                } else {
                    searchListState.scrollToItem(0)
                }
            }

            MainTab.Tasks -> taskListState.scrollToItem(0)
        }
    }

    LaunchedEffect(tabReselectTick) {
        if (tabReselectTick > 0L) scrollCurrentTabToTop()
    }

    LaunchedEffect(
        libraryState.layoutMode,
        currentDirectoryKey,
        libraryState.directoryContentVersion,
        pendingDirectoryRestore
    ) {
        val request = pendingDirectoryRestore
        if (
            libraryState.layoutMode == LibraryLayoutMode.Directory &&
            request != null &&
            request.directoryKey == currentDirectoryKey &&
            libraryState.directoryContentVersion > request.minContentVersion
        ) {
            directoryListState.scrollToItem(
                index = request.position.index,
                scrollOffset = request.position.offset
            )
            highlightedDirectoryFolderKey = request.highlightFolderKey
            pendingDirectoryRestore = null
        }
    }

    LaunchedEffect(libraryState.layoutMode, libraryState.directoryScrollToken) {
        if (
            libraryState.layoutMode == LibraryLayoutMode.Directory &&
            libraryState.directoryScrollToken > 0L
        ) {
            directoryListState.scrollToItem(0)
        }
    }

    LaunchedEffect(highlightedDirectoryFolderKey) {
        val highlightedKey = highlightedDirectoryFolderKey ?: return@LaunchedEffect
        delay(HighlightHoldMillis)
        if (highlightedDirectoryFolderKey == highlightedKey) {
            highlightedDirectoryFolderKey = null
        }
    }

    LaunchedEffect(highlightedVideoUri) {
        if (highlightedVideoUri == null) return@LaunchedEffect
        delay(HighlightHoldMillis)
        onHighlightedVideoConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Iwara Manager",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        SourceScopeDropdown(
                            state = libraryState,
                            onSourceScopeChange = onSourceScopeChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            VideoSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (libraryState.videoSortMode == mode) {
                                                "✓ ${videoSortModeLabel(mode)}"
                                            } else {
                                                videoSortModeLabel(mode)
                                            }
                                        )
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        onVideoSortModeChange(mode)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            onLayoutModeChange(
                                when (libraryState.layoutMode) {
                                    LibraryLayoutMode.Grid -> LibraryLayoutMode.List
                                    LibraryLayoutMode.List -> LibraryLayoutMode.Directory
                                    LibraryLayoutMode.Directory -> LibraryLayoutMode.Grid
                                }
                            )
                        }
                    ) {
                        Icon(
                            imageVector = when (libraryState.layoutMode) {
                                LibraryLayoutMode.Grid -> Icons.Filled.ViewList
                                LibraryLayoutMode.List -> Icons.Filled.FolderOpen
                                LibraryLayoutMode.Directory -> Icons.Filled.GridView
                            },
                            contentDescription = "切换布局"
                        )
                    }

                    IconButton(
                        onClick = onRescan,
                        enabled = libraryState.librarySources.isNotEmpty() && !libraryState.isScanning
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新扫描")
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab
            )
        }
    ) { paddingValues ->
        if (libraryState.librarySources.isEmpty()) {
            NoFolderContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onOpenSettings = onOpenSettings
            )
            return@Scaffold
        }

        when (selectedTab) {
            MainTab.Library -> LibraryContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = libraryState,
                libraryItems = libraryItems,
                listState = libraryListState,
                directoryListState = directoryListState,
                gridState = libraryGridState,
                onOpenVideo = onOpenVideo,
                onMatchVideo = onMatchVideo,
                onPlayVideo = onPlayVideo,
                onOpenDirectory = ::openDirectoryWithPositionMemory,
                onDirectoryUp = ::navigateDirectoryUpWithPositionRestore,
                highlightedDirectoryFolderKey = highlightedDirectoryFolderKey,
                highlightedVideoUri = highlightedVideoUri,
                showRematchButtonInList = showRematchButtonInList,
                showGridCoverPlayButton = showGridCoverPlayButton
            )

            MainTab.Filters -> FilterContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = libraryState,
                listState = filterListState,
                onFilterTabChange = onFilterTabChange,
                onToggleTagFilter = onToggleTagFilter,
                onToggleAuthorFilter = onToggleAuthorFilter,
                onToggleQualityFilter = onToggleQualityFilter,
                onClearFilters = onClearFilters
            )

            MainTab.Search -> SearchContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                libraryState = libraryState,
                searchState = searchState,
                searchItems = searchItems,
                listState = searchListState,
                gridState = searchGridState,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onOpenVideo = onOpenVideo,
                onMatchVideo = onMatchVideo,
                onPlayVideo = onPlayVideo,
                showRematchButtonInList = showRematchButtonInList,
                showGridCoverPlayButton = showGridCoverPlayButton,
                highlightedVideoUri = highlightedVideoUri
            )

            MainTab.Tasks -> MatchTaskContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = libraryState,
                listState = taskListState,
                onFilterChange = onMatchTaskFilterChange,
                onOpenTask = onOpenMatchTask,
                onSkipTask = onSkipMatchTask,
                onCancelSkippedTask = onCancelSkippedMatchTask,
                onSkipUnqueuedVideo = onSkipUnqueuedVideo,
                onRetryTask = onRetryMatchTask,
                onRetryFailedTasks = onRetryFailedTasks,
                onQueueDurationIssuesForReview = onQueueDurationIssuesForReview,
                onRetryNeedReviewTasks = onRetryNeedReviewTasks,
                onStartBatchMatch = onStartBatchMatch,
                onStartBatchRematch = onStartBatchRematch,
                onMatchVideo = onMatchVideo
            )
        }
    }
}


@Composable
private fun SourceScopeDropdown(
    state: LibraryUiState,
    onSourceScopeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.sourceScopes.firstOrNull { it.key == state.selectedSourceScopeKey }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = current?.label ?: "全部",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = "选择来源")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            state.sourceScopes.forEach { scope ->
                DropdownMenuItem(
                    text = { Text(scope.label) },
                    onClick = {
                        expanded = false
                        onSourceScopeChange(scope.key)
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryContent(
    modifier: Modifier,
    state: LibraryUiState,
    libraryItems: LazyPagingItems<VideoItem>,
    listState: LazyListState,
    directoryListState: LazyListState,
    gridState: LazyGridState,
    onOpenVideo: (VideoItem) -> Unit,
    onMatchVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onOpenDirectory: (LibraryFolderNode) -> Unit,
    onDirectoryUp: () -> Unit,
    highlightedDirectoryFolderKey: String?,
    highlightedVideoUri: String?,
    showRematchButtonInList: Boolean,
    showGridCoverPlayButton: Boolean
) {
    Column(modifier = modifier) {
        if (state.isScanning) {
            ScanProgressCard(state.scanDone, state.scanTotal, state.scanCurrentName)
        }

        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        ResultCountText("共 ${state.filteredCount} 个视频")

        if (state.layoutMode == LibraryLayoutMode.Directory) {
            DirectoryContent(
                state = state,
                listState = directoryListState,
                onOpenDirectory = onOpenDirectory,
                onDirectoryUp = onDirectoryUp,
                highlightedFolderKey = highlightedDirectoryFolderKey,
                highlightedVideoUri = highlightedVideoUri,
                onOpenVideo = onOpenVideo,
                onMatchVideo = onMatchVideo,
                onPlayVideo = onPlayVideo,
                showRematchButtonInList = showRematchButtonInList
            )
        } else if (!state.isScanning && state.filteredCount == 0) {
            EmptyHint("没有找到视频。")
        } else {
            VideoShelfContentPaged(
                items = libraryItems,
                layoutMode = state.layoutMode,
                gridColumns = state.gridColumns,
                listState = listState,
                gridState = gridState,
                onOpenVideo = onOpenVideo,
                onMatchVideo = onMatchVideo,
                onPlayVideo = onPlayVideo,
                showRematchButtonInList = showRematchButtonInList,
                showGridCoverPlayButton = showGridCoverPlayButton,
                highlightedVideoUri = highlightedVideoUri
            )
        }
    }
}

/**
 * 分页版的列表/网格外壳（仅库列表与搜索使用）；目录视图仍用普通 List。
 */
@Composable
private fun VideoShelfContentPaged(
    items: LazyPagingItems<VideoItem>,
    layoutMode: LibraryLayoutMode,
    gridColumns: Int,
    listState: LazyListState,
    gridState: LazyGridState,
    onOpenVideo: (VideoItem) -> Unit,
    onMatchVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    showRematchButtonInList: Boolean,
    showGridCoverPlayButton: Boolean,
    highlightedVideoUri: String?
) {
    if (layoutMode == LibraryLayoutMode.Grid) {
        VideoGridContentPaged(
            items = items,
            gridState = gridState,
            gridColumns = gridColumns,
            contentPadding = PaddingValues(8.dp),
            onOpenVideo = onOpenVideo,
            onPlayVideo = onPlayVideo,
            showPlayButton = showGridCoverPlayButton,
            highlightedVideoUri = highlightedVideoUri
        )
    } else {
        VideoListContentPaged(
            items = items,
            listState = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            onOpenVideo = onOpenVideo,
            onMatchVideo = onMatchVideo,
            onPlayVideo = onPlayVideo,
            showRematchButtonInList = showRematchButtonInList,
            highlightedVideoUri = highlightedVideoUri
        )
    }
}


@Composable
private fun DirectoryContent(
    state: LibraryUiState,
    listState: LazyListState,
    onOpenDirectory: (LibraryFolderNode) -> Unit,
    onDirectoryUp: () -> Unit,
    highlightedFolderKey: String?,
    highlightedVideoUri: String?,
    onOpenVideo: (VideoItem) -> Unit,
    onMatchVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    showRematchButtonInList: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDirectoryUp,
                enabled = canNavigateDirectoryUp(state)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一级")
            }
            Text(
                text = directoryTitle(state),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(state.directoryFolders, key = { "folder:${it.sourceId}:${it.path}" }) { folder ->
            val folderKey = folderItemKey(folder)
            val backgroundColor = animatedHighlightColor(
                highlighted = highlightedFolderKey == folderKey
            )
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = backgroundColor
                ),
                leadingContent = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(folder.sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth().clickable { onOpenDirectory(folder) }
            )
        }
        items(state.directoryVideos, key = { it.uriString }) { video ->
            DirectoryVideoItem(
                video = video,
                highlighted = highlightedVideoUri == video.uriString,
                onOpen = { onOpenVideo(video) },
                onMatch = { onMatchVideo(video) },
                showRematchButton = video.matchedIwaraId == null || showRematchButtonInList
            )
        }
        if (state.directoryFolders.isEmpty() && state.directoryVideos.isEmpty()) {
            item { EmptyTaskText("当前目录没有视频。") }
        }
    }
    }
}

private fun directoryStateKey(state: LibraryUiState): String {
    return directoryStateKey(
        selectedSourceScopeKey = state.selectedSourceScopeKey,
        sourceId = state.currentDirectorySourceId,
        path = state.currentDirectoryPath
    )
}

private fun directoryStateKey(
    selectedSourceScopeKey: String,
    sourceId: String?,
    path: String
): String {
    return "$selectedSourceScopeKey:${sourceId.orEmpty()}:$path"
}

private fun parentDirectoryStateKey(state: LibraryUiState): String? {
    val sourceId = state.currentDirectorySourceId ?: return null
    val path = state.currentDirectoryPath
    return if (path.isBlank()) {
        val selectedScope = state.sourceScopes.firstOrNull { it.key == state.selectedSourceScopeKey }
        if (selectedScope?.sourceIds?.singleOrNull() == sourceId) {
            null
        } else {
            directoryStateKey(state.selectedSourceScopeKey, null, "")
        }
    } else {
        directoryStateKey(
            selectedSourceScopeKey = state.selectedSourceScopeKey,
            sourceId = sourceId,
            path = path.substringBeforeLast('/', missingDelimiterValue = "")
        )
    }
}

private fun currentDirectoryFolderKey(state: LibraryUiState): String? {
    val sourceId = state.currentDirectorySourceId ?: return null
    return "folder:$sourceId:${state.currentDirectoryPath}"
}

private fun folderItemKey(folder: LibraryFolderNode): String {
    return "folder:${folder.sourceId}:${folder.path}"
}

@Composable
private fun DirectoryVideoItem(
    video: VideoItem,
    highlighted: Boolean,
    onOpen: () -> Unit,
    onMatch: () -> Unit,
    showRematchButton: Boolean
) {
    val containerColor = animatedHighlightColor(highlighted)

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = containerColor
        ),
        leadingContent = {
            if (video.coverFilePath != null) {
                AsyncImage(
                    model = File(video.coverFilePath),
                    contentDescription = video.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(74.dp).height(42.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Box(
                    modifier = Modifier.width(74.dp).height(42.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Text("无", style = MaterialTheme.typography.labelSmall) }
            }
        },
        headlineContent = { Text(video.title ?: video.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(buildVideoInfoLine(video), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            if (showRematchButton) {
                TextButton(onClick = onMatch) { Text(if (video.matchedIwaraId == null) "匹配" else "重匹配") }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    )
}

private fun directoryTitle(state: LibraryUiState): String {
    val source = state.librarySources.firstOrNull { it.id == state.currentDirectorySourceId }?.name
    val path = state.currentDirectoryPath
    return when {
        source == null -> "来源根目录"
        path.isBlank() -> source
        else -> "$source / $path"
    }
}

private fun canNavigateDirectoryUp(state: LibraryUiState): Boolean {
    val sourceId = state.currentDirectorySourceId ?: return false
    if (state.currentDirectoryPath.isNotBlank()) return true
    val selectedScope = state.sourceScopes.firstOrNull { it.key == state.selectedSourceScopeKey }
    return selectedScope?.sourceIds?.size != 1 || selectedScope.sourceIds.singleOrNull() != sourceId
}

@Composable
private fun SearchContent(
    modifier: Modifier,
    libraryState: LibraryUiState,
    searchState: SearchUiState,
    searchItems: LazyPagingItems<VideoItem>,
    listState: LazyListState,
    gridState: LazyGridState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onMatchVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    showRematchButtonInList: Boolean,
    showGridCoverPlayButton: Boolean,
    highlightedVideoUri: String?
) {
    Column(modifier = modifier) {
        SearchRow(searchState.query, onQueryChange, onClearQuery)

        if (searchState.query.isBlank()) {
            Text(
                text = "输入标题、文件名、Iwara ID 或清晰度进行搜索。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return
        }

        ResultCountText("共 ${searchState.resultCount} 个视频")

        if (searchState.resultCount == 0) {
            EmptyHint("没有搜索结果。")
        } else {
            VideoShelfContentPaged(
                items = searchItems,
                layoutMode = libraryState.layoutMode,
                gridColumns = libraryState.gridColumns,
                listState = listState,
                gridState = gridState,
                onOpenVideo = onOpenVideo,
                onMatchVideo = onMatchVideo,
                onPlayVideo = onPlayVideo,
                showRematchButtonInList = showRematchButtonInList,
                showGridCoverPlayButton = showGridCoverPlayButton,
                highlightedVideoUri = highlightedVideoUri
            )
        }
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索标题、文件名或 Iwara ID") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        TextButton(
            onClick = onClearQuery,
            enabled = query.isNotBlank()
        ) {
            Text("清除")
        }
    }
}

@Composable
private fun animatedHighlightColor(
    highlighted: Boolean
): Color {
    val targetColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = HighlightFadeMillis),
        label = "itemHighlight"
    )
    return color
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterContent(
    modifier: Modifier,
    state: LibraryUiState,
    listState: LazyListState,
    onFilterTabChange: (FilterTab) -> Unit,
    onToggleTagFilter: (String) -> Unit,
    onToggleAuthorFilter: (String) -> Unit,
    onToggleQualityFilter: (String) -> Unit,
    onClearFilters: () -> Unit
) {
    val filterItems = when (state.filterTab) {
        FilterTab.Tags -> state.tagCounts
        FilterTab.Authors -> state.authorCounts
        FilterTab.Quality -> state.qualityCounts
    }
    var sortMode by remember(state.filterTab) {
        mutableStateOf(FilterSortMode.CountDesc)
    }
    // 在后台线程排序，避免标签很多时阻塞切换到本界面的那一帧；
    // 计算完成前为 null，界面先瞬间进入并显示“加载中”。
    val sortedFilterItems by produceState<List<CountItem>?>(
        initialValue = null,
        filterItems,
        sortMode
    ) {
        value = null
        value = withContext(Dispatchers.Default) {
            when (sortMode) {
                FilterSortMode.CountDesc -> filterItems.sortedWith(
                    compareByDescending<CountItem> { it.count }
                        .thenBy { it.label.lowercase() }
                )

                FilterSortMode.NameAsc -> filterItems.sortedWith(
                    compareBy<CountItem> { it.label.lowercase() }
                        .thenByDescending { it.count }
                )
            }
        }
    }
    val selectedCount = state.selectedTagKeys.size +
            state.selectedAuthorKeys.size +
            state.selectedQualities.size
    val hasFilters = selectedCount > 0

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = state.filterTab.ordinal) {
            listOf(FilterTab.Tags, FilterTab.Authors, FilterTab.Quality).forEach { tab ->
                Tab(
                    selected = state.filterTab == tab,
                    onClick = { onFilterTabChange(tab) },
                    text = { Text(filterTabLabel(tab)) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filterTabLabel(state.filterTab)}（${filterItems.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = onClearFilters,
                        enabled = hasFilters
                    ) {
                        Text("清除")
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CompactChip(
                        selected = sortMode == FilterSortMode.CountDesc,
                        onClick = { sortMode = FilterSortMode.CountDesc },
                        text = "出现次数"
                    )
                    CompactChip(
                        selected = sortMode == FilterSortMode.NameAsc,
                        onClick = { sortMode = FilterSortMode.NameAsc },
                        text = "首字母"
                    )
                }
            }

            if (hasFilters) {
                item {
                    Text(
                        text = "已选择 $selectedCount 个筛选条件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val readyItems = sortedFilterItems
            if (filterItems.isEmpty()) {
                item {
                    Text(
                        text = "暂无可筛选项。匹配 Iwara 元数据后会显示标签和作者。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (readyItems == null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        readyItems.forEach { item ->
                            val selected = when (state.filterTab) {
                                FilterTab.Tags -> item.key in state.selectedTagKeys
                                FilterTab.Authors -> item.key in state.selectedAuthorKeys
                                FilterTab.Quality -> item.key in state.selectedQualities
                            }
                            CompactChip(
                                selected = selected,
                                onClick = {
                                    when (state.filterTab) {
                                        FilterTab.Tags -> onToggleTagFilter(item.key)
                                        FilterTab.Authors -> onToggleAuthorFilter(item.key)
                                        FilterTab.Quality -> onToggleQualityFilter(item.key)
                                    }
                                },
                                text = "${item.label}(${item.count})",
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.FilterAlt, contentDescription = null) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class FilterSortMode {
    CountDesc,
    NameAsc
}

private fun videoSortModeLabel(mode: VideoSortMode): String {
    return when (mode) {
        VideoSortMode.NameAsc -> "名称 A-Z"
        VideoSortMode.NameDesc -> "名称 Z-A"
        VideoSortMode.FileTimeDesc -> "修改时间 新到旧"
        VideoSortMode.FileTimeAsc -> "修改时间 旧到新"
        VideoSortMode.DurationDesc -> "时长 长到短"
        VideoSortMode.DurationAsc -> "时长 短到长"
        VideoSortMode.FileSizeDesc -> "文件大小 大到小"
        VideoSortMode.FileSizeAsc -> "文件大小 小到大"
    }
}

@Composable
private fun MatchTaskContent(
    modifier: Modifier,
    state: LibraryUiState,
    listState: LazyListState,
    onFilterChange: (MatchTaskFilter) -> Unit,
    onOpenTask: (MatchTaskEntity) -> Unit,
    onSkipTask: (MatchTaskEntity) -> Unit,
    onCancelSkippedTask: (MatchTaskEntity) -> Unit,
    onSkipUnqueuedVideo: (VideoItem) -> Unit,
    onRetryTask: (MatchTaskEntity) -> Unit,
    onRetryFailedTasks: () -> Unit,
    onQueueDurationIssuesForReview: () -> Unit,
    onRetryNeedReviewTasks: () -> Unit,
    onStartBatchMatch: () -> Unit,
    onStartBatchRematch: () -> Unit,
    onMatchVideo: (VideoItem) -> Unit
) {
    var showStartBatchDialog by remember { mutableStateOf(false) }
    var showBatchRematchDialog by remember { mutableStateOf(false) }
    val taskVideoByUri = remember(
        state.directoryVideos,
        state.unqueuedVideos
    ) {
        (state.directoryVideos + state.unqueuedVideos)
            .associateBy { it.uriString }
    }
    if (showStartBatchDialog) {
        AlertDialog(
            onDismissRequest = { showStartBatchDialog = false },
            title = { Text("批量匹配未匹配视频？") },
            text = { Text("会为尚未绑定 Iwara 元数据、且未进入任务队列的视频创建匹配任务。") },
            confirmButton = {
                Button(
                    onClick = {
                        showStartBatchDialog = false
                        onStartBatchMatch()
                    },
                    enabled = !state.isBatchMatching
                ) {
                    Text("开始")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartBatchDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (showBatchRematchDialog) {
        AlertDialog(
            onDismissRequest = { showBatchRematchDialog = false },
            title = { Text("重新匹配全部已有 ID 的视频？") },
            text = { Text("会优先按已绑定 Iwara ID，其次按文件名解析出的 ID 重新获取远程详情，用于补全头像、标签、统计等遗漏字段。") },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchRematchDialog = false
                        onStartBatchRematch()
                    },
                    enabled = !state.isBatchMatching
                ) {
                    Text("开始")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchRematchDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TaskFilterRow(
                selected = state.taskFilter,
                counts = state.matchTaskFilterCounts,
                isBatchMatching = state.isBatchMatching,
                onFilterChange = onFilterChange,
                onRetryFailedTasks = onRetryFailedTasks,
                onQueueDurationIssuesForReview = onQueueDurationIssuesForReview,
                onRetryNeedReviewTasks = onRetryNeedReviewTasks,
                onStartBatchMatch = { showStartBatchDialog = true },
                onStartBatchRematch = { showBatchRematchDialog = true }
            )
        }

        if (state.error != null) {
            item {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (state.isBatchMatching) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (state.taskFilter == MatchTaskFilter.Unqueued) {
            if (state.unqueuedVideos.isEmpty()) {
                item { EmptyTaskText("没有未匹配视频。") }
            } else {
                items(state.unqueuedVideos, key = { it.uriString }) { video ->
                    UnqueuedVideoItem(
                        video = video,
                        onOpen = { onMatchVideo(video) },
                        onMatch = { onMatchVideo(video) },
                        onSkip = { onSkipUnqueuedVideo(video) }
                    )
                }
            }
            return@LazyColumn
        }

        if (state.matchTasks.isEmpty()) {
            item { EmptyTaskText("没有任务。") }
        } else {
            items(state.matchTasks, key = { it.id }) { task ->
                val currentVideo = taskVideoByUri[task.videoUriString]
                MatchTaskItem(
                    task = task,
                    coverPath = currentVideo?.coverFilePath ?: task.coverFilePath,
                    onOpen = { onOpenTask(task) },
                    onSkip = if (state.taskFilter == MatchTaskFilter.Failed) {
                        { onSkipTask(task) }
                    } else null,
                    onRetry = if (state.taskFilter == MatchTaskFilter.Failed) {
                        { onRetryTask(task) }
                    } else null,
                    onCancelSkipped = if (state.taskFilter == MatchTaskFilter.Skipped) {
                        { onCancelSkippedTask(task) }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFilterRow(
    selected: MatchTaskFilter,
    counts: Map<MatchTaskFilter, Int>,
    isBatchMatching: Boolean,
    onFilterChange: (MatchTaskFilter) -> Unit,
    onRetryFailedTasks: () -> Unit,
    onQueueDurationIssuesForReview: () -> Unit,
    onRetryNeedReviewTasks: () -> Unit,
    onStartBatchMatch: () -> Unit,
    onStartBatchRematch: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            MatchTaskFilter.entries.forEach { filter ->
                CompactChip(
                    selected = selected == filter,
                    onClick = { onFilterChange(filter) },
                    text = "${matchTaskFilterLabel(filter)}(${counts[filter] ?: 0})"
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CompactChip(
                selected = false,
                onClick = onStartBatchMatch,
                enabled = !isBatchMatching,
                text = if (isBatchMatching) "匹配中" else "匹配未匹配"
            )
            CompactChip(
                selected = false,
                onClick = onRetryFailedTasks,
                enabled = !isBatchMatching,
                text = if (isBatchMatching) "重试中" else "重试失败"
            )
            CompactChip(
                selected = false,
                onClick = onQueueDurationIssuesForReview,
                enabled = !isBatchMatching,
                text = "时长复核"
            )
            CompactChip(
                selected = false,
                onClick = onRetryNeedReviewTasks,
                enabled = !isBatchMatching,
                text = if (isBatchMatching) "重试中" else "重试复核"
            )
            CompactChip(
                selected = false,
                onClick = onStartBatchRematch,
                enabled = !isBatchMatching,
                text = if (isBatchMatching) "重匹配中" else "全部重匹配"
            )
        }
    }
}

@Composable
private fun UnqueuedVideoItem(
    video: VideoItem,
    onOpen: () -> Unit,
    onMatch: () -> Unit,
    onSkip: () -> Unit
) {
    ListItem(
        leadingContent = {
            TaskPreview(
                coverPath = video.coverFilePath,
                contentDescription = video.displayName
            )
        },
        headlineContent = {
            Text(video.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = video.title ?: video.sourceVideoId ?: "未解析标题",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TaskActionButton(onClick = onMatch) {
                    TaskActionIcon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = "匹配"
                    )
                }
                TaskActionButton(onClick = onSkip) {
                    TaskActionIcon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "跳过"
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    )
}

@Composable
private fun MatchTaskItem(
    task: MatchTaskEntity,
    coverPath: String?,
    onOpen: () -> Unit,
    onSkip: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onCancelSkipped: (() -> Unit)?
) {
    ListItem(
        leadingContent = {
            TaskPreview(
                coverPath = coverPath,
                contentDescription = task.displayName
            )
        },
        headlineContent = {
            Text(task.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text("状态：${matchTaskStatusLabel(task.status)} · 候选：${task.candidateCount}")
                Text("搜索词：${task.query}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!task.errorMessage.isNullOrBlank()) {
                    Text(
                        text = task.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        trailingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onRetry != null) {
                    TaskActionButton(onClick = onRetry) {
                        TaskActionIcon(Icons.Filled.Refresh, "重试")
                    }
                }
                if (onSkip != null) {
                    TaskActionButton(onClick = onSkip) {
                        TaskActionIcon(Icons.Filled.FastForward, "跳过")
                    }
                }
                if (onCancelSkipped != null) {
                    TaskActionButton(onClick = onCancelSkipped) {
                        TaskActionIcon(Icons.Filled.Close, "取消跳过")
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    )
}

@Composable
private fun CompactChip(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            label = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = leadingIcon,
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
private fun TaskPreview(
    coverPath: String?,
    contentDescription: String
) {
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(74.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "无",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TaskActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            content()
        }
    }
}

@Composable
private fun TaskActionIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun NoFolderContent(
    modifier: Modifier,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "还没有设置本地 Iwara 视频目录。",
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("前往设置")
        }
    }
}

@Composable
private fun ScanProgressCard(
    done: Int,
    total: Int,
    currentName: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (total > 0) "正在扫描并生成封面：$done / $total" else "正在扫描视频...",
            style = MaterialTheme.typography.bodyMedium
        )
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (!currentName.isNullOrBlank()) {
            Text(
                text = currentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ResultCountText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyTaskText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

private fun filterTabLabel(tab: FilterTab): String {
    return when (tab) {
        FilterTab.Tags -> "标签"
        FilterTab.Authors -> "作者"
        FilterTab.Quality -> "清晰度"
    }
}

private fun matchTaskFilterLabel(filter: MatchTaskFilter): String {
    return when (filter) {
        MatchTaskFilter.All -> "全部"
        MatchTaskFilter.Running -> "进行中"
        MatchTaskFilter.Success -> "成功"
        MatchTaskFilter.NeedReview -> "需复核"
        MatchTaskFilter.Failed -> "失败"
        MatchTaskFilter.Skipped -> "跳过"
        MatchTaskFilter.Unqueued -> "未匹配"
    }
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
