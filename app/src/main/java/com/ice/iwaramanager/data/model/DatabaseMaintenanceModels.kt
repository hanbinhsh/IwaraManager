package com.ice.iwaramanager.data.model

data class DatabaseImportResult(
    val importedBytes: Long,
    val databaseVersion: Int
)

data class CleanupResult(
    val mediaRecordsDeleted: Int,
    val folderRecordsDeleted: Int
) {
    val totalRecordsDeleted: Int
        get() = mediaRecordsDeleted + folderRecordsDeleted
}
