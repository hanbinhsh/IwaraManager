package com.ice.iwaramanager

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ice.iwaramanager.data.model.MainTab
import com.ice.iwaramanager.data.model.Routes
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.VideoOpenMode
import com.ice.iwaramanager.data.model.VideoPlayerApp
import com.ice.iwaramanager.ui.screen.DetailScreen
import com.ice.iwaramanager.ui.screen.LibraryScreen
import com.ice.iwaramanager.ui.screen.PlayerScreen

@Composable
fun AppRoot(
    viewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val videoPlayerApps = remember {
        loadVideoPlayerApps(context)
    }
    val libraryState by viewModel.libraryState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val detailState by viewModel.detailState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val matchState by viewModel.matchState.collectAsState()
    val matchTaskDetailState by viewModel.matchTaskDetailState.collectAsState()

    val currentRoute = remember {
        mutableStateOf(Routes.Library)
    }

    val selectedTab = remember {
        mutableStateOf(MainTab.Library)
    }

    val tabReselectTick = remember {
        mutableStateOf(0L)
    }

    val routeBeforePlayer = remember {
        mutableStateOf(Routes.Library)
    }

    val routeBeforeSettings = remember {
        mutableStateOf(Routes.Library)
    }

    val tabBeforeDetail = remember {
        mutableStateOf(MainTab.Library)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderPicked(uri)
            selectedTab.value = MainTab.Library
            currentRoute.value = Routes.Library
        }
    }

    fun openMainTab(tab: MainTab) {
        if (currentRoute.value != Routes.Library) {
            currentRoute.value = Routes.Library
        }

        if (selectedTab.value == tab) {
            tabReselectTick.value += 1L
        } else {
            selectedTab.value = tab
        }
    }

    fun playVideo(video: VideoItem, sourceRoute: String) {
        when (settingsState.videoOpenMode) {
            VideoOpenMode.InApp -> {
                viewModel.openVideoPlayer(video)
                routeBeforePlayer.value = sourceRoute
                currentRoute.value = Routes.Player
            }

            VideoOpenMode.ExternalPlayer -> {
                val componentName = settingsState.selectedVideoPlayerComponentName

                if (componentName.isNullOrBlank()) {
                    Toast.makeText(context, "请先在设置中选择默认播放器", Toast.LENGTH_SHORT).show()
                } else {
                    openVideoWithExternalPlayer(
                        context = context,
                        video = video,
                        componentName = componentName
                    )
                }
            }
        }
    }

    BackHandler(
        enabled = currentRoute.value != Routes.Library
    ) {
        when (currentRoute.value) {
            Routes.Player -> {
                currentRoute.value = routeBeforePlayer.value
            }

            Routes.Detail -> {
                selectedTab.value = tabBeforeDetail.value
                currentRoute.value = Routes.Library
            }

            Routes.Settings -> {
                currentRoute.value = routeBeforeSettings.value
            }

            Routes.Match,
            Routes.MatchTaskDetail -> {
                currentRoute.value = Routes.Library
            }

            else -> {
                currentRoute.value = Routes.Library
            }
        }
    }

    when (currentRoute.value) {
        Routes.Library,
        Routes.Search -> {
            LibraryScreen(
                libraryState = libraryState,
                searchState = searchState,
                selectedTab = selectedTab.value,
                tabReselectTick = tabReselectTick.value,
                onSelectTab = ::openMainTab,
                onOpenSettings = {
                    routeBeforeSettings.value = Routes.Library
                    currentRoute.value = Routes.Settings
                },
                onRescan = viewModel::rescanLibrary,
                onLayoutModeChange = viewModel::setLibraryLayoutMode,
                onQueryChange = viewModel::updateSearchQuery,
                onClearQuery = viewModel::clearSearchQuery,
                onFilterTabChange = viewModel::setFilterTab,
                onToggleTagFilter = viewModel::toggleTagFilter,
                onToggleAuthorFilter = viewModel::toggleAuthorFilter,
                onToggleQualityFilter = viewModel::toggleQualityFilter,
                onClearFilters = viewModel::clearFilters,
                onMatchTaskFilterChange = viewModel::setMatchTaskFilter,
                onOpenMatchTask = { task ->
                    viewModel.openMatchTask(task)
                    currentRoute.value = Routes.MatchTaskDetail
                },
                onSkipMatchTask = viewModel::skipMatchTask,
                onCancelSkippedMatchTask = viewModel::cancelSkippedMatchTask,
                onSkipUnqueuedVideo = viewModel::skipUnqueuedVideo,
                onRetryMatchTask = viewModel::retryMatchTask,
                onRetryFailedTasks = viewModel::retryFailedMatchTasks,
                onStartBatchMatch = viewModel::startBatchMatchUnmatched,
                onOpenVideo = { video ->
                    viewModel.openVideoDetail(video)
                    tabBeforeDetail.value = selectedTab.value
                    currentRoute.value = Routes.Detail
                },
                onMatchVideo = { video ->
                    viewModel.openMatchVideo(video)
                    currentRoute.value = Routes.Match
                },
                onPlayVideo = { video ->
                    playVideo(video, Routes.Library)
                }
            )
        }

        Routes.Settings -> {
            com.ice.iwaramanager.ui.screen.SettingsScreen(
                state = settingsState,
                videoPlayerApps = videoPlayerApps,
                onBack = {
                    currentRoute.value = routeBeforeSettings.value
                },
                onPickFolder = {
                    folderPicker.launch(null)
                },
                onRescan = viewModel::rescanLibrary,
                onClearDatabase = viewModel::clearDatabase,
                onClearFolder = viewModel::clearLibraryFolder,
                onLayoutModeChange = viewModel::setLibraryLayoutMode,
                onGridColumnsChange = viewModel::setGridColumns,
                onVideoOpenModeChange = viewModel::setVideoOpenMode,
                onExternalVideoPlayerChange = viewModel::setExternalVideoPlayer,
                onResetVideoOpenMode = viewModel::resetVideoOpenMode,
                onIwaraMatchModeChange = viewModel::setIwaraMatchMode,
                onMatchSearchTimeoutSecondsChange = viewModel::setMatchSearchTimeoutSeconds,
                onBatchMatchThreadsChange = viewModel::setBatchMatchThreads
            )
        }

        Routes.Detail -> {
            DetailScreen(
                state = detailState,
                onBack = {
                    selectedTab.value = tabBeforeDetail.value
                    currentRoute.value = Routes.Library
                },
                onPlay = { video ->
                    playVideo(video, Routes.Detail)
                },
                onMatch = { video ->
                    viewModel.openMatchVideo(video)
                    currentRoute.value = Routes.Match
                },
                onTagClick = { tagKey ->
                    viewModel.openTagFilterFromDetail(tagKey)
                    selectedTab.value = MainTab.Filters
                    currentRoute.value = Routes.Library
                },
                onAuthorClick = { authorKey ->
                    viewModel.openAuthorFilterFromDetail(authorKey)
                    selectedTab.value = MainTab.Filters
                    currentRoute.value = Routes.Library
                }
            )
        }

        Routes.Match -> {
            com.ice.iwaramanager.ui.screen.MatchScreen(
                state = matchState,
                onBack = {
                    currentRoute.value = Routes.Library
                },
                onQueryChange = viewModel::updateMatchQuery,
                onSearch = viewModel::searchMatchCandidates,
                onIdMatch = viewModel::idMatchCurrentVideo,
                onToggleSearchDiagnosticRaw = viewModel::toggleSearchDiagnosticRaw,
                onOpenWeb = {
                    val query = matchState.query.trim()
                    if (query.isNotBlank()) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.iwara.tv/search?query=${Uri.encode(query)}")
                            )
                        )
                    }
                },
                onBind = { meta ->
                    viewModel.bindIwaraMeta(meta) {
                        currentRoute.value = Routes.Detail
                    }
                },
                onPlay = { video ->
                    playVideo(video, Routes.Match)
                }
            )
        }

        Routes.MatchTaskDetail -> {
            com.ice.iwaramanager.ui.screen.MatchTaskDetailScreen(
                state = matchTaskDetailState,
                onBack = {
                    currentRoute.value = Routes.Library
                },
                onOpenMatchPage = { task ->
                    viewModel.openMatchFromTask(task)
                    currentRoute.value = Routes.Match
                },
                onBindCandidate = viewModel::bindMatchCandidate,
                onSkipTask = viewModel::skipMatchTask,
                onPlayTask = { task ->
                    viewModel.openVideoFromTask(task) { video ->
                        playVideo(video, Routes.MatchTaskDetail)
                    }
                }
            )
        }

        Routes.Player -> {
            PlayerScreen(
                state = playerState,
                onBack = {
                    currentRoute.value = routeBeforePlayer.value
                }
            )
        }
    }
}

private fun openVideoWithExternalPlayer(
    context: Context,
    video: VideoItem,
    componentName: String
) {
    val component = ComponentName.unflattenFromString(componentName)

    if (component == null) {
        Toast.makeText(context, "保存的播放器配置无效", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            Uri.parse(video.uriString),
            videoMimeType(video)
        )
        this.component = component
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "已选择的播放器不可用", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "无法授予外部播放器读取权限", Toast.LENGTH_SHORT).show()
    }
}

private fun loadVideoPlayerApps(
    context: Context
): List<VideoPlayerApp> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setType("video/*")
    }

    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0L)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, 0)
    }

    return resolveInfos
        .mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()
                ?: activityInfo.applicationInfo.loadLabel(packageManager).toString()

            VideoPlayerApp(
                label = label,
                packageName = activityInfo.packageName,
                activityName = activityInfo.name
            )
        }
        .distinctBy { it.componentName }
        .sortedBy { it.label.lowercase() }
}

private fun videoMimeType(video: VideoItem): String {
    return when (video.extension?.lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "wmv" -> "video/x-ms-wmv"
        else -> "video/*"
    }
}
