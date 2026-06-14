package com.ice.iwaramanager.domain.parser

data class IwaraFilenameParseResult(
    val rawName: String,
    val cleanedName: String,
    val titleFromFilename: String?,
    val videoId: String?,
    val quality: String?,
    val extension: String?
)