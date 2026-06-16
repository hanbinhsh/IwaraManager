package com.ice.iwaramanager.domain.scanner

data class ScannedFolder(
    val path: String,
    val parentPath: String?,
    val name: String
)
