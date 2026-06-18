package com.ice.iwaramanager.data.model

enum class LibrarySourceType {
    LocalSaf,
    WebDav
}

enum class RemoteIndexMode {
    Light,
    Full
}

data class LibrarySource(
    val id: String,
    val name: String,
    val type: LibrarySourceType,
    val rootUriString: String,
    val webDavBaseUrl: String? = null,
    val webDavRootPath: String? = null,
    val webDavUsername: String? = null,
    val webDavAllowInsecureTls: Boolean = false,
    val remoteIndexMode: RemoteIndexMode = RemoteIndexMode.Light,
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 30,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastCompletedScanAt: Long? = null
) {
    val isRemote: Boolean
        get() = type == LibrarySourceType.WebDav
}

data class LibrarySourceScope(
    val key: String,
    val label: String,
    val sourceIds: List<String>,
    val folderSourceId: String? = null,
    val folderPath: String? = null
)

data class LibraryFolderNode(
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val parentPath: String?,
    val name: String,
    val videoCount: Int = 0
)

data class WebDavSourceForm(
    val editingSourceId: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val rootPath: String = "/",
    val username: String = "",
    val password: String = "",
    val allowInsecureTls: Boolean = false,
    val indexMode: RemoteIndexMode = RemoteIndexMode.Light,
    val connectTimeoutSecondsText: String = "15",
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSecondsText: String = "30",
    val readTimeoutSeconds: Int = 30
)

data class RemotePlaybackDiagnostic(
    val message: String,
    val detail: String? = null
)
