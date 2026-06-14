package com.ice.iwaramanager.domain.scanner

data class ScannedLibrary(
    val videos: List<ScannedVideo>,
    val folders: List<ScannedFolder>
)

data class ScannedFolder(
    val path: String,
    val parentPath: String?,
    val name: String
)
