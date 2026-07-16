package com.ice.iwaramanager

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.ice.iwaramanager.data.model.AppRoute
import com.ice.iwaramanager.data.model.LibrarySourceType
import com.ice.iwaramanager.data.model.MainTab
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.VideoOpenMode
import com.ice.iwaramanager.data.model.VideoPlayerApp
import com.ice.iwaramanager.data.remote.WebDavClient
import com.ice.iwaramanager.domain.security.WebDavCredentialStore
import com.ice.iwaramanager.domain.scanner.VideoMediaSupport
import com.ice.iwaramanager.playback.RemotePlaybackProxyService
import com.ice.iwaramanager.ui.screen.DetailScreen
import com.ice.iwaramanager.ui.screen.IwaraLoginWebViewScreen
import com.ice.iwaramanager.ui.screen.LibraryScreen
import com.ice.iwaramanager.ui.screen.MatchScreen
import com.ice.iwaramanager.ui.screen.MatchTaskDetailScreen
import com.ice.iwaramanager.ui.screen.PlayerScreen
import com.ice.iwaramanager.ui.screen.SettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppRoot(
    viewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val videoPlayerApps = remember { loadVideoPlayerApps(context) }

    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val matchState by viewModel.matchState.collectAsStateWithLifecycle()
    val matchTaskDetailState by viewModel.matchTaskDetailState.collectAsStateWithLifecycle()
    val libraryItems = viewModel.libraryVideos.collectAsLazyPagingItems()
    val searchItems = viewModel.searchVideos.collectAsLazyPagingItems()

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Library) }
    var tabReselectTick by rememberSaveable { mutableLongStateOf(0L) }
    var tabBeforeDetail by rememberSaveable { mutableStateOf(MainTab.Library) }
    var pendingDetailVideoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightedVideoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var iwaraWebViewUrl by rememberSaveable { mutableStateOf("https://www.iwara.tv") }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderPicked(uri)
            selectedTab = MainTab.Library
            navController.returnToLibrary()
        }
    }
    val databaseExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.exportDatabase(uri)
    }
    val databaseImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importDatabase(uri)
    }

    fun selectMainTab(tab: MainTab) {
        if (selectedTab == tab) {
            tabReselectTick += 1L
        } else {
            selectedTab = tab
        }
    }

    fun openDetail(video: VideoItem) {
        pendingDetailVideoUri = video.uriString
        tabBeforeDetail = selectedTab
        viewModel.openVideoDetail(video)
        navController.navigate(AppRoute.Detail.path)
    }

    fun leaveDetailWithHighlight() {
        highlightedVideoUri = detailState.video?.uriString ?: pendingDetailVideoUri
        pendingDetailVideoUri = null
        selectedTab = tabBeforeDetail
        if (!navController.popBackStack()) navController.returnToLibrary()
    }

    fun playVideo(video: VideoItem) {
        when (settingsState.videoOpenMode) {
            VideoOpenMode.InApp -> {
                viewModel.openVideoPlayer(video)
                navController.navigate(AppRoute.Player.path)
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

    NavHost(
        navController = navController,
        startDestination = AppRoute.Library.path,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(AppRoute.Library.path) {
            LibraryScreen(
                libraryState = libraryState,
                searchState = searchState,
                libraryItems = libraryItems,
                searchItems = searchItems,
                selectedTab = selectedTab,
                tabReselectTick = tabReselectTick,
                showRematchButtonInList = settingsState.showRematchButtonInList,
                showGridCoverPlayButton = settingsState.showGridCoverPlayButton,
                highlightedVideoUri = highlightedVideoUri,
                onHighlightedVideoConsumed = { highlightedVideoUri = null },
                onSelectTab = ::selectMainTab,
                onSourceScopeChange = viewModel::selectSourceScope,
                onOpenDirectory = viewModel::openDirectory,
                onDirectoryUp = viewModel::navigateDirectoryUp,
                onOpenSettings = { navController.navigate(AppRoute.Settings.path) },
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
                    navController.navigate(AppRoute.MatchTaskDetail.path)
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
                    if (settingsState.openVideoDirectlyInPlayer) playVideo(video) else openDetail(video)
                },
                onMatchVideo = { video ->
                    viewModel.openMatchVideo(video)
                    navController.navigate(AppRoute.Match.path)
                },
                onPlayVideo = ::playVideo
            )
        }

        composable(AppRoute.Settings.path) {
            SettingsScreen(
                state = settingsState,
                videoPlayerApps = videoPlayerApps,
                onBack = { navController.popBackStack() },
                onPickFolder = { folderPicker.launch(null) },
                onRescan = viewModel::rescanLibrary,
                onClearDatabase = viewModel::clearDatabase,
                onClearFolder = viewModel::clearLibraryFolder,
                onCleanupMissingRecords = viewModel::cleanupOrphanedRecords,
                onExportDatabase = { databaseExporter.launch(defaultDatabaseFileName()) },
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
                onDeleteSource = viewModel::removeSourceConfiguration,
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
                onOpenIwaraLogin = { navController.navigate(AppRoute.IwaraLogin.path) },
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

        composable(AppRoute.IwaraLogin.path) {
            IwaraLoginWebViewScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    viewModel.markIwaraLoginCompleted()
                    navController.popBackStack()
                }
            )
        }

        composable(AppRoute.IwaraWebView.path) {
            IwaraLoginWebViewScreen(
                onBack = { navController.popBackStack() },
                onComplete = { navController.popBackStack() },
                initialUrl = iwaraWebViewUrl,
                defaultTitle = "Iwara 页面",
                showCompleteAction = false
            )
        }

        composable(AppRoute.Detail.path) {
            DetailScreen(
                state = detailState,
                onBack = ::leaveDetailWithHighlight,
                onPlay = ::playVideo,
                onMatch = { video ->
                    pendingDetailVideoUri = null
                    viewModel.openMatchVideo(video)
                    navController.navigate(AppRoute.Match.path)
                },
                onTagClick = { tagKey ->
                    pendingDetailVideoUri = null
                    viewModel.openTagFilterFromDetail(tagKey)
                    selectedTab = MainTab.Filters
                    navController.returnToLibrary()
                },
                onAuthorClick = { authorKey ->
                    pendingDetailVideoUri = null
                    viewModel.openAuthorFilterFromDetail(authorKey)
                    selectedTab = MainTab.Filters
                    navController.returnToLibrary()
                }
            )
        }

        composable(AppRoute.Match.path) {
            MatchScreen(
                state = matchState,
                onBack = { navController.popBackStack() },
                onQueryChange = viewModel::updateMatchQuery,
                onSearch = viewModel::searchMatchCandidates,
                onIdMatch = viewModel::idMatchCurrentVideo,
                onToggleSearchDiagnosticRaw = viewModel::toggleSearchDiagnosticRaw,
                onOpenWeb = {
                    val query = matchState.query.trim()
                    if (query.isNotBlank()) {
                        iwaraWebViewUrl = "https://www.iwara.tv/search?query=${Uri.encode(query)}"
                        navController.navigate(AppRoute.IwaraWebView.path)
                    }
                },
                onBind = { meta ->
                    viewModel.bindIwaraMeta(meta) {
                        matchState.video?.let(viewModel::openVideoDetail)
                        navController.navigate(AppRoute.Detail.path) {
                            popUpTo(AppRoute.Match.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onPlay = ::playVideo
            )
        }

        composable(AppRoute.MatchTaskDetail.path) {
            MatchTaskDetailScreen(
                state = matchTaskDetailState,
                onBack = { navController.popBackStack() },
                onOpenMatchPage = { task ->
                    viewModel.openMatchFromTask(task)
                    navController.navigate(AppRoute.Match.path)
                },
                onBindCandidate = viewModel::bindMatchCandidate,
                onSkipTask = viewModel::skipMatchTask,
                onPlayTask = { task ->
                    viewModel.openVideoFromTask(task, ::playVideo)
                }
            )
        }

        composable(AppRoute.Player.path) {
            PlayerScreen(
                state = playerState,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun NavHostController.returnToLibrary() {
    if (!popBackStack(AppRoute.Library.path, inclusive = false)) {
        navigate(AppRoute.Library.path) { launchSingleTop = true }
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
    val isWebDav = source?.type == LibrarySourceType.WebDav
    val playbackUri = if (isWebDav) {
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
            Toast.makeText(
                context,
                "启动远程播放代理失败：${error.message ?: error::class.java.simpleName}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
    } else {
        Uri.parse(video.uriString)
    }

    if (!isWebDav) {
        val readError = localVideoReadError(context, playbackUri)
        if (readError != null) {
            Toast.makeText(
                context,
                "视频文件已不在当前目录或目录授权已失效。请重新扫描/重新绑定目录；确认文件已删除后可在设置中清理缺失记录。$readError",
                Toast.LENGTH_LONG
            ).show()
            return
        }
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(playbackUri, videoMimeType(video))
        this.component = component
        if (!isWebDav) {
            clipData = ClipData.newRawUri(video.displayName, playbackUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "已选择的播放器不可用", Toast.LENGTH_SHORT).show()
    } catch (error: SecurityException) {
        Toast.makeText(
            context,
            "外部播放器无法读取该视频：${error.message ?: "URI 读取授权被拒绝"}",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun localVideoReadError(context: Context, uri: Uri): String? {
    return try {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return "（无法打开文件描述符）"
        descriptor.use { }
        null
    } catch (error: Exception) {
        "（${error.message ?: error::class.java.simpleName}）"
    }
}

private fun loadVideoPlayerApps(context: Context): List<VideoPlayerApp> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_VIEW).apply { setType("video/*") }
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0L)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, 0)
    }

    return resolveInfos.mapNotNull { info ->
        val activityInfo = info.activityInfo ?: return@mapNotNull null
        val label = info.loadLabel(packageManager)?.toString()
            ?: activityInfo.applicationInfo.loadLabel(packageManager).toString()
        VideoPlayerApp(
            label = label,
            packageName = activityInfo.packageName,
            activityName = activityInfo.name
        )
    }.distinctBy { it.componentName }
        .sortedBy { it.label.lowercase(Locale.ROOT) }
}

private fun videoMimeType(video: VideoItem): String {
    return VideoMediaSupport.mimeType(video.displayName)
}

private fun defaultDatabaseFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "iwara_manager_$stamp.db"
}
