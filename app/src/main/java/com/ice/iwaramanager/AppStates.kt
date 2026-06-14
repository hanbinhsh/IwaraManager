package com.ice.iwaramanager

import com.ice.iwaramanager.data.local.dao.CountItem
import com.ice.iwaramanager.data.local.entity.MatchCandidateEntity
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import com.ice.iwaramanager.data.model.FilterTab
import com.ice.iwaramanager.data.model.IwaraMatchMode
import com.ice.iwaramanager.data.model.IwaraMatchNetworkDefaults
import com.ice.iwaramanager.data.model.IwaraVideoMeta
import com.ice.iwaramanager.data.model.LibraryFolderNode
import com.ice.iwaramanager.data.model.LibraryLayoutMode
import com.ice.iwaramanager.data.model.LibrarySource
import com.ice.iwaramanager.data.model.LibrarySourceScope
import com.ice.iwaramanager.data.model.MatchTaskFilter
import com.ice.iwaramanager.data.model.RemotePlaybackDiagnostic
import com.ice.iwaramanager.data.model.VideoItem
import com.ice.iwaramanager.data.model.VideoOpenMode
import com.ice.iwaramanager.data.model.WebDavSourceForm

data class LibraryUiState(
    val folderUriString: String? = null,
    val videos: List<VideoItem> = emptyList(),
    val filteredVideos: List<VideoItem> = emptyList(),
    val librarySources: List<LibrarySource> = emptyList(),
    val sourceScopes: List<LibrarySourceScope> = emptyList(),
    val selectedSourceScopeKey: String = ALL_SOURCES_KEY,
    val currentDirectorySourceId: String? = null,
    val currentDirectoryPath: String = "",
    val directoryFolders: List<LibraryFolderNode> = emptyList(),
    val directoryVideos: List<VideoItem> = emptyList(),

    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.Grid,
    val gridColumns: Int = 2,
    val filterTab: FilterTab = FilterTab.Tags,
    val selectedTagKeys: Set<String> = emptySet(),
    val selectedAuthorKeys: Set<String> = emptySet(),
    val selectedQualities: Set<String> = emptySet(),
    val tagCounts: List<CountItem> = emptyList(),
    val authorCounts: List<CountItem> = emptyList(),
    val qualityCounts: List<CountItem> = emptyList(),
    val taskFilter: MatchTaskFilter = MatchTaskFilter.All,
    val matchTasks: List<MatchTaskEntity> = emptyList(),
    val unqueuedVideos: List<VideoItem> = emptyList(),
    val matchTaskFilterCounts: Map<MatchTaskFilter, Int> = emptyMap(),
    val isBatchMatching: Boolean = false,

    val isScanning: Boolean = false,
    val scanDone: Int = 0,
    val scanTotal: Int = 0,
    val scanCurrentName: String? = null,

    val error: String? = null
)

data class SearchUiState(
    val folderUriString: String? = null,
    val query: String = "",
    val results: List<VideoItem> = emptyList(),
    val error: String? = null
)

data class SettingsUiState(
    val folderUriString: String? = null,
    val librarySources: List<LibrarySource> = emptyList(),
    val webDavForm: WebDavSourceForm = WebDavSourceForm(),
    val remoteProxyIdleTimeoutSecondsText: String = "300",
    val remoteProxyIdleTimeoutSeconds: Int = 300,
    val remoteProxyReadTimeoutSecondsText: String = "60",
    val remoteProxyReadTimeoutSeconds: Int = 60,
    val showRemotePlaybackDiagnostics: Boolean = true,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.Grid,
    val gridColumns: Int = 2,
    val showRematchButtonInList: Boolean = true,
    val videoOpenMode: VideoOpenMode = VideoOpenMode.InApp,
    val selectedVideoPlayerComponentName: String? = null,
    val selectedVideoPlayerLabel: String? = null,
    val iwaraMatchMode: IwaraMatchMode = IwaraMatchMode.IdThenTitle,
    val matchSearchTimeoutSecondsText: String = "60",
    val matchSearchTimeoutSeconds: Int = 60,
    val apiProbeTimeoutSecondsText: String = "30",
    val apiProbeTimeoutSeconds: Int = 30,
    val apiEndpointTemplatesText: String = IwaraMatchNetworkDefaults.apiEndpointTemplates.joinToString("\n"),
    val apiEndpointTemplates: List<String> = IwaraMatchNetworkDefaults.apiEndpointTemplates,
    val allowPageFallback: Boolean = false,
    val fetchSearchResultDetailsWithApi: Boolean = true,
    val maxSearchApiDetailsText: String = "10",
    val maxSearchApiDetails: Int = 10,
    val batchMatchThreadsText: String = "1",
    val batchMatchThreads: Int = 1,
    val autoMatchSingleHighConfidence: Boolean = true,
    val autoMatchTitleSimilarityThresholdText: String = "90",
    val autoMatchTitleSimilarityThreshold: Int = 90,
    val autoMatchDurationToleranceSecondsText: String = "2",
    val autoMatchDurationToleranceSeconds: Int = 2,
    val autoMatchSkipNoId: Boolean = false,
    val isScanning: Boolean = false,
    val videoCount: Int = 0,
    val message: String? = null,
    val error: String? = null
)

data class VideoDetailUiState(
    val video: VideoItem? = null
)

data class PlayerUiState(
    val video: VideoItem? = null,
    val playbackUriString: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val diagnostic: RemotePlaybackDiagnostic? = null
)

data class MatchUiState(
    val video: VideoItem? = null,
    val sourceTaskId: Long? = null,
    val query: String = "",
    val candidates: List<IwaraVideoMeta> = emptyList(),
    val isSearching: Boolean = false,
    val isBinding: Boolean = false,
    val error: String? = null,
    val searchDiagnosticSummary: String? = null,
    val searchDiagnosticRaw: String? = null,
    val showSearchDiagnosticRaw: Boolean = false
)

data class MatchTaskDetailUiState(
    val task: MatchTaskEntity? = null,
    val candidates: List<MatchCandidateEntity> = emptyList(),
    val isBinding: Boolean = false,
    val error: String? = null
)

const val ALL_SOURCES_KEY = "__all__"
const val LOCAL_SOURCES_KEY = "__local__"
