package com.ice.iwaramanager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ice.iwaramanager.data.model.LibraryLayoutMode
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import com.ice.iwaramanager.data.model.FilterTab
import com.ice.iwaramanager.data.model.IwaraMatchMode
import com.ice.iwaramanager.data.model.IwaraMatchNetworkDefaults
import com.ice.iwaramanager.data.model.IwaraMatchNetworkOptions
import com.ice.iwaramanager.data.model.IwaraVideoMeta
import com.ice.iwaramanager.data.model.MatchTaskFilter
import com.ice.iwaramanager.data.model.MatchTaskStatus
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.VideoOpenMode
import com.ice.iwaramanager.data.repository.IwaraMetadataRepository
import com.ice.iwaramanager.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AppViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val app = application

    private val prefs = app.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val videoRepository = VideoRepository(app)
    private val iwaraRepository = IwaraMetadataRepository(app)

    private val initialFolderUriString = prefs.getString(KEY_FOLDER_URI, null)

    private val initialLayoutMode = runCatching {
        LibraryLayoutMode.valueOf(
            prefs.getString(KEY_LAYOUT_MODE, LibraryLayoutMode.Grid.name)
                ?: LibraryLayoutMode.Grid.name
        )
    }.getOrDefault(LibraryLayoutMode.Grid)

    private val initialGridColumns = prefs.getInt(KEY_GRID_COLUMNS, 2)
        .coerceIn(2, 6)

    private val initialShowRematchButtonInList = prefs.getBoolean(
        KEY_SHOW_REMATCH_BUTTON_IN_LIST,
        true
    )

    private val initialVideoOpenMode = runCatching {
        VideoOpenMode.valueOf(
            prefs.getString(KEY_VIDEO_OPEN_MODE, VideoOpenMode.InApp.name)
                ?: VideoOpenMode.InApp.name
        )
    }.getOrDefault(VideoOpenMode.InApp)

    private val initialVideoPlayerComponentName = prefs.getString(
        KEY_VIDEO_PLAYER_COMPONENT,
        null
    )

    private val initialVideoPlayerLabel = prefs.getString(
        KEY_VIDEO_PLAYER_LABEL,
        null
    )

    private val initialIwaraMatchMode = runCatching {
        IwaraMatchMode.valueOf(
            prefs.getString(KEY_IWARA_MATCH_MODE, IwaraMatchMode.IdThenTitle.name)
                ?: IwaraMatchMode.IdThenTitle.name
        )
    }.getOrDefault(IwaraMatchMode.IdThenTitle)

    private val initialMatchSearchTimeoutSeconds = prefs.getInt(
        KEY_MATCH_SEARCH_TIMEOUT_SECONDS,
        60
    ).coerceIn(10, 180)

    private val initialApiProbeTimeoutSeconds = prefs.getInt(
        KEY_API_PROBE_TIMEOUT_SECONDS,
        30
    ).coerceIn(5, 120)

    private val initialApiEndpointTemplatesText = prefs.getString(
        KEY_API_ENDPOINT_TEMPLATES,
        IwaraMatchNetworkDefaults.apiEndpointTemplates.joinToString("\n")
    ) ?: IwaraMatchNetworkDefaults.apiEndpointTemplates.joinToString("\n")

    private val initialApiEndpointTemplates = parseApiEndpointTemplates(initialApiEndpointTemplatesText)
        .ifEmpty { IwaraMatchNetworkDefaults.apiEndpointTemplates }

    private val initialAllowPageFallback = prefs.getBoolean(
        KEY_ALLOW_PAGE_FALLBACK,
        false
    )

    private val initialFetchSearchResultDetailsWithApi = prefs.getBoolean(
        KEY_FETCH_SEARCH_RESULT_DETAILS_WITH_API,
        true
    )

    private val initialMaxSearchApiDetails = prefs.getInt(
        KEY_MAX_SEARCH_API_DETAILS,
        10
    ).coerceIn(1, 30)

    private val initialBatchMatchThreads = prefs.getInt(
        KEY_BATCH_MATCH_THREADS,
        1
    ).coerceIn(1, 4)

    private val initialAutoMatchSingleHighConfidence = prefs.getBoolean(
        KEY_AUTO_MATCH_SINGLE_HIGH_CONFIDENCE,
        true
    )

    private val initialAutoMatchTitleSimilarityThreshold = prefs.getInt(
        KEY_AUTO_MATCH_TITLE_SIMILARITY_THRESHOLD,
        90
    ).coerceIn(1, 100)

    private val initialAutoMatchDurationToleranceSeconds = prefs.getInt(
        KEY_AUTO_MATCH_DURATION_TOLERANCE_SECONDS,
        2
    ).coerceIn(0, 60)

    private val initialAutoMatchSkipNoId = prefs.getBoolean(
        KEY_AUTO_MATCH_SKIP_NO_ID,
        false
    )

    private val _libraryState = MutableStateFlow(
        LibraryUiState(
            folderUriString = initialFolderUriString,
            layoutMode = initialLayoutMode,
            gridColumns = initialGridColumns
        )
    )
    val libraryState: StateFlow<LibraryUiState> = _libraryState

    private val _searchState = MutableStateFlow(
        SearchUiState(
            folderUriString = initialFolderUriString
        )
    )
    val searchState: StateFlow<SearchUiState> = _searchState

    private val _settingsState = MutableStateFlow(
        SettingsUiState(
            folderUriString = initialFolderUriString,
            layoutMode = initialLayoutMode,
            gridColumns = initialGridColumns,
            showRematchButtonInList = initialShowRematchButtonInList,
            videoOpenMode = if (initialVideoOpenMode == VideoOpenMode.ExternalPlayer &&
                initialVideoPlayerComponentName.isNullOrBlank()
            ) {
                VideoOpenMode.InApp
            } else {
                initialVideoOpenMode
            },
            selectedVideoPlayerComponentName = initialVideoPlayerComponentName,
            selectedVideoPlayerLabel = initialVideoPlayerLabel,
            iwaraMatchMode = initialIwaraMatchMode,
            matchSearchTimeoutSecondsText = initialMatchSearchTimeoutSeconds.toString(),
            matchSearchTimeoutSeconds = initialMatchSearchTimeoutSeconds,
            apiProbeTimeoutSecondsText = initialApiProbeTimeoutSeconds.toString(),
            apiProbeTimeoutSeconds = initialApiProbeTimeoutSeconds,
            apiEndpointTemplatesText = initialApiEndpointTemplatesText,
            apiEndpointTemplates = initialApiEndpointTemplates,
            allowPageFallback = initialAllowPageFallback,
            fetchSearchResultDetailsWithApi = initialFetchSearchResultDetailsWithApi,
            maxSearchApiDetailsText = initialMaxSearchApiDetails.toString(),
            maxSearchApiDetails = initialMaxSearchApiDetails,
            batchMatchThreadsText = initialBatchMatchThreads.toString(),
            batchMatchThreads = initialBatchMatchThreads,
            autoMatchSingleHighConfidence = initialAutoMatchSingleHighConfidence,
            autoMatchTitleSimilarityThresholdText = initialAutoMatchTitleSimilarityThreshold.toString(),
            autoMatchTitleSimilarityThreshold = initialAutoMatchTitleSimilarityThreshold,
            autoMatchDurationToleranceSecondsText = initialAutoMatchDurationToleranceSeconds.toString(),
            autoMatchDurationToleranceSeconds = initialAutoMatchDurationToleranceSeconds,
            autoMatchSkipNoId = initialAutoMatchSkipNoId
        )
    )
    val settingsState: StateFlow<SettingsUiState> = _settingsState

    private val _detailState = MutableStateFlow(
        VideoDetailUiState()
    )
    val detailState: StateFlow<VideoDetailUiState> = _detailState

    private val _playerState = MutableStateFlow(
        PlayerUiState()
    )
    val playerState: StateFlow<PlayerUiState> = _playerState

    private val _matchState = MutableStateFlow(MatchUiState())
    val matchState: StateFlow<MatchUiState> = _matchState

    private val _matchTaskDetailState = MutableStateFlow(MatchTaskDetailUiState())
    val matchTaskDetailState: StateFlow<MatchTaskDetailUiState> = _matchTaskDetailState

    private var observeLibraryJob: Job? = null
    private var observeSearchJob: Job? = null
    private var observeFilteredJob: Job? = null
    private var observeCountsJob: Job? = null
    private var observeTasksJob: Job? = null
    private var observeTaskCountsJob: Job? = null
    private var observeTaskDetailJob: Job? = null
    private var observeTaskCandidatesJob: Job? = null
    private var scanJob: Job? = null

    init {
        recoverInterruptedMatchTasks()
        if (!initialFolderUriString.isNullOrBlank()) {
            observeLibrary()
            observeFiltersAndTasks()
        }
    }

    private fun recoverInterruptedMatchTasks() {
        viewModelScope.launch {
            runCatching {
                videoRepository.markInterruptedMatchTasksAsFailed()
            }
        }
    }

    fun onFolderPicked(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        val uriString = uri.toString()

        prefs.edit()
            .putString(KEY_FOLDER_URI, uriString)
            .apply()

        _libraryState.update {
            it.copy(
                folderUriString = uriString,
                error = null
            )
        }

        _searchState.update {
            it.copy(
                folderUriString = uriString,
                error = null
            )
        }

        _settingsState.update {
            it.copy(
                folderUriString = uriString,
                error = null
            )
        }

        observeLibrary()
        observeFiltersAndTasks()
        observeSearchIfNeeded()
        scanFolder(uri)
    }

    fun rescanLibrary() {
        val folder = _libraryState.value.folderUriString ?: return
        scanFolder(Uri.parse(folder))
    }

    fun updateSearchQuery(query: String) {
        _searchState.update {
            it.copy(
                query = query
            )
        }

        observeSearchIfNeeded()
    }

    fun clearSearchQuery() {
        observeSearchJob?.cancel()

        _searchState.update {
            it.copy(
                query = "",
                results = emptyList(),
                error = null
            )
        }
    }

    fun openVideoDetail(video: VideoItem) {
        _detailState.update {
            it.copy(video = video)
        }
    }

    fun openVideoPlayer(video: VideoItem) {
        _playerState.update {
            it.copy(video = video)
        }
    }

    fun openMatchVideo(video: VideoItem, sourceTaskId: Long? = null) {
        val id = video.sourceVideoId?.trim().orEmpty()
        val query = id.ifBlank { buildMatchQuery(video) }
        _matchState.update {
            MatchUiState(
                video = video,
                sourceTaskId = sourceTaskId,
                query = query
            )
        }
    }

    fun setLibraryLayoutMode(mode: LibraryLayoutMode) {
        prefs.edit()
            .putString(KEY_LAYOUT_MODE, mode.name)
            .apply()

        _libraryState.update {
            it.copy(layoutMode = mode)
        }

        _settingsState.update {
            it.copy(layoutMode = mode)
        }
    }

    fun setGridColumns(columns: Int) {
        val fixed = columns.coerceIn(2, 6)

        prefs.edit()
            .putInt(KEY_GRID_COLUMNS, fixed)
            .apply()

        _libraryState.update {
            it.copy(gridColumns = fixed)
        }

        _settingsState.update {
            it.copy(gridColumns = fixed)
        }
    }

    fun setShowRematchButtonInList(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_REMATCH_BUTTON_IN_LIST, enabled)
            .apply()

        _settingsState.update {
            it.copy(showRematchButtonInList = enabled)
        }
    }

    fun setVideoOpenMode(mode: VideoOpenMode) {
        prefs.edit()
            .putString(KEY_VIDEO_OPEN_MODE, mode.name)
            .apply()

        _settingsState.update {
            it.copy(videoOpenMode = mode)
        }
    }

    fun setExternalVideoPlayer(
        componentName: String,
        label: String
    ) {
        prefs.edit()
            .putString(KEY_VIDEO_OPEN_MODE, VideoOpenMode.ExternalPlayer.name)
            .putString(KEY_VIDEO_PLAYER_COMPONENT, componentName)
            .putString(KEY_VIDEO_PLAYER_LABEL, label)
            .apply()

        _settingsState.update {
            it.copy(
                videoOpenMode = VideoOpenMode.ExternalPlayer,
                selectedVideoPlayerComponentName = componentName,
                selectedVideoPlayerLabel = label
            )
        }
    }

    fun resetVideoOpenMode() {
        prefs.edit()
            .remove(KEY_VIDEO_OPEN_MODE)
            .remove(KEY_VIDEO_PLAYER_COMPONENT)
            .remove(KEY_VIDEO_PLAYER_LABEL)
            .apply()

        _settingsState.update {
            it.copy(
                videoOpenMode = VideoOpenMode.InApp,
                selectedVideoPlayerComponentName = null,
                selectedVideoPlayerLabel = null
            )
        }
    }

    fun setIwaraMatchMode(mode: IwaraMatchMode) {
        prefs.edit()
            .putString(KEY_IWARA_MATCH_MODE, mode.name)
            .apply()
        _settingsState.update { it.copy(iwaraMatchMode = mode) }
    }

    fun setMatchSearchTimeoutSeconds(raw: String) {
        val value = raw.toIntOrNull()?.coerceIn(10, 180)
        _settingsState.update {
            if (value == null) {
                it.copy(matchSearchTimeoutSecondsText = raw)
            } else {
                prefs.edit().putInt(KEY_MATCH_SEARCH_TIMEOUT_SECONDS, value).apply()
                it.copy(
                    matchSearchTimeoutSecondsText = value.toString(),
                    matchSearchTimeoutSeconds = value
                )
            }
        }
    }

    fun setApiProbeTimeoutSeconds(raw: String) {
        val value = raw.toIntOrNull()?.coerceIn(5, 120)
        _settingsState.update {
            if (value == null) {
                it.copy(apiProbeTimeoutSecondsText = raw)
            } else {
                prefs.edit().putInt(KEY_API_PROBE_TIMEOUT_SECONDS, value).apply()
                it.copy(
                    apiProbeTimeoutSecondsText = value.toString(),
                    apiProbeTimeoutSeconds = value
                )
            }
        }
    }

    fun setApiEndpointTemplates(raw: String) {
        val templates = parseApiEndpointTemplates(raw)
        _settingsState.update {
            if (templates.isEmpty()) {
                it.copy(apiEndpointTemplatesText = raw)
            } else {
                prefs.edit().putString(KEY_API_ENDPOINT_TEMPLATES, raw).apply()
                it.copy(
                    apiEndpointTemplatesText = raw,
                    apiEndpointTemplates = templates
                )
            }
        }
    }

    fun resetApiEndpointTemplates() {
        val text = IwaraMatchNetworkDefaults.apiEndpointTemplates.joinToString("\n")
        prefs.edit()
            .putString(KEY_API_ENDPOINT_TEMPLATES, text)
            .apply()
        _settingsState.update {
            it.copy(
                apiEndpointTemplatesText = text,
                apiEndpointTemplates = IwaraMatchNetworkDefaults.apiEndpointTemplates
            )
        }
    }

    fun setAllowPageFallback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_PAGE_FALLBACK, enabled).apply()
        _settingsState.update { it.copy(allowPageFallback = enabled) }
    }

    fun setFetchSearchResultDetailsWithApi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FETCH_SEARCH_RESULT_DETAILS_WITH_API, enabled).apply()
        _settingsState.update { it.copy(fetchSearchResultDetailsWithApi = enabled) }
    }

    fun setMaxSearchApiDetails(raw: String) {
        val value = raw.toIntOrNull()?.coerceIn(1, 30)
        _settingsState.update {
            if (value == null) {
                it.copy(maxSearchApiDetailsText = raw)
            } else {
                prefs.edit().putInt(KEY_MAX_SEARCH_API_DETAILS, value).apply()
                it.copy(
                    maxSearchApiDetailsText = value.toString(),
                    maxSearchApiDetails = value
                )
            }
        }
    }

    fun setBatchMatchThreads(raw: String) {
        val value = raw.toIntOrNull()?.coerceIn(1, 4)
        _settingsState.update {
            if (value == null) {
                it.copy(batchMatchThreadsText = raw)
            } else {
                prefs.edit().putInt(KEY_BATCH_MATCH_THREADS, value).apply()
                it.copy(
                    batchMatchThreadsText = value.toString(),
                    batchMatchThreads = value
                )
            }
        }
    }

    fun setAutoMatchSingleHighConfidence(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_MATCH_SINGLE_HIGH_CONFIDENCE, enabled).apply()
        _settingsState.update { it.copy(autoMatchSingleHighConfidence = enabled) }
    }

    fun setAutoMatchTitleSimilarityThreshold(raw: String) {
        val value = raw.toIntOrNull()?.coerceIn(1, 100)
        _settingsState.update {
            if (value == null) {
                it.copy(autoMatchTitleSimilarityThresholdText = raw)
            } else {
                prefs.edit().putInt(KEY_AUTO_MATCH_TITLE_SIMILARITY_THRESHOLD, value).apply()
                it.copy(
                    autoMatchTitleSimilarityThresholdText = value.toString(),
                    autoMatchTitleSimilarityThreshold = value
                )
            }
        }
    }

    fun setAutoMatchDurationToleranceSeconds(raw: String) {
        val value = raw.toIntOrNull()?.coerceIn(0, 60)
        _settingsState.update {
            if (value == null) {
                it.copy(autoMatchDurationToleranceSecondsText = raw)
            } else {
                prefs.edit().putInt(KEY_AUTO_MATCH_DURATION_TOLERANCE_SECONDS, value).apply()
                it.copy(
                    autoMatchDurationToleranceSecondsText = value.toString(),
                    autoMatchDurationToleranceSeconds = value
                )
            }
        }
    }

    fun setAutoMatchSkipNoId(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_MATCH_SKIP_NO_ID, enabled)
            .apply()
        _settingsState.update { it.copy(autoMatchSkipNoId = enabled) }
    }

    fun setFilterTab(tab: FilterTab) {
        _libraryState.update { it.copy(filterTab = tab) }
    }

    fun toggleTagFilter(key: String) {
        _libraryState.update {
            val next = it.selectedTagKeys.toggle(key)
            it.copy(selectedTagKeys = next)
        }
        observeFilteredVideos()
    }

    fun toggleAuthorFilter(key: String) {
        _libraryState.update {
            val next = it.selectedAuthorKeys.toggle(key)
            it.copy(selectedAuthorKeys = next)
        }
        observeFilteredVideos()
    }

    fun toggleQualityFilter(key: String) {
        _libraryState.update {
            val next = it.selectedQualities.toggle(key)
            it.copy(selectedQualities = next)
        }
        observeFilteredVideos()
    }

    fun clearFilters() {
        _libraryState.update {
            it.copy(
                selectedTagKeys = emptySet(),
                selectedAuthorKeys = emptySet(),
                selectedQualities = emptySet()
            )
        }
        observeFilteredVideos()
    }

    fun openTagFilterFromDetail(tagKey: String) {
        _libraryState.update {
            it.copy(
                filterTab = FilterTab.Tags,
                selectedTagKeys = setOf(tagKey),
                selectedAuthorKeys = emptySet(),
                selectedQualities = emptySet()
            )
        }
        observeFilteredVideos()
    }

    fun openAuthorFilterFromDetail(authorKey: String) {
        _libraryState.update {
            it.copy(
                filterTab = FilterTab.Authors,
                selectedTagKeys = emptySet(),
                selectedAuthorKeys = setOf(authorKey),
                selectedQualities = emptySet()
            )
        }
        observeFilteredVideos()
    }

    fun setMatchTaskFilter(filter: MatchTaskFilter) {
        _libraryState.update { it.copy(taskFilter = filter) }
        observeMatchTasks()
    }

    fun updateMatchQuery(query: String) {
        _matchState.update {
            it.copy(query = query)
        }
    }

    fun toggleSearchDiagnosticRaw() {
        _matchState.update {
            it.copy(showSearchDiagnosticRaw = !it.showSearchDiagnosticRaw)
        }
    }

    fun searchMatchCandidates() {
        val state = _matchState.value
        val query = state.query.trim()
        if (query.isBlank()) {
            _matchState.update { it.copy(error = "搜索词不能为空") }
            return
        }
        viewModelScope.launch {
            _matchState.update {
                it.copy(
                    isSearching = true,
                    isBinding = false,
                    error = null,
                    candidates = emptyList(),
                    searchDiagnosticSummary = null,
                    searchDiagnosticRaw = null
                )
            }
            val result = iwaraRepository.searchTitle(
                title = query,
                timeoutMillis = _settingsState.value.matchSearchTimeoutSeconds * 1000L,
                options = currentMatchOptions()
            )
            _matchState.update {
                it.copy(
                    isSearching = false,
                    candidates = result.videos,
                    error = result.failureReason,
                    searchDiagnosticSummary = result.diagnosticSummary,
                    searchDiagnosticRaw = result.diagnosticRaw
                )
            }
        }
    }

    fun idMatchCurrentVideo() {
        val state = _matchState.value
        val video = state.video ?: return
        val id = normalizeIwaraId(
            state.query.trim().ifBlank { video.sourceVideoId.orEmpty() }
        )
        if (id.isNullOrBlank()) {
            _matchState.update { it.copy(error = "请输入 Iwara ID，或使用带 ID 的文件名打开匹配页") }
            return
        }
        startIdMatch(video, id)
    }

    private fun startIdMatch(
        video: VideoItem,
        id: String
    ) {
        viewModelScope.launch {
            _matchState.update {
                it.copy(
                    video = video,
                    query = id,
                    isSearching = true,
                    isBinding = false,
                    error = null,
                    candidates = emptyList(),
                    searchDiagnosticSummary = null,
                    searchDiagnosticRaw = null
                )
            }
            val result = iwaraRepository.fetchById(
                id = id,
                timeoutMillis = _settingsState.value.matchSearchTimeoutSeconds * 1000L,
                options = currentMatchOptions()
            )
            _matchState.update {
                it.copy(
                    isSearching = false,
                    candidates = result.videos,
                    error = result.failureReason,
                    searchDiagnosticSummary = result.diagnosticSummary,
                    searchDiagnosticRaw = result.diagnosticRaw
                )
            }
        }
    }

    fun bindIwaraMeta(
        meta: IwaraVideoMeta,
        onBound: () -> Unit = {}
    ) {
        val video = _matchState.value.video ?: return
        val taskId = _matchState.value.sourceTaskId
        viewModelScope.launch {
            _matchState.update { it.copy(isBinding = true, error = null) }
            runCatching {
                val enriched = enrichIwaraMeta(meta)
                videoRepository.bindIwaraMeta(video.uriString, enriched, "manual_matched")
                upsertSuccessfulMatchTask(
                    video = video,
                    meta = enriched,
                    query = _matchState.value.query.trim().ifBlank { enriched.id },
                    sourceTaskId = taskId
                )
                videoRepository.findVideoByUri(video.uriString)?.let { openVideoDetail(it) }
            }.onSuccess {
                _matchState.update { it.copy(isBinding = false) }
                onBound()
            }.onFailure { error ->
                _matchState.update {
                    it.copy(
                        isBinding = false,
                        error = "绑定失败：${error.message ?: error::class.java.simpleName}"
                    )
                }
            }
        }
    }

    private suspend fun upsertSuccessfulMatchTask(
        video: VideoItem,
        meta: IwaraVideoMeta,
        query: String,
        sourceTaskId: Long? = null
    ) {
        val root = _libraryState.value.folderUriString ?: return
        val existingTask = sourceTaskId
            ?.let { videoRepository.getMatchTask(it) }
            ?: videoRepository.getTasksByVideoUri(video.uriString).firstOrNull()
        val task = existingTask ?: videoRepository.getMatchTask(
            videoRepository.createMatchTask(
                video = video,
                libraryRootUriString = root,
                query = query
            )
        ) ?: return

        videoRepository.replaceCandidatesForTask(task.id, listOf(meta), meta.id)
        videoRepository.updateMatchTask(
            task.copy(
                query = query,
                status = MatchTaskStatus.AutoMatched,
                matchedIwaraId = meta.id,
                candidateCount = 1,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun openMatchTask(task: MatchTaskEntity) {
        observeTaskDetailJob?.cancel()
        observeTaskCandidatesJob?.cancel()
        _matchTaskDetailState.update { MatchTaskDetailUiState(task = task) }

        observeTaskDetailJob = viewModelScope.launch {
            videoRepository.observeMatchTask(task.id).collectLatest { observed ->
                _matchTaskDetailState.update { it.copy(task = observed) }
            }
        }
        observeTaskCandidatesJob = viewModelScope.launch {
            videoRepository.observeCandidatesForTask(task.id).collectLatest { candidates ->
                _matchTaskDetailState.update { it.copy(candidates = candidates) }
            }
        }
    }

    fun openMatchFromTask(task: MatchTaskEntity) {
        viewModelScope.launch {
            val video = videoRepository.findVideoByUri(task.videoUriString) ?: return@launch
            openMatchVideo(video, task.id)
        }
    }

    fun openVideoFromTask(
        task: MatchTaskEntity,
        onFound: (VideoItem) -> Unit
    ) {
        viewModelScope.launch {
            videoRepository.findVideoByUri(task.videoUriString)?.let(onFound)
        }
    }

    fun bindMatchCandidate(candidate: com.ice.iwaramanager.data.local.entity.MatchCandidateEntity) {
        val task = _matchTaskDetailState.value.task ?: return
        viewModelScope.launch {
            _matchTaskDetailState.update { it.copy(isBinding = true, error = null) }
            runCatching {
                val meta = IwaraVideoMeta(
                    id = candidate.iwaraId,
                    title = candidate.title,
                    authorName = candidate.authorName,
                    authorUsername = candidate.authorUsername,
                    thumbnailUrl = candidate.thumbnailUrl,
                    rating = candidate.rating,
                    createdAt = candidate.createdAtText,
                    durationSeconds = candidate.durationSeconds,
                    viewCount = candidate.viewCount,
                    likeCount = candidate.likeCount,
                    rawJson = candidate.rawJson
                )
                val enriched = enrichIwaraMeta(meta)
                videoRepository.bindIwaraMeta(task.videoUriString, enriched, "manual_matched")
                videoRepository.replaceCandidatesForTask(task.id, listOf(enriched), enriched.id)
                videoRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.AutoMatched,
                        matchedIwaraId = enriched.id,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                videoRepository.getNextNeedReviewAfter(task)
            }.onSuccess { next ->
                if (next != null) {
                    openMatchTask(next)
                }
                _matchTaskDetailState.update { it.copy(isBinding = false) }
            }.onFailure { error ->
                _matchTaskDetailState.update {
                    it.copy(
                        isBinding = false,
                        error = "绑定失败：${error.message ?: error::class.java.simpleName}"
                    )
                }
            }
        }
    }

    fun skipMatchTask(task: MatchTaskEntity) {
        viewModelScope.launch {
            videoRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Skipped,
                    errorMessage = "用户手动标记跳过",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun skipUnqueuedVideo(video: VideoItem) {
        val root = _libraryState.value.folderUriString ?: return
        viewModelScope.launch {
            val taskId = videoRepository.createMatchTask(
                video = video,
                libraryRootUriString = root,
                query = buildTaskQuery(video)
            )
            val task = videoRepository.getMatchTask(taskId) ?: return@launch
            videoRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Skipped,
                    errorMessage = SKIPPED_FROM_UNQUEUED_MESSAGE,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun cancelSkippedMatchTask(task: MatchTaskEntity) {
        viewModelScope.launch {
            if (task.errorMessage == SKIPPED_FROM_UNQUEUED_MESSAGE) {
                videoRepository.deleteMatchTask(task.id)
            } else {
                videoRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.Failed,
                        errorMessage = "已取消跳过",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun retryMatchTask(task: MatchTaskEntity) {
        viewModelScope.launch {
            val video = videoRepository.findVideoByUri(task.videoUriString) ?: return@launch
            processOneBatchVideo(video, task)
        }
    }

    fun retryFailedMatchTasks() {
        viewModelScope.launch {
            val root = _libraryState.value.folderUriString ?: return@launch
            val tasks = _libraryState.value.matchTasks.filter { it.status == MatchTaskStatus.Failed }
                .ifEmpty {
                    videoRepository.observeMatchTasksByStatuses(root, listOf(MatchTaskStatus.Failed))
                    emptyList()
                }
            tasks.forEach { retryMatchTask(it) }
        }
    }

    fun startBatchMatchUnmatched() {
        if (_libraryState.value.isBatchMatching) return
        viewModelScope.launch {
            val root = _libraryState.value.folderUriString ?: return@launch
            _libraryState.update { it.copy(isBatchMatching = true, error = null) }
            try {
                val videos = videoRepository.getUnqueuedUnmatchedVideos(root)
                val semaphore = Semaphore(_settingsState.value.batchMatchThreads)
                coroutineScope {
                    videos.map { video ->
                        async {
                            semaphore.withPermit {
                                val taskId = videoRepository.createMatchTask(
                                    video = video,
                                    libraryRootUriString = root,
                                    query = buildTaskQuery(video)
                                )
                                val task = videoRepository.getMatchTask(taskId) ?: return@withPermit
                                processOneBatchVideo(video, task)
                            }
                        }
                    }.awaitAll()
                }
                setMatchTaskFilter(MatchTaskFilter.All)
            } finally {
                _libraryState.update { it.copy(isBatchMatching = false) }
            }
        }
    }

    fun startBatchRematchMatched() {
        if (_libraryState.value.isBatchMatching) return
        viewModelScope.launch {
            val root = _libraryState.value.folderUriString ?: return@launch
            val videos = videoRepository.getRematchableVideos(root)
            if (videos.isEmpty()) {
                _libraryState.update {
                    it.copy(error = "没有可重新匹配的视频：需要已有 Iwara ID 或文件名能解析出 Iwara ID")
                }
                return@launch
            }

            _libraryState.update { it.copy(isBatchMatching = true, error = null) }
            try {
                val semaphore = Semaphore(_settingsState.value.batchMatchThreads)
                coroutineScope {
                    videos.map { video ->
                        async {
                            semaphore.withPermit {
                                val id = video.matchedIwaraId?.trim().orEmpty()
                                val query = id.ifBlank { video.sourceVideoId?.trim().orEmpty() }
                                val task = videoRepository.getTasksByVideoUri(video.uriString).firstOrNull()
                                    ?: videoRepository.getMatchTask(
                                        videoRepository.createMatchTask(
                                            video = video,
                                            libraryRootUriString = root,
                                            query = query.ifBlank { buildMatchQuery(video) }
                                        )
                                    )
                                    ?: return@withPermit
                                processOneMatchedRefresh(video, task, query)
                            }
                        }
                    }.awaitAll()
                }
                setMatchTaskFilter(MatchTaskFilter.All)
            } finally {
                _libraryState.update { it.copy(isBatchMatching = false) }
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            videoRepository.clearAll()

            _libraryState.update {
                it.copy(videos = emptyList())
            }

            _searchState.update {
                it.copy(results = emptyList())
            }

            _settingsState.update {
                it.copy(videoCount = 0)
            }

            _detailState.update {
                it.copy(video = null)
            }

            _playerState.update {
                it.copy(video = null)
            }
        }
    }

    fun exportDatabase(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                videoRepository.exportDatabaseTo(uri)
            }.onSuccess {
                _settingsState.update {
                    it.copy(message = "数据库导出成功", error = null)
                }
            }.onFailure { error ->
                _settingsState.update {
                    it.copy(
                        message = null,
                        error = "数据库导出失败：${error.message ?: error::class.java.simpleName}"
                    )
                }
            }
        }
    }

    fun importDatabase(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                videoRepository.importDatabaseFrom(uri)
            }.onSuccess {
                if (!_libraryState.value.folderUriString.isNullOrBlank()) {
                    observeLibrary()
                    observeFiltersAndTasks()
                    observeSearchIfNeeded()
                }
                _settingsState.update {
                    it.copy(message = "数据库导入成功", error = null)
                }
            }.onFailure { error ->
                _settingsState.update {
                    it.copy(
                        message = null,
                        error = "数据库导入失败：${error.message ?: error::class.java.simpleName}"
                    )
                }
            }
        }
    }

    fun clearLibraryFolder() {
        val oldFolder = _libraryState.value.folderUriString

        prefs.edit()
            .remove(KEY_FOLDER_URI)
            .apply()

        observeLibraryJob?.cancel()
        observeSearchJob?.cancel()
        observeFilteredJob?.cancel()
        observeCountsJob?.cancel()
        observeTasksJob?.cancel()
        observeTaskCountsJob?.cancel()
        scanJob?.cancel()

        _libraryState.update {
            it.copy(
                folderUriString = null,
                videos = emptyList(),
                filteredVideos = emptyList(),
                matchTasks = emptyList(),
                unqueuedVideos = emptyList(),
                matchTaskFilterCounts = emptyMap(),
                isScanning = false,
                scanDone = 0,
                scanTotal = 0,
                scanCurrentName = null,
                error = null
            )
        }

        _searchState.update {
            it.copy(
                folderUriString = null,
                query = "",
                results = emptyList(),
                error = null
            )
        }

        _settingsState.update {
            it.copy(
                folderUriString = null,
                isScanning = false,
                videoCount = 0,
                error = null
            )
        }

        _detailState.update {
            it.copy(video = null)
        }

        _playerState.update {
            it.copy(video = null)
        }

        if (!oldFolder.isNullOrBlank()) {
            viewModelScope.launch {
                videoRepository.clearRoot(oldFolder)
            }
        }
    }

    private fun observeLibrary() {
        val root = _libraryState.value.folderUriString ?: return

        observeLibraryJob?.cancel()
        observeLibraryJob = viewModelScope.launch {
            videoRepository.observeVideos(root)
                .collect { videos ->
                    _libraryState.update {
                        it.copy(videos = videos)
                    }

                    _settingsState.update {
                        it.copy(videoCount = videos.size)
                    }
                }
        }
    }

    private fun observeSearchIfNeeded() {
        val root = _searchState.value.folderUriString ?: return
        val query = _searchState.value.query.trim()

        observeSearchJob?.cancel()

        if (query.isBlank()) {
            _searchState.update {
                it.copy(
                    results = emptyList(),
                    error = null
                )
            }
            return
        }

        observeSearchJob = viewModelScope.launch {
            videoRepository.observeVideosBySearch(
                libraryRootUriString = root,
                query = query
            ).collect { videos ->
                _searchState.update {
                    it.copy(
                        results = videos,
                        error = null
                    )
                }
            }
        }
    }

    private fun scanFolder(uri: Uri) {
        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            _libraryState.update {
                it.copy(
                    isScanning = true,
                    scanDone = 0,
                    scanTotal = 0,
                    scanCurrentName = null,
                    error = null
                )
            }

            _settingsState.update {
                it.copy(
                    isScanning = true,
                    error = null
                )
            }

            try {
                videoRepository.scanAndSync(
                    treeUri = uri,
                    onProgress = { progress ->
                        _libraryState.update {
                            it.copy(
                                scanDone = progress.done,
                                scanTotal = progress.total,
                                scanCurrentName = progress.currentName
                            )
                        }
                    }
                )

                _libraryState.update {
                    it.copy(
                        isScanning = false,
                        scanCurrentName = null,
                        error = null
                    )
                }

                _settingsState.update {
                    it.copy(
                        isScanning = false,
                        error = null
                    )
                }

                observeLibrary()
                observeSearchIfNeeded()
            } catch (e: Exception) {
                val message = e.message ?: "扫描失败"

                _libraryState.update {
                    it.copy(
                        isScanning = false,
                        scanCurrentName = null,
                        error = message
                    )
                }

                _settingsState.update {
                    it.copy(
                        isScanning = false,
                        error = message
                    )
                }
            }
        }
    }

    private fun observeFiltersAndTasks() {
        observeFilteredVideos()
        observeFilterCounts()
        observeMatchTasks()
        observeMatchTaskCounts()
    }

    private fun observeFilteredVideos() {
        val root = _libraryState.value.folderUriString ?: return
        val state = _libraryState.value
        observeFilteredJob?.cancel()
        observeFilteredJob = viewModelScope.launch {
            videoRepository.observeVideosByFilters(
                libraryRootUriString = root,
                tagKeys = state.selectedTagKeys,
                authorKeys = state.selectedAuthorKeys,
                qualities = state.selectedQualities
            ).collectLatest { videos ->
                _libraryState.update { it.copy(filteredVideos = videos) }
            }
        }
    }

    private fun observeFilterCounts() {
        val root = _libraryState.value.folderUriString ?: return
        observeCountsJob?.cancel()
        observeCountsJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                videoRepository.observeTagCounts(root),
                videoRepository.observeAuthorCounts(root),
                videoRepository.observeQualityCounts(root)
            ) { tags, authors, qualities ->
                Triple(tags, authors, qualities)
            }.collectLatest { (tags, authors, qualities) ->
                _libraryState.update {
                    it.copy(
                        tagCounts = tags,
                        authorCounts = authors,
                        qualityCounts = qualities
                    )
                }
            }
        }
    }

    private fun observeMatchTasks() {
        val root = _libraryState.value.folderUriString ?: return
        observeTasksJob?.cancel()
        observeTasksJob = viewModelScope.launch {
            if (_libraryState.value.taskFilter == MatchTaskFilter.Unqueued) {
                videoRepository.observeUnqueuedUnmatchedVideos(root).collectLatest { videos ->
                    _libraryState.update {
                        it.copy(
                            unqueuedVideos = videos,
                            matchTasks = emptyList()
                        )
                    }
                }
            } else {
                videoRepository.observeMatchTasksByStatuses(
                    root,
                    statusesForFilter(_libraryState.value.taskFilter)
                ).collectLatest { tasks ->
                    _libraryState.update {
                        it.copy(
                            matchTasks = tasks,
                            unqueuedVideos = emptyList()
                        )
                    }
                }
            }
        }
    }

    private fun observeMatchTaskCounts() {
        val root = _libraryState.value.folderUriString ?: return
        observeTaskCountsJob?.cancel()
        observeTaskCountsJob = viewModelScope.launch {
            videoRepository.observeMatchTaskFilterCounts(root).collectLatest { counts ->
                _libraryState.update { it.copy(matchTaskFilterCounts = counts) }
            }
        }
    }

    private suspend fun processOneBatchVideo(
        video: VideoItem,
        task: com.ice.iwaramanager.data.local.entity.MatchTaskEntity
    ) {
        videoRepository.updateMatchTask(
            task.copy(
                status = MatchTaskStatus.Running,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        )

        if (_settingsState.value.autoMatchSkipNoId && video.sourceVideoId.isNullOrBlank()) {
            videoRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Skipped,
                    candidateCount = 0,
                    errorMessage = "已按设置跳过：本地文件名没有解析到 Iwara ID",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        try {
            val result = searchByMode(video, task.query, idOnly = false)
            val candidates = result.videos

            if (candidates.isEmpty()) {
                videoRepository.replaceCandidatesForTask(task.id, emptyList())
                videoRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.Failed,
                        candidateCount = 0,
                        errorMessage = result.failureReason ?: result.diagnosticSummary,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return
            }

            val auto = chooseAutoCandidate(task.query, video, candidates)
            videoRepository.replaceCandidatesForTask(task.id, candidates, auto?.id)

        if (auto != null) {
                val enrichedAuto = enrichIwaraMeta(auto)
                videoRepository.bindIwaraMeta(video.uriString, enrichedAuto, "auto_matched")
                videoRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.AutoMatched,
                        matchedIwaraId = enrichedAuto.id,
                        candidateCount = candidates.size,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                videoRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.NeedReview,
                        candidateCount = candidates.size,
                        errorMessage = result.failureReason,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            videoRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Failed,
                    errorMessage = "匹配异常：${error.message ?: error::class.java.simpleName}",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun processOneMatchedRefresh(
        video: VideoItem,
        task: com.ice.iwaramanager.data.local.entity.MatchTaskEntity,
        id: String
    ) {
        val targetId = id.trim()
        videoRepository.updateMatchTask(
            task.copy(
                query = targetId.ifBlank { task.query },
                status = MatchTaskStatus.Running,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (targetId.isBlank()) {
            videoRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Failed,
                    candidateCount = 0,
                    errorMessage = "已匹配记录缺少 Iwara ID，无法重新匹配",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        try {
            val result = iwaraRepository.fetchById(
                id = targetId,
                timeoutMillis = _settingsState.value.matchSearchTimeoutSeconds * 1000L,
                options = currentMatchOptions()
            )
            val meta = result.videos.firstOrNull { it.id == targetId } ?: result.videos.firstOrNull()
            if (meta == null) {
                videoRepository.replaceCandidatesForTask(task.id, emptyList())
                videoRepository.updateMatchTask(
                    task.copy(
                        query = targetId,
                        status = MatchTaskStatus.Failed,
                        candidateCount = 0,
                        errorMessage = result.failureReason ?: result.diagnosticSummary,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return
            }

            val enriched = enrichIwaraMeta(meta)
            videoRepository.bindIwaraMeta(video.uriString, enriched, "auto_matched")
            videoRepository.replaceCandidatesForTask(task.id, listOf(enriched), enriched.id)
            videoRepository.updateMatchTask(
                task.copy(
                    query = targetId,
                    status = MatchTaskStatus.AutoMatched,
                    matchedIwaraId = enriched.id,
                    candidateCount = 1,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            videoRepository.updateMatchTask(
                task.copy(
                    query = targetId,
                    status = MatchTaskStatus.Failed,
                    errorMessage = "重新匹配异常：${error.message ?: error::class.java.simpleName}",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun searchByMode(
        video: VideoItem?,
        query: String,
        idOnly: Boolean
    ): com.ice.iwaramanager.data.model.IwaraSearchMetaResult {
        val timeoutMillis = _settingsState.value.matchSearchTimeoutSeconds * 1000L
        val options = currentMatchOptions()
        val mode = if (idOnly) IwaraMatchMode.IdOnly else _settingsState.value.iwaraMatchMode
        val id = video?.sourceVideoId?.trim().orEmpty()

        return when (mode) {
            IwaraMatchMode.IdOnly -> {
                if (id.isBlank()) {
                    com.ice.iwaramanager.data.model.IwaraSearchMetaResult(
                        videos = emptyList(),
                        diagnosticSummary = "文件名中没有解析到 Iwara ID",
                        diagnosticRaw = """{"failureReason":"文件名中没有解析到 Iwara ID"}""",
                        failureReason = "文件名中没有解析到 Iwara ID"
                    )
                } else {
                    iwaraRepository.fetchById(id, timeoutMillis, options)
                }
            }

            IwaraMatchMode.TitleOnly -> iwaraRepository.searchTitle(query, timeoutMillis, options)

            IwaraMatchMode.IdThenTitle -> {
                if (id.isNotBlank()) {
                    val byId = iwaraRepository.fetchById(id, timeoutMillis, options)
                    if (byId.videos.isNotEmpty()) {
                        byId
                    } else {
                        iwaraRepository.searchTitle(query, timeoutMillis, options)
                    }
                } else {
                    iwaraRepository.searchTitle(query, timeoutMillis, options)
                }
            }
        }
    }

    private suspend fun enrichIwaraMeta(meta: IwaraVideoMeta): IwaraVideoMeta {
        val needsDetail = meta.authorName.isNullOrBlank() ||
            meta.authorUsername.isNullOrBlank() ||
            meta.authorAvatarUrl.isNullOrBlank() ||
            meta.thumbnailUrl.isNullOrBlank() ||
            meta.tags.isEmpty() ||
            meta.description.isNullOrBlank()
        if (!needsDetail) return meta

        val detail = runCatching {
            iwaraRepository.fetchById(
                id = meta.id,
                timeoutMillis = _settingsState.value.matchSearchTimeoutSeconds * 1000L,
                options = currentMatchOptions()
            ).videos.firstOrNull { it.id == meta.id }
        }.getOrNull() ?: return meta

        return meta.copy(
            title = detail.title.ifBlank { meta.title },
            description = detail.description ?: meta.description,
            authorId = detail.authorId ?: meta.authorId,
            authorName = detail.authorName ?: meta.authorName,
            authorUsername = detail.authorUsername ?: meta.authorUsername,
            authorAvatarUrl = detail.authorAvatarUrl ?: meta.authorAvatarUrl,
            thumbnailUrl = detail.thumbnailUrl ?: meta.thumbnailUrl,
            rating = detail.rating ?: meta.rating,
            visibility = detail.visibility ?: meta.visibility,
            createdAt = detail.createdAt ?: meta.createdAt,
            updatedAt = detail.updatedAt ?: meta.updatedAt,
            durationSeconds = detail.durationSeconds ?: meta.durationSeconds,
            likeCount = detail.likeCount ?: meta.likeCount,
            viewCount = detail.viewCount ?: meta.viewCount,
            commentCount = detail.commentCount ?: meta.commentCount,
            tags = if (detail.tags.isNotEmpty()) detail.tags else meta.tags,
            rawJson = detail.rawJson ?: meta.rawJson
        )
    }

    private fun chooseAutoCandidate(
        query: String,
        video: VideoItem,
        candidates: List<IwaraVideoMeta>
    ): IwaraVideoMeta? {
        val id = video.sourceVideoId
        if (!id.isNullOrBlank()) {
            candidates.firstOrNull { it.id == id }?.let { return it }
        }
        if (!_settingsState.value.autoMatchSingleHighConfidence || candidates.size != 1) return null
        val candidate = candidates.first()
        val titleMatched = titleSimilarity(query, candidate.title) >=
            _settingsState.value.autoMatchTitleSimilarityThreshold
        val durationMatched = durationMatches(
            localDurationMs = video.durationMs,
            remoteDurationSeconds = candidate.durationSeconds,
            toleranceSeconds = _settingsState.value.autoMatchDurationToleranceSeconds
        )
        return if (titleMatched && durationMatched) {
            candidate
        } else {
            null
        }
    }

    private fun durationMatches(
        localDurationMs: Long?,
        remoteDurationSeconds: Long?,
        toleranceSeconds: Int
    ): Boolean {
        val localSeconds = localDurationMs?.takeIf { it > 0L }?.let { it / 1000L } ?: return false
        val remoteSeconds = remoteDurationSeconds?.takeIf { it > 0L } ?: return false
        return kotlin.math.abs(localSeconds - remoteSeconds) <= toleranceSeconds
    }

    private fun titleSimilarity(a: String, b: String): Int {
        val na = normalizeTitle(a)
        val nb = normalizeTitle(b)
        if (na.isBlank() || nb.isBlank()) return 0
        if (na == nb) return 100
        if (na.contains(nb) || nb.contains(na)) return 85
        val aw = na.split(" ").filter { it.isNotBlank() }.toSet()
        val bw = nb.split(" ").filter { it.isNotBlank() }.toSet()
        if (aw.isEmpty() || bw.isEmpty()) return 0
        return aw.intersect(bw).size * 100 / aw.union(bw).size
    }

    private fun normalizeTitle(value: String): String {
        return value
            .lowercase()
            .replace(Regex("\\.(mp4|mkv|webm|mov|m4v)$"), "")
            .replace(Regex("""\[[A-Za-z0-9_-]{8,}\]"""), " ")
            .replace(Regex("""\[[^]]+]"""), " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildMatchQuery(video: VideoItem): String {
        return video.title
            ?: video.displayName
                .substringBeforeLast('.', video.displayName)
                .replace(Regex("""\[[A-Za-z0-9_-]{8,}\]"""), "")
                .replace(Regex("""\[[^]]+]"""), "")
                .replace(Regex("""^\s*Iwara\s*-\s*""", RegexOption.IGNORE_CASE), "")
                .trim()
    }

    private fun buildTaskQuery(video: VideoItem): String {
        val id = video.sourceVideoId?.trim().orEmpty()
        return if (_settingsState.value.iwaraMatchMode == IwaraMatchMode.IdOnly && id.isNotBlank()) {
            id
        } else {
            buildMatchQuery(video)
        }
    }

    private fun currentMatchOptions(): IwaraMatchNetworkOptions {
        val state = _settingsState.value
        val templates = state.apiEndpointTemplates.ifEmpty { IwaraMatchNetworkDefaults.apiEndpointTemplates }
        return IwaraMatchNetworkOptions(
            apiEndpointTemplates = templates,
            apiProbeTimeoutMillis = state.apiProbeTimeoutSeconds * 1000L,
            allowPageFallback = state.allowPageFallback,
            fetchSearchResultDetailsWithApi = state.fetchSearchResultDetailsWithApi,
            maxSearchApiDetails = state.maxSearchApiDetails
        )
    }

    private fun parseApiEndpointTemplates(raw: String): List<String> {
        return raw
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.contains("{id}") }
            .distinct()
            .take(12)
    }

    private fun normalizeIwaraId(value: String): String? {
        val raw = value
            .substringAfterLast("/video/")
            .substringBefore("?")
            .substringBefore("#")
            .trim()
        return raw.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,}")) }
    }

    private fun statusesForFilter(filter: MatchTaskFilter): List<String> {
        return when (filter) {
            MatchTaskFilter.All -> emptyList()
            MatchTaskFilter.Running -> listOf(MatchTaskStatus.Pending, MatchTaskStatus.Running)
            MatchTaskFilter.Success -> listOf(MatchTaskStatus.AutoMatched)
            MatchTaskFilter.NeedReview -> listOf(MatchTaskStatus.NeedReview)
            MatchTaskFilter.Failed -> listOf(MatchTaskStatus.Failed)
            MatchTaskFilter.Skipped -> listOf(MatchTaskStatus.Skipped)
            MatchTaskFilter.Unqueued -> emptyList()
        }
    }

    private fun Set<String>.toggle(value: String): Set<String> {
        return if (contains(value)) this - value else this + value
    }

    private companion object {
        const val PREFS_NAME = "iwara_manager_prefs"

        const val KEY_FOLDER_URI = "folder_uri"
        const val KEY_LAYOUT_MODE = "layout_mode"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_SHOW_REMATCH_BUTTON_IN_LIST = "show_rematch_button_in_list"
        const val KEY_VIDEO_OPEN_MODE = "video_open_mode"
        const val KEY_VIDEO_PLAYER_COMPONENT = "video_player_component"
        const val KEY_VIDEO_PLAYER_LABEL = "video_player_label"
        const val KEY_IWARA_MATCH_MODE = "iwara_match_mode"
        const val KEY_MATCH_SEARCH_TIMEOUT_SECONDS = "match_search_timeout_seconds"
        const val KEY_API_PROBE_TIMEOUT_SECONDS = "api_probe_timeout_seconds"
        const val KEY_API_ENDPOINT_TEMPLATES = "api_endpoint_templates"
        const val KEY_ALLOW_PAGE_FALLBACK = "allow_page_fallback"
        const val KEY_FETCH_SEARCH_RESULT_DETAILS_WITH_API = "fetch_search_result_details_with_api"
        const val KEY_MAX_SEARCH_API_DETAILS = "max_search_api_details"
        const val KEY_BATCH_MATCH_THREADS = "batch_match_threads"
        const val KEY_AUTO_MATCH_SINGLE_HIGH_CONFIDENCE = "auto_match_single_high_confidence"
        const val KEY_AUTO_MATCH_TITLE_SIMILARITY_THRESHOLD = "auto_match_title_similarity_threshold"
        const val KEY_AUTO_MATCH_DURATION_TOLERANCE_SECONDS = "auto_match_duration_tolerance_seconds"
        const val KEY_AUTO_MATCH_SKIP_NO_ID = "auto_match_skip_no_id"
        const val SKIPPED_FROM_UNQUEUED_MESSAGE = "从未匹配列表手动跳过"
    }
}
