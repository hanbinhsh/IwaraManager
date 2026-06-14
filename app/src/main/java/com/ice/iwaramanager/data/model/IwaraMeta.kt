package com.ice.iwaramanager.data.model

data class IwaraTagMeta(
    val namespace: String,
    val name: String
) {
    val key: String
        get() = "${namespace}:${name}".lowercase()
}

data class IwaraVideoMeta(
    val id: String,
    val title: String,
    val description: String? = null,
    val authorId: String? = null,
    val authorName: String? = null,
    val authorUsername: String? = null,
    val authorAvatarUrl: String? = null,
    val thumbnailUrl: String? = null,
    val rating: String? = null,
    val visibility: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val durationSeconds: Long? = null,
    val likeCount: Int? = null,
    val viewCount: Int? = null,
    val commentCount: Int? = null,
    val tags: List<IwaraTagMeta> = emptyList(),
    val rawJson: String? = null
)

data class IwaraSearchMetaResult(
    val videos: List<IwaraVideoMeta>,
    val diagnosticSummary: String,
    val diagnosticRaw: String,
    val failureReason: String?
)

data class IwaraMatchNetworkOptions(
    val apiEndpointTemplates: List<String> = IwaraMatchNetworkDefaults.apiEndpointTemplates,
    val apiProbeTimeoutMillis: Long = 30_000L,
    val allowPageFallback: Boolean = false,
    val fetchSearchResultDetailsWithApi: Boolean = true,
    val maxSearchApiDetails: Int = 10
)

object IwaraMatchNetworkDefaults {
    val apiEndpointTemplates: List<String> = listOf(
        "https://api.iwara.tv/video/{id}"
    )
}

data class IwaraMatchCandidate(
    val id: String,
    val title: String,
    val authorName: String?,
    val authorUsername: String?,
    val thumbnailUrl: String?,
    val rawJson: String?
)

object MatchTaskStatus {
    const val Pending = "pending"
    const val Running = "running"
    const val AutoMatched = "auto_matched"
    const val NeedReview = "need_review"
    const val Failed = "failed"
    const val Skipped = "skipped"
}

enum class MatchTaskFilter {
    All,
    Running,
    Success,
    NeedReview,
    Failed,
    Skipped,
    Unqueued
}

enum class IwaraMatchMode {
    IdThenTitle,
    IdOnly,
    TitleOnly
}

enum class FilterTab {
    Tags,
    Authors,
    Quality
}
