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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.ice.iwaramanager.data.model.MainTab
import com.ice.iwaramanager.data.model.Routes
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.LibraryFolderNode
import com.ice.iwaramanager.data.model.LibrarySourceType
import com.ice.iwaramanager.data.model.VideoOpenMode
import com.ice.iwaramanager.data.model.VideoPlayerApp
import com.ice.iwaramanager.ui.screen.DetailScreen
import com.ice.iwaramanager.ui.screen.IwaraLoginWebViewScreen
import com.ice.iwaramanager.ui.screen.LibraryScreen
import com.ice.iwaramanager.ui.screen.PlayerScreen
import com.ice.iwaramanager.data.remote.WebDavClient
import com.ice.iwaramanager.domain.security.WebDavCredentialStore
import com.ice.iwaramanager.playback.RemotePlaybackProxyService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val libraryItems = viewModel.libraryVideos.collectAsLazyPagingItems()
    val searchItems = viewModel.searchVideos.collectAsLazyPagingItems()

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

    val routeBeforeIwaraLogin = remember {
        mutableStateOf(Routes.Settings)
    }

    val routeBeforeWebView = remember {
        mutableStateOf(Routes.Library)
    }

    val iwaraWebViewUrl = remember {
        mutableStateOf("https://www.iwara.tv")
    }

    val tabBeforeDetail = remember {
        mutableStateOf(MainTab.Library)
    }

    val pendingDetailVideoUri = remember {
        mutableStateOf<String?>(null)
    }

    val highlightedVideoUri = remember {
        mutableStateOf<String?>(null)
    }

    // 在不同路由间切换时保留各屏幕的可保存状态（如列表滚动位置），
    // 这样从详情页返回时库列表会停在原来的位置而不是回到顶部。
    val saveableStateHolder = rememberSaveableStateHolder()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderPicked(uri)
            selectedTab.value = MainTab.Library
            currentRoute.value = Routes.Library
        }
    }

    val databaseExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportDatabase(uri)
        }
    }

    val databaseImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importDatabase(uri)
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

    fun backToLibraryFromDetailWithHighlight() {
        highlightedVideoUri.value = detailState.video?.uriString ?: pendingDetailVideoUri.value
        pendingDetailVideoUri.value = null
        selectedTab.value = tabBeforeDetail.value
        currentRoute.value = Routes.Library
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
                        componentName = componentName,
                        settingsState = settingsState
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
                backToLibraryFromDetailWithHighlight()
            }

            Routes.Settings -> {
                currentRoute.value = routeBeforeSettings.value
            }

            Routes.IwaraLogin -> {
                currentRoute.value = routeBeforeIwaraLogin.value
            }

            Routes.IwaraWebView -> {
                currentRoute.value = routeBeforeWebView.value
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

    saveableStateHolder.SaveableStateProvider(currentRoute.value) {
    when (currentRoute.value) {
        Routes.Library,
        Routes.Search -> {
            LibraryScreen(
                libraryState = libraryState,
                searchState = searchState,
                libraryItems = libraryItems,
                searchItems = searchItems,
                selectedTab = selectedTab.value,
                tabReselectTick = tabReselectTick.value,
                showRematchButtonInList = settingsState.showRematchButtonInList,
                showGridCoverPlayButton = settingsState.showGridCoverPlayButton,
                highlightedVideoUri = highlightedVideoUri.value,
                onHighlightedVideoConsumed = {
                    highlightedVideoUri.value = null
                },
                onSelectTab = ::openMainTab,
                onSourceScopeChange = viewModel::selectSourceScope,
                onOpenDirectory = viewModel::openDirectory,
                onDirectoryUp = viewModel::navigateDirectoryUp,
                onOpenSettings = {
                    routeBeforeSettings.value = Routes.Library
                    currentRoute.value = Routes.Settings
                },
                onRescan = viewModel::rescanLibrary,
                onLayoutModeChange = viewModel::setLibraryLayoutMode,
                onVideoSortModeChange = viewModel::setVideoSortMode,
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
                onQueueDurationIssuesForReview = viewModel::queueDurationIssuesForReview,
                onRetryNeedReviewTasks = viewModel::retryNeedReviewTasks,
                onStartBatchMatch = viewModel::startBatchMatchUnmatched,
                onStartBatchRematch = viewModel::startBatchRematchMatched,
                onOpenVideo = { video ->
                    if (settingsState.openVideoDirectlyInPlayer) {
                        playVideo(video, Routes.Library)
                    } else {
                        pendingDetailVideoUri.value = video.uriString
                        viewModel.openVideoDetail(video)
                        tabBeforeDetail.value = selectedTab.value
                        currentRoute.value = Routes.Detail
                    }
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
                onCleanupMissingRecords = viewModel::cleanupMissingFromConfiguredSources,
                onExportDatabase = {
                    databaseExporter.launch(defaultDatabaseFileName())
                },
                onImportDatabase = {
                    databaseImporter.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/vnd.sqlite3",
                            "application/x-sqlite3",
                            "*/*"
                        )
                    )
                },
                onWebDavNameChange = viewModel::updateWebDavName,
                onWebDavBaseUrlChange = viewModel::updateWebDavBaseUrl,
                onWebDavRootPathChange = viewModel::updateWebDavRootPath,
                onWebDavUsernameChange = viewModel::updateWebDavUsername,
                onWebDavPasswordChange = viewModel::updateWebDavPassword,
                onWebDavAllowInsecureTlsChange = viewModel::setWebDavAllowInsecureTls,
                onWebDavIndexModeChange = viewModel::setWebDavIndexMode,
                onWebDavConnectTimeoutSecondsChange = viewModel::setWebDavConnectTimeoutSeconds,
                onWebDavReadTimeoutSecondsChange = viewModel::setWebDavReadTimeoutSeconds,
                onTestWebDav = viewModel::testWebDavSource,
                onPrepareNewWebDav = viewModel::prepareNewWebDavSource,
                onEditWebDav = viewModel::editWebDavSource,
                onAddWebDav = viewModel::addWebDavSource,
                onRenameSource = viewModel::renameLibrarySource,
                onDeleteSource = viewModel::deleteLibrarySource,
                onScanSource = viewModel::scanLibrarySource,
                onRemoteProxyIdleTimeoutSecondsChange = viewModel::setRemoteProxyIdleTimeoutSeconds,
                onRemoteProxyReadTimeoutSecondsChange = viewModel::setRemoteProxyReadTimeoutSeconds,
                onShowRemotePlaybackDiagnosticsChange = viewModel::setShowRemotePlaybackDiagnostics,
                onLayoutModeChange = viewModel::setLibraryLayoutMode,
                onGridColumnsChange = viewModel::setGridColumns,
                onShowRematchButtonInListChange = viewModel::setShowRematchButtonInList,
                onOpenVideoDirectlyInPlayerChange = viewModel::setOpenVideoDirectlyInPlayer,
                onShowGridCoverPlayButtonChange = viewModel::setShowGridCoverPlayButton,
                onVideoOpenModeChange = viewModel::setVideoOpenMode,
                onExternalVideoPlayerChange = viewModel::setExternalVideoPlayer,
                onResetVideoOpenMode = viewModel::resetVideoOpenMode,
                onOpenIwaraLogin = {
                    routeBeforeIwaraLogin.value = Routes.Settings
                    currentRoute.value = Routes.IwaraLogin
                },
                onCheckIwaraLoginStatus = viewModel::checkIwaraLoginStatus,
                onClearIwaraLoginSession = viewModel::clearIwaraLoginSession,
                onIwaraMatchModeChange = viewModel::setIwaraMatchMode,
                onMatchSearchTimeoutSecondsChange = viewModel::setMatchSearchTimeoutSeconds,
                onApiProbeTimeoutSecondsChange = viewModel::setApiProbeTimeoutSeconds,
                onApiEndpointTemplatesChange = viewModel::setApiEndpointTemplates,
                onResetApiEndpointTemplates = viewModel::resetApiEndpointTemplates,
                onApiRequestHeadersChange = viewModel::setApiRequestHeaders,
                onResetApiRequestHeaders = viewModel::resetApiRequestHeaders,
                onAllowPageFallbackChange = viewModel::setAllowPageFallback,
                onFetchSearchResultDetailsWithApiChange = viewModel::setFetchSearchResultDetailsWithApi,
                onMaxSearchApiDetailsChange = viewModel::setMaxSearchApiDetails,
                onBatchMatchThreadsChange = viewModel::setBatchMatchThreads,
                onAutoMatchSingleHighConfidenceChange = viewModel::setAutoMatchSingleHighConfidence,
                onAutoMatchTitleSimilarityThresholdChange = viewModel::setAutoMatchTitleSimilarityThreshold,
                onAutoMatchDurationToleranceSecondsChange = viewModel::setAutoMatchDurationToleranceSeconds,
                onAutoMatchSkipNoIdChange = viewModel::setAutoMatchSkipNoId
            )
        }

        Routes.IwaraLogin -> {
            IwaraLoginWebViewScreen(
                onBack = {
                    currentRoute.value = routeBeforeIwaraLogin.value
                },
                onComplete = {
                    viewModel.markIwaraLoginCompleted()
                    currentRoute.value = routeBeforeIwaraLogin.value
                }
            )
        }

        Routes.IwaraWebView -> {
            IwaraLoginWebViewScreen(
                onBack = {
                    currentRoute.value = routeBeforeWebView.value
                },
                onComplete = {
                    currentRoute.value = routeBeforeWebView.value
                },
                initialUrl = iwaraWebViewUrl.value,
                defaultTitle = "Iwara 页面",
                showCompleteAction = false
            )
        }

        Routes.Detail -> {
            DetailScreen(
                state = detailState,
                onBack = {
                    backToLibraryFromDetailWithHighlight()
                },
                onPlay = { video ->
                    playVideo(video, Routes.Detail)
                },
                onMatch = { video ->
                    pendingDetailVideoUri.value = null
                    viewModel.openMatchVideo(video)
                    currentRoute.value = Routes.Match
                },
                onTagClick = { tagKey ->
                    pendingDetailVideoUri.value = null
                    viewModel.openTagFilterFromDetail(tagKey)
                    selectedTab.value = MainTab.Filters
                    currentRoute.value = Routes.Library
                },
                onAuthorClick = { authorKey ->
                    pendingDetailVideoUri.value = null
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
                        iwaraWebViewUrl.value = "https://www.iwara.tv/search?query=${Uri.encode(query)}"
                        routeBeforeWebView.value = Routes.Match
                        currentRoute.value = Routes.IwaraWebView
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
}

private fun openVideoWithExternalPlayer(
    context: Context,
    video: VideoItem,
    componentName: String,
    settingsState: SettingsUiState
) {
    val component = ComponentName.unflattenFromString(componentName)
    if (component == null) {
        Toast.makeText(context, "保存的播放器配置无效", Toast.LENGTH_SHORT).show()
        return
    }

    val source = settingsState.librarySources.firstOrNull { it.id == video.sourceId }
    val playbackUri = if (source?.type == LibrarySourceType.WebDav) {
        val password = WebDavCredentialStore(context).loadPassword(source.id)
        val headers = WebDavClient().authHeaders(source, password)
        if (!source.webDavUsername.isNullOrBlank() && headers.isEmpty()) {
            Toast.makeText(context, "WebDAV 密码未保存，无法交给外部播放器", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            RemotePlaybackProxyService.createProxyUri(
                context = context,
                remoteUrl = video.uriString,
                headers = headers,
                mimeType = videoMimeType(video),
                readTimeoutSeconds = settingsState.remoteProxyReadTimeoutSeconds,
                idleTimeoutSeconds = settingsState.remoteProxyIdleTimeoutSeconds,
                allowInsecureTls = source.webDavAllowInsecureTls
            )
        }.getOrElse { error ->
            Toast.makeText(context, "启动远程播放代理失败：${error.message ?: error::class.java.simpleName}", Toast.LENGTH_SHORT).show()
            return
        }
    } else {
        Uri.parse(video.uriString)
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(playbackUri, videoMimeType(video))
        this.component = component
        if (source?.type != LibrarySourceType.WebDav) {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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

private fun defaultDatabaseFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "iwara_manager_$stamp.db"
}
