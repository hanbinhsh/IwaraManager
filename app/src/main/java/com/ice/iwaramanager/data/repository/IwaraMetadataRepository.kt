package com.ice.iwaramanager.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.webkit.CookieManager
import com.ice.iwaramanager.data.model.IwaraSearchMetaResult
import com.ice.iwaramanager.data.model.IwaraMatchNetworkOptions
import com.ice.iwaramanager.data.model.IwaraTagMeta
import com.ice.iwaramanager.data.model.IwaraVideoMeta
import com.ice.iwaramanager.data.remote.IwaraSessionManager
import com.ice.iwaramanager.data.remote.IwaraSearchWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class IwaraMetadataRepository(
    context: Context
) {
    private val searcher = IwaraSearchWebView(context)

    suspend fun fetchById(
        id: String,
        timeoutMillis: Long,
        options: IwaraMatchNetworkOptions = IwaraMatchNetworkOptions()
    ): IwaraSearchMetaResult {
        val raw = searcher.fetchById(id, timeoutMillis, options)
        return parseSearchResult(raw, id, options)
    }

    suspend fun searchTitle(
        title: String,
        timeoutMillis: Long,
        options: IwaraMatchNetworkOptions = IwaraMatchNetworkOptions()
    ): IwaraSearchMetaResult {
        val raw = searcher.searchByTitle(title, timeoutMillis, options)
        val searchResult = parseSearchResult(raw, title, options)
        if (!options.fetchSearchResultDetailsWithApi || searchResult.videos.isEmpty()) {
            return searchResult
        }

        val duplicateTitleKeys = searchResult.videos
            .map { normalizedTitleKey(it.title) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
        val candidatesNeedingDetails = searchResult.videos
            .filter { candidate ->
                shouldFetchSearchDetail(candidate, title, duplicateTitleKeys)
            }
            .take(options.maxSearchApiDetails)
        if (candidatesNeedingDetails.isEmpty()) {
            return searchResult
        }

        var detailSuccess = 0
        val detailFailures = mutableListOf<String>()
        val detailsById = linkedMapOf<String, IwaraVideoMeta>()
        candidatesNeedingDetails.forEach { candidate ->
            val detailResult = runCatching {
                fetchById(candidate.id, timeoutMillis, options)
            }.getOrElse { error ->
                detailFailures += "${candidate.id}: ${error.message ?: error::class.java.simpleName}"
                return@forEach
            }
            val detail = detailResult.videos.firstOrNull { it.id == candidate.id }
            if (detail != null) {
                detailsById[candidate.id] = mergeSearchCandidateWithDetail(candidate, detail)
                detailSuccess += 1
            } else {
                detailFailures += "${candidate.id}: ${detailResult.failureReason ?: detailResult.diagnosticSummary}"
            }
        }

        val videos = searchResult.videos.map { candidate ->
            detailsById[candidate.id] ?: candidate
        }
        val summary = buildString {
            append(searchResult.diagnosticSummary)
            append("；搜索候选按ID获取详情 $detailSuccess/${candidatesNeedingDetails.size}")
            if (detailFailures.isNotEmpty()) {
                append("；详情失败 ${detailFailures.size} 个")
            }
        }
        val rawWithDetails = runCatching {
            val original = JSONObject(searchResult.diagnosticRaw)
            original
                .put("summary", summary)
                .put("searchDetailFetchSuccessIds", JSONArray(detailsById.keys))
                .put("searchDetailFetchFailures", JSONArray(detailFailures))
                .toString(2)
        }.getOrElse { searchResult.diagnosticRaw }

        return searchResult.copy(
            videos = videos,
            diagnosticSummary = summary,
            diagnosticRaw = rawWithDetails
        )
    }

    private suspend fun parseSearchResult(
        raw: String,
        query: String,
        options: IwaraMatchNetworkOptions
    ): IwaraSearchMetaResult {
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val ids = mutableListOf<String>()
        val metaById = linkedMapOf<String, JSONObject>()
        json.optJSONArray("results")?.let { array ->
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id").trim()
                if (id.isBlank()) continue
                ids += id
                metaById[id] = obj
            }
        }
        json.optJSONArray("candidateIds")?.let { array ->
            for (index in 0 until array.length()) {
                val id = array.optString(index).trim()
                if (id.isNotBlank()) ids += id
            }
        }

        val blockingFailure = isBlockingFailure(json)
        val candidateIds = if (blockingFailure) {
            emptyList()
        } else {
            ids.distinct()
        }

        val isSearchPage = json.optString("url")
            .contains("/search", ignoreCase = true)
        val pageMeta = json.optJSONObject("pageMeta")
        val usePageMetaForSingleCandidate = candidateIds.size == 1 && !isSearchPage
        val apiProbeReturnedData = (json.optJSONArray("apiProbeSummaries")?.length() ?: 0) > 0
        val apiAuthHeader = json.optString("apiAuthHeader")
            .takeIf { it.isNotBlank() }

        val videos = candidateIds.map { id ->
            val candidateMeta = metaById[id]
            val detailMeta = if (usePageMetaForSingleCandidate) pageMeta else null
            val candidateFromApi = apiProbeReturnedData && candidateMeta != null
            val fallbackDetailMeta = if (candidateFromApi) null else detailMeta
            val html = json.optString("html")
            val bodyText = json.optString("bodyText")
            val serializedAuthor = if (candidateFromApi) SerializedAuthor() else extractSerializedAuthor(html, id)
            val authorUsername = firstString(
                candidateMeta?.optString("authorUsername"),
                fallbackDetailMeta?.optString("authorUsername"),
                serializedAuthor.username
            )
            val authorId = firstString(
                candidateMeta?.optString("authorId"),
                fallbackDetailMeta?.optString("authorId"),
                serializedAuthor.id
            )?.takeUnless { value ->
                authorUsername != null && value.equals(authorUsername, ignoreCase = true)
            }
            val tags = tagsFromJson(candidateMeta, fallbackDetailMeta).ifEmpty {
                if (!candidateFromApi && usePageMetaForSingleCandidate) {
                    extractTags(html, bodyText, id)
                } else {
                    emptyList()
                }
            }
            val apiDurationSeconds = firstLong(candidateMeta, null, "durationSeconds")
            val probedDurationSeconds = firstJsonLongValue(
                json.opt("mediaDurationSeconds"),
                json.opt("pageVideoDurationSeconds"),
                json.opt("textDurationSeconds")
            )?.takeIf { it > 0L && candidateFromApi && candidateIds.size == 1 }
            val nativeDurationSeconds = if (apiDurationSeconds == null && probedDurationSeconds == null && candidateFromApi) {
                json.put("nativeMediaDurationProbeStarted", true)
                val resolved = resolveRemoteDurationSeconds(candidateMeta?.optString("fileUrl"), options, apiAuthHeader)
                json.put("nativeMediaDurationProbeDone", true)
                json.put("nativeMediaDurationSeconds", resolved ?: JSONObject.NULL)
                resolved
            } else {
                null
            }
            val remoteDurationSeconds = apiDurationSeconds
                ?: probedDurationSeconds
                ?: nativeDurationSeconds
            remoteDurationSeconds?.takeIf { it > 0L }?.let { seconds ->
                candidateMeta?.put("durationSeconds", seconds)
            }
            IwaraVideoMeta(
                id = id,
                title = extractTitle(
                    json = json,
                    query = query,
                    id = id,
                    candidateMeta = candidateMeta,
                    detailMeta = fallbackDetailMeta,
                    html = html,
                    allowQueryFallback = !isSearchPage && usePageMetaForSingleCandidate
                ),
                description = firstString(
                    candidateMeta?.optString("description"),
                    fallbackDetailMeta?.optString("description"),
                    if (candidateFromApi) null else extractSerializedDescription(html, id)
                ),
                authorId = firstString(
                    authorId
                ),
                authorName = firstString(
                    candidateMeta?.optString("authorName"),
                    fallbackDetailMeta?.optString("authorName"),
                    serializedAuthor.name
                ),
                authorUsername = firstString(
                    authorUsername
                ),
                authorAvatarUrl = firstString(
                    candidateMeta?.optString("authorAvatarUrl"),
                    fallbackDetailMeta?.optString("authorAvatarUrl"),
                    serializedAuthor.avatarUrl
                ),
                thumbnailUrl = firstString(
                    candidateMeta?.optString("thumbnailUrl"),
                    fallbackDetailMeta?.optString("thumbnailUrl"),
                    if (candidateFromApi) null else extractThumbnail(json.optString("html"))
                ),
                rating = firstString(
                    candidateMeta?.optString("rating"),
                    fallbackDetailMeta?.optString("rating"),
                    if (candidateFromApi) null else extractSerializedRating(html, id),
                    if (candidateFromApi) null else extractRating(bodyText)
                ),
                visibility = firstString(
                    candidateMeta?.optString("visibility"),
                    fallbackDetailMeta?.optString("visibility"),
                    if (candidateFromApi) null else extractSerializedVisibility(html, id),
                    if (candidateFromApi) null else extractVisibility(bodyText)
                ),
                createdAt = firstString(
                    candidateMeta?.optString("createdAt"),
                    fallbackDetailMeta?.optString("createdAt"),
                    if (candidateFromApi) null else extractSerializedDate(
                        html,
                        id,
                        "createdAt",
                        "created_at",
                        "publishedAt",
                        "published_at",
                        "postedAt",
                        "posted_at",
                        "date"
                    )
                ),
                updatedAt = firstString(
                    candidateMeta?.optString("updatedAt"),
                    fallbackDetailMeta?.optString("updatedAt"),
                    if (candidateFromApi) null else extractSerializedDate(html, id, "updatedAt", "updated_at", "modifiedAt", "modified_at")
                ),
                durationSeconds = remoteDurationSeconds ?: if (candidateFromApi) {
                    null
                } else {
                    firstLong(null, fallbackDetailMeta, "durationSeconds")
                        ?: extractSerializedDurationSeconds(html, id)
                },
                likeCount = firstInt(candidateMeta, fallbackDetailMeta, "likeCount")
                    ?: if (candidateFromApi) null else extractSerializedInt(html, id, "likeCount", "like_count", "likes", "numLikes", "num_likes"),
                viewCount = firstInt(candidateMeta, fallbackDetailMeta, "viewCount")
                    ?: if (candidateFromApi) null else extractSerializedInt(html, id, "viewCount", "view_count", "views", "numViews", "num_views"),
                commentCount = firstInt(candidateMeta, fallbackDetailMeta, "commentCount")
                    ?: if (candidateFromApi) null else extractSerializedInt(html, id, "commentCount", "comment_count", "comments", "numComments", "num_comments"),
                tags = tags,
                rawJson = compactSearchJson(json).toString(2)
            )
        }

        val failure = when {
            blockingFailure -> json.optString("failureReason").ifBlank {
                blockingFailureReason(json)
            }
            videos.isNotEmpty() -> null
            else -> json.optString("failureReason").ifBlank { "没有提取到候选 ID" }
        }

        val summary = json.optString("diagnosticSummary").ifBlank {
            failure ?: "搜索成功"
        }

        val diagnosticRaw = JSONObject()
            .put("query", query)
            .put("search", compactSearchJson(json))
            .put("candidateIds", JSONArray(candidateIds))
            .put("metaSuccessIds", JSONArray(videos.map { it.id }))
            .put("failureReason", failure)
            .put("summary", summary)
            .toString(2)

        return IwaraSearchMetaResult(
            videos = videos,
            diagnosticSummary = summary,
            diagnosticRaw = diagnosticRaw,
            failureReason = failure
        )
    }

    private fun firstJsonLongValue(vararg values: Any?): Long? {
        values.forEach { value ->
            val parsed = when (value) {
                null, JSONObject.NULL -> null
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            if (parsed != null && parsed > 0L) return parsed
        }
        return null
    }
    private suspend fun resolveRemoteDurationSeconds(
        fileUrl: String?,
        options: IwaraMatchNetworkOptions,
        apiAuthHeader: String?
    ): Long? {
        val url = normalizeMediaUrl(fileUrl?.takeIf { it.isNotBlank() } ?: return null)
        val timeoutMillis = options.apiProbeTimeoutMillis.coerceIn(5_000L, 120_000L)
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMillis) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(url, mediaRequestHeaders(url, options, apiAuthHeader))
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0L }
                        ?.let { (it + 500L) / 1000L }
                } catch (_: Throwable) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
    }

    private fun normalizeMediaUrl(value: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://api.iwara.tv$value"
        else -> value
    }

    private fun mediaRequestHeaders(
        mediaUrl: String,
        options: IwaraMatchNetworkOptions,
        apiAuthHeader: String?
    ): Map<String, String> {
        val forbidden = setOf(
            "accept-charset",
            "accept-encoding",
            "access-control-request-headers",
            "access-control-request-method",
            "connection",
            "content-length",
            "cookie",
            "date",
            "dnt",
            "expect",
            "host",
            "keep-alive",
            "origin",
            "permissions-policy",
            "referer",
            "set-cookie",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "via"
        )
        val headers = linkedMapOf(
            "User-Agent" to IwaraSessionManager.USER_AGENT,
            "Referer" to "https://www.iwara.tv/"
        )
        options.apiRequestHeaders.forEach { (name, value) ->
            val normalizedName = name.trim()
            val normalizedValue = value.trim()
            if (
                normalizedName.isNotBlank() &&
                normalizedValue.isNotBlank() &&
                normalizedName.lowercase() !in forbidden
            ) {
                headers[normalizedName] = normalizedValue
            }
        }
        apiAuthHeader
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["Authorization"] = it }
        cookieHeaderFor(mediaUrl)
            ?.let { headers["Cookie"] = it }
        return headers
    }

    private fun cookieHeaderFor(mediaUrl: String): String? {
        val cookieManager = CookieManager.getInstance()
        val cookies = listOf(
            "https://www.iwara.tv",
            "https://api.iwara.tv",
            mediaUrl
        ).mapNotNull { url ->
            runCatching { cookieManager.getCookie(url) }.getOrNull()
        }
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return cookies.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun compactSearchJson(json: JSONObject): JSONObject {
        val result = JSONObject(json.toString())
        result.remove("apiAuthHeader")
        result.remove("apiAuthToken")
        result.remove("nativeAuthHeader")
        result.remove("bodyText")
        result.remove("html")
        result.optJSONArray("results")?.let { array ->
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { item ->
                    item.remove("fileUrl")
                    item.remove("apiAuthHeader")
                    item.remove("apiAuthToken")
                    item.remove("nativeAuthHeader")
                }
            }
            if (array.length() > 0) {
                result.remove("pageMeta")
            }
        }
        result.put(
            "apiProbeSummaries",
            compactApiSummaries(result.optJSONArray("apiProbeSummaries"))
        )
        return result
    }

    private fun compactApiSummaries(array: JSONArray?): JSONArray {
        if (array == null) return JSONArray()
        val seen = linkedSetOf<String>()
        val compact = JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("videoId")
                .ifBlank { item.optString("url") }
                .ifBlank { index.toString() }
            if (!seen.add(key)) continue
            compact.put(
                JSONObject()
                    .put("url", item.optString("url"))
                    .put("videoId", item.optString("videoId"))
                    .put("title", item.optString("title"))
                    .put("rating", item.optString("rating"))
                    .put("visibility", item.optString("visibility"))
                    .put("createdAt", item.optString("createdAt"))
                    .put("updatedAt", item.optString("updatedAt"))
                    .put("fileDuration", item.opt("fileDuration") ?: JSONObject.NULL)
                    .put("fileUrlPresent", item.optBoolean("fileUrlPresent"))
                    .put("userId", item.optString("userId"))
                    .put("userName", item.optString("userName"))
                    .put("userUsername", item.optString("userUsername"))
                    .put("tagCount", item.optInt("tagCount"))
                    .put("rootKeys", item.optJSONArray("rootKeys") ?: JSONArray())
                    .put("fileKeys", item.optJSONArray("fileKeys") ?: JSONArray())
                    .put("userKeys", item.optJSONArray("userKeys") ?: JSONArray())
            )
        }
        return compact
    }

    private fun mergeSearchCandidateWithDetail(
        candidate: IwaraVideoMeta,
        detail: IwaraVideoMeta
    ): IwaraVideoMeta {
        val detailHasAuthorIdentity = !detail.authorId.isNullOrBlank() || !detail.authorUsername.isNullOrBlank()
        return candidate.copy(
            title = detail.title.ifBlank { candidate.title },
            description = detail.description ?: candidate.description,
            authorId = detail.authorId ?: candidate.authorId,
            authorName = if (detailHasAuthorIdentity) detail.authorName else detail.authorName ?: candidate.authorName,
            authorUsername = detail.authorUsername ?: candidate.authorUsername,
            authorAvatarUrl = detail.authorAvatarUrl ?: candidate.authorAvatarUrl,
            thumbnailUrl = detail.thumbnailUrl ?: candidate.thumbnailUrl,
            rating = detail.rating ?: candidate.rating,
            visibility = detail.visibility ?: candidate.visibility,
            createdAt = detail.createdAt ?: candidate.createdAt,
            updatedAt = detail.updatedAt ?: candidate.updatedAt,
            durationSeconds = detail.durationSeconds ?: candidate.durationSeconds,
            likeCount = detail.likeCount ?: candidate.likeCount,
            viewCount = detail.viewCount ?: candidate.viewCount,
            commentCount = detail.commentCount ?: candidate.commentCount,
            tags = if (detail.tags.isNotEmpty()) detail.tags else candidate.tags,
            rawJson = detail.rawJson ?: candidate.rawJson
        )
    }

    private fun shouldFetchSearchDetail(
        candidate: IwaraVideoMeta,
        query: String,
        duplicateTitleKeys: Map<String, Int>
    ): Boolean {
        if (candidate.id.isNotBlank()) return true
        if (candidate.durationSeconds == null || candidate.durationSeconds <= 0L) return true
        val titleKey = normalizedTitleKey(candidate.title)
        if (titleKey.isBlank()) return true
        if (titleKey == normalizedTitleKey(candidate.id)) return true
        val authorNameKey = normalizedTitleKey(candidate.authorName)
        val authorUsernameKey = normalizedTitleKey(candidate.authorUsername)
        if (authorNameKey.isNotBlank() && (titleKey == authorNameKey || authorNameKey.contains(titleKey))) return true
        if (authorUsernameKey.isNotBlank() && (titleKey == authorUsernameKey || authorUsernameKey.contains(titleKey))) return true
        if ((duplicateTitleKeys[titleKey] ?: 0) > 1) return true
        if (looksLikeMetadataTitle(candidate.title)) return true
        return false
    }

    private fun normalizedTitleKey(value: String?): String {
        return value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.lowercase()
            .orEmpty()
    }

    private fun looksLikeMetadataTitle(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return true
        if (Regex("^(search|loading|iwara|error|404|not found|videos?|images?|upload|uploads|latest|popular|profile|login|settings)$", RegexOption.IGNORE_CASE).matches(text)) return true
        if (Regex("\\b\\d+\\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?|mo\\.?|mos|months?|y|yr|yrs|years?)\\s+ago\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
        if (text.contains("ago", ignoreCase = true) &&
            Regex("\\b(\\d+([,.]\\d+)?k?|[0-9]{1,2}:[0-9]{2})\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) return true
        if (Regex("\\b[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?\\b").containsMatchIn(text) &&
            Regex("\\b(views?|likes?|comments?|\\d+([,.]\\d+)?k?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) return true
        return false
    }

    private fun isBlockingFailure(json: JSONObject): Boolean {
        if ((json.optJSONArray("results")?.length() ?: 0) > 0) {
            return false
        }
        val title = json.optString("pageTitle")
        val timedOut = json.optBoolean("timedOut")
        val bodyTextLength = json.optInt("bodyTextLength")
        val htmlLength = json.optInt("htmlLength")
        return json.optBoolean("pageError") ||
            json.optBoolean("loading") ||
            title.contains("Error | Iwara", true) ||
            title.equals("Error", ignoreCase = true) ||
            title.equals("Loading", ignoreCase = true) ||
            title.contains("404", true) ||
            title.contains("Not Found", true) ||
            json.optBoolean("cloudflare") ||
            json.optBoolean("noResults") ||
            (timedOut && bodyTextLength == 0 && htmlLength == 0)
    }

    private fun blockingFailureReason(json: JSONObject): String {
        val timedOut = json.optBoolean("timedOut")
        val timeoutMillis = json.optLong("timeoutMillis")
        return when {
            json.optBoolean("pageError") ||
                json.optString("pageTitle").contains("Error | Iwara", true) -> "Iwara 返回错误页面"
            timedOut -> "Iwara 页面超时（${timeoutMillis / 1000} 秒）"
            json.optBoolean("loading") ||
                json.optString("pageTitle").equals("Loading", ignoreCase = true) -> "Iwara 页面仍停留在 Loading，未提取到有效视频"
            json.optBoolean("cloudflare") -> "Iwara Cloudflare 校验未通过"
            json.optBoolean("noResults") -> "Iwara 搜索无结果"
            else -> "Iwara 页面加载失败"
        }
    }

    private fun extractTitle(
        json: JSONObject,
        query: String,
        id: String,
        candidateMeta: JSONObject?,
        detailMeta: JSONObject?,
        html: String,
        allowQueryFallback: Boolean
    ): String {
        cleanTitle(candidateMeta?.optString("searchTitle").orEmpty())?.let { return it }
        cleanTitle(candidateMeta?.optString("title").orEmpty())?.let { return it }
        cleanTitle(detailMeta?.optString("title").orEmpty())?.let { return it }
        cleanTitle(extractSerializedTitle(html, id).orEmpty())?.let { return it }
        val pageTitle = cleanTitle(
            json.optString("pageTitle")
                .replace(" | Iwara", "")
                .replace("Iwara", "")
        )
        return pageTitle ?: if (allowQueryFallback) query.ifBlank { id } else id
    }

    private fun cleanTitle(value: String): String? {
        val text = value
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isBlank()) return null
        if (text.equals("Search", ignoreCase = true)) return null
        if (text.equals("Loading", ignoreCase = true)) return null
        if (text.equals("Iwara", ignoreCase = true)) return null
        if (text.equals("Error", ignoreCase = true)) return null
        if (text.equals("404", ignoreCase = true)) return null
        if (text.contains("Not Found", ignoreCase = true)) return null
        if (Regex("\\b\\d+\\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?|mo\\.?|mos|months?|y|yr|yrs|years?)\\s+ago\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        if (text.contains("ago", ignoreCase = true) &&
            Regex("\\b(\\d+([,.]\\d+)?k?|[0-9]{1,2}:[0-9]{2})\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) return null
        if (Regex("\\b[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?\\b").containsMatchIn(text) &&
            Regex("\\b(views?|likes?|comments?|\\d+([,.]\\d+)?k?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) return null
        return text.take(160)
    }

    private fun firstString(vararg values: String?): String? {
        return values.firstNotNullOfOrNull { value ->
            value
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "null" && it != "[object Object]" }
        }
    }

    private fun firstLong(
        candidateMeta: JSONObject?,
        detailMeta: JSONObject?,
        key: String
    ): Long? {
        return listOf(candidateMeta, detailMeta)
            .firstNotNullOfOrNull { obj ->
                val value = obj?.opt(key) ?: return@firstNotNullOfOrNull null
                when (value) {
                    JSONObject.NULL -> null
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    else -> null
                }
            }
    }

    private fun firstInt(
        candidateMeta: JSONObject?,
        detailMeta: JSONObject?,
        key: String
    ): Int? {
        return firstLong(candidateMeta, detailMeta, key)?.toInt()
    }

    private fun tagsFromJson(
        candidateMeta: JSONObject?,
        detailMeta: JSONObject?
    ): List<IwaraTagMeta> {
        val tags = linkedSetOf<IwaraTagMeta>()
        listOf(candidateMeta, detailMeta).forEach { obj ->
            val array = obj?.optJSONArray("tags") ?: return@forEach
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val namespace = item.optString("namespace").ifBlank { "tag" }
                val name = item.optString("name").trim()
                if (name.isNotBlank()) {
                    tags += IwaraTagMeta(namespace, name)
                }
            }
        }
        return tags.toList()
    }

    private data class SerializedAuthor(
        val id: String? = null,
        val name: String? = null,
        val username: String? = null,
        val avatarUrl: String? = null
    )

    private fun extractSerializedTitle(
        html: String,
        id: String
    ): String? {
        val window = jsonishWindow(html, id)
        return firstJsonString(window, "title")
    }

    private fun extractSerializedDescription(
        html: String,
        id: String
    ): String? {
        val window = jsonishWindow(html, id)
        return firstJsonString(window, "description", "body")
    }

    private fun extractSerializedDurationSeconds(
        html: String,
        id: String
    ): Long? {
        val window = jsonishWindow(html, id)
        firstJsonString(window, "durationText", "duration_text")
            ?.let { extractDurationSeconds(it) }
            ?.let { return it }
        firstJsonLong(window, "fileDuration", "file_duration", "mediaDuration", "media_duration")
            ?.let { return if (it > 86_400L) it / 1000L else it }
        val value = firstJsonLong(
            window,
            "durationSeconds",
            "duration_seconds",
            "duration",
            "length",
            "lengthSeconds"
        ) ?: return null
        return if (value > 86_400L) value / 1000L else value
    }

    private fun extractSerializedRating(
        html: String,
        id: String
    ): String? {
        val window = jsonishWindow(html, id)
        return firstJsonString(window, "rating", "contentRating", "content_rating")
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractSerializedVisibility(
        html: String,
        id: String
    ): String? {
        val window = jsonishWindow(html, id)
        return firstJsonString(window, "visibility", "privacy", "access")
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractSerializedDate(
        html: String,
        id: String,
        vararg keys: String
    ): String? {
        val window = jsonishWindow(html, id)
        return firstJsonString(window, *keys)
    }

    private fun extractSerializedInt(
        html: String,
        id: String,
        vararg keys: String
    ): Int? {
        val window = jsonishWindow(html, id)
        return firstJsonLong(window, *keys)?.toInt()
    }

    private fun extractSerializedAuthor(
        html: String,
        id: String
    ): SerializedAuthor {
        val window = jsonishWindow(html, id)
        val userObject = Regex(
            """"(?:user|author|owner|creator)"\s*:\s*\{(.{0,3000}?)\}""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(window)?.groupValues?.getOrNull(1)

        val source = userObject ?: window
        val username = firstJsonString(source, "username", "slug")
            ?.takeIf { it.isNotBlank() }
        val rawName = firstJsonString(source, "displayName", "display_name", "nickname", "name")
        val name = rawName
            ?.takeIf { candidate ->
                candidate.isNotBlank() &&
                    !candidate.equals(username, ignoreCase = true) &&
                    !candidate.matches(Regex("user\\d+", RegexOption.IGNORE_CASE))
            }
        val authorId = firstJsonString(source, "id", "userId", "user_id")
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { value ->
                username != null && value.equals(username, ignoreCase = true)
            }
        val avatarUrl = firstString(
            firstJsonString(
                source,
                "avatarUrl",
                "avatar_url",
                "imageUrl",
                "image_url",
                "thumbnailUrl",
                "thumbnail_url",
                "picture"
            ),
            extractAvatarAssetUrl(source),
            extractAvatarAssetUrl(window)
        )

        return SerializedAuthor(
            id = authorId,
            name = name,
            username = username,
            avatarUrl = avatarUrl
        )
    }

    private fun extractAvatarAssetUrl(
        source: String
    ): String? {
        val avatarObject = Regex(
            """"avatar"\s*:\s*\{(.{0,2000}?)(?:\}\s*,|\}\s*\}|\})""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(source)?.groupValues?.getOrNull(1) ?: source

        firstJsonString(
            avatarObject,
            "url",
            "uri",
            "href",
            "src",
            "avatarUrl",
            "avatar_url",
            "imageUrl",
            "image_url",
            "thumbnailUrl",
            "thumbnail_url",
            "originalUrl",
            "original_url"
        )?.let { direct ->
            when {
                direct.startsWith("http://", ignoreCase = true) ||
                    direct.startsWith("https://", ignoreCase = true) -> return direct
                direct.startsWith("/image/", ignoreCase = true) -> return "https://i.iwara.tv$direct"
                direct.startsWith("image/", ignoreCase = true) -> return "https://i.iwara.tv/$direct"
            }
        }

        val assetId = firstJsonString(avatarObject, "id", "uuid", "fileId", "file_id")
        val assetName = firstJsonString(avatarObject, "name", "filename", "fileName", "file_name")
        if (!assetId.isNullOrBlank() && !assetName.isNullOrBlank()) {
            return "https://i.iwara.tv/image/avatar/${encodeUrlPath(assetId)}/${encodeUrlPath(assetName)}"
        }
        return null
    }

    private fun extractThumbnail(html: String): String? {
        return Regex("""https?://[^"']+\.(?:jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
            .find(html)
            ?.value
    }

    private fun extractTags(
        html: String,
        bodyText: String,
        id: String
    ): List<IwaraTagMeta> {
        val tags = linkedSetOf<IwaraTagMeta>()
        val normalizedHtml = normalizeJsonish(html)
        val scopedHtml = jsonishWindow(normalizedHtml, id)
        val sources = listOf(scopedHtml, normalizedHtml)

        sources.asSequence()
            .flatMap { source ->
                Regex("""/videos\?[^"'<>\s\\]*?(?:tags|tag)=([^"'&<>\s\\]+)""")
                    .findAll(source)
            }
            .map { it.groupValues[1] }
            .map { decodeUrlComponent(it) }
            .filter { it.length in 2..60 }
            .take(40)
            .forEach { tags += IwaraTagMeta("tag", it) }

        sources.asSequence()
            .flatMap { source ->
                Regex(
                    """"tags"\s*:\s*\[(.{0,6000}?)\]""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).findAll(source)
            }
            .flatMap { match ->
                Regex(
                    """"(?:name|slug|id)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""",
                    RegexOption.IGNORE_CASE
                ).findAll(match.groupValues[1])
            }
            .map { it.groupValues[1] }
            .map { decodeJsonString(it) }
            .filter { it.length in 2..60 && !it.startsWith("http", ignoreCase = true) }
            .take(40)
            .forEach { tags += IwaraTagMeta("tag", it) }

        Regex("""#([\p{L}\p{N}_-]{2,40})""")
            .findAll(bodyText)
            .take(20)
            .forEach { tags += IwaraTagMeta("tag", it.groupValues[1]) }

        return tags.toList()
    }

    private fun extractDurationSeconds(text: String): Long? {
        val iso = Regex(
            """\bPT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?\b""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (iso != null) {
            val hours = iso.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
            val minutes = iso.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
            val seconds = iso.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
            return hours * 3600L + minutes * 60L + seconds
        }

        val match = Regex("""\b(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b""")
            .find(text)
            ?: return null
        val hours = match.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
        val minutes = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
        val seconds = match.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
        return hours * 3600L + minutes * 60L + seconds
    }

    private fun extractRating(text: String): String? {
        return Regex(
            """\b(general|ecchi|r-?18|restricted|safe)\b""",
            RegexOption.IGNORE_CASE
        ).find(text)?.value
    }

    private fun extractVisibility(text: String): String? {
        return Regex(
            """\b(public|private|unlisted|friends|followers)\b""",
            RegexOption.IGNORE_CASE
        ).find(text)?.value
    }

    private fun jsonishWindow(
        html: String,
        id: String
    ): String {
        val normalized = normalizeJsonish(html)
        if (id.isBlank()) return normalized.take(16000)
        val index = normalized.indexOf(id, ignoreCase = true)
        if (index < 0) return normalized.take(16000)
        val start = (index - 12000).coerceAtLeast(0)
        val end = (index + 24000).coerceAtMost(normalized.length)
        return normalized.substring(start, end)
    }

    private fun normalizeJsonish(value: String): String {
        return value
            .replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u003F", "?", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("&amp;", "&")
    }

    private fun firstJsonString(
        source: String,
        vararg keys: String
    ): String? {
        keys.forEach { key ->
            Regex(
                """"${Regex.escape(key)}"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""",
                RegexOption.IGNORE_CASE
            ).find(source)?.groupValues?.getOrNull(1)
                ?.let { decodeJsonString(it) }
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?.let { return it }
        }
        return null
    }

    private fun firstJsonLong(
        source: String,
        vararg keys: String
    ): Long? {
        keys.forEach { key ->
            Regex(
                """"${Regex.escape(key)}"\s*:\s*(?:"([^"\\]*(?:\\.[^"\\]*)*)"|([0-9]+(?:\.[0-9]+)?))""",
                RegexOption.IGNORE_CASE
            ).find(source)?.let { match ->
                val quoted = match.groupValues.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { decodeJsonString(it) }
                val numeric = match.groupValues.getOrNull(2)
                    ?.takeIf { it.isNotBlank() }
                val raw = quoted ?: numeric
                raw?.toDoubleOrNull()?.toLong()?.let { return it }
                raw?.let { extractDurationSeconds(it) }?.let { return it }
            }
        }
        return null
    }

    private fun decodeJsonString(value: String): String {
        return runCatching {
            JSONArray("""["$value"]""").getString(0)
        }.getOrDefault(value)
    }

    private fun decodeUrlComponent(value: String): String {
        return runCatching {
            java.net.URLDecoder.decode(value, "UTF-8")
        }.getOrDefault(value)
    }

    private fun encodeUrlPath(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
    }
}
