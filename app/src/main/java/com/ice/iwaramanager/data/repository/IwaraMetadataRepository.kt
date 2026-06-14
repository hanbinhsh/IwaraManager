package com.ice.iwaramanager.data.repository

import android.content.Context
import com.ice.iwaramanager.data.model.IwaraSearchMetaResult
import com.ice.iwaramanager.data.model.IwaraTagMeta
import com.ice.iwaramanager.data.model.IwaraVideoMeta
import com.ice.iwaramanager.data.remote.IwaraSearchWebView
import org.json.JSONArray
import org.json.JSONObject

class IwaraMetadataRepository(
    context: Context
) {
    private val searcher = IwaraSearchWebView(context)

    suspend fun fetchById(
        id: String,
        timeoutMillis: Long
    ): IwaraSearchMetaResult {
        val raw = searcher.fetchById(id, timeoutMillis)
        return parseSearchResult(raw, id)
    }

    suspend fun searchTitle(
        title: String,
        timeoutMillis: Long
    ): IwaraSearchMetaResult {
        val raw = searcher.searchByTitle(title, timeoutMillis)
        return parseSearchResult(raw, title)
    }

    private fun parseSearchResult(
        raw: String,
        query: String
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

        val videos = candidateIds.map { id ->
            val candidateMeta = metaById[id]
            val detailMeta = if (usePageMetaForSingleCandidate) pageMeta else null
            val html = json.optString("html")
            val bodyText = json.optString("bodyText")
            val serializedAuthor = extractSerializedAuthor(html, id)
            val authorUsername = firstString(
                candidateMeta?.optString("authorUsername"),
                detailMeta?.optString("authorUsername"),
                serializedAuthor.username
            )
            val authorId = firstString(
                candidateMeta?.optString("authorId"),
                detailMeta?.optString("authorId"),
                serializedAuthor.id
            )?.takeUnless { value ->
                authorUsername != null && value.equals(authorUsername, ignoreCase = true)
            }
            val tags = tagsFromJson(candidateMeta, detailMeta).ifEmpty {
                if (usePageMetaForSingleCandidate) {
                    extractTags(html, bodyText, id)
                } else {
                    emptyList()
                }
            }
            IwaraVideoMeta(
                id = id,
                title = extractTitle(
                    json = json,
                    query = query,
                    id = id,
                    candidateMeta = candidateMeta,
                    detailMeta = detailMeta,
                    html = html,
                    allowQueryFallback = !isSearchPage && usePageMetaForSingleCandidate
                ),
                description = firstString(
                    candidateMeta?.optString("description"),
                    detailMeta?.optString("description"),
                    extractSerializedDescription(html, id)
                ),
                authorId = firstString(
                    authorId
                ),
                authorName = firstString(
                    candidateMeta?.optString("authorName"),
                    detailMeta?.optString("authorName"),
                    serializedAuthor.name
                ),
                authorUsername = firstString(
                    authorUsername
                ),
                thumbnailUrl = firstString(
                    candidateMeta?.optString("thumbnailUrl"),
                    detailMeta?.optString("thumbnailUrl"),
                    extractThumbnail(json.optString("html"))
                ),
                rating = firstString(
                    candidateMeta?.optString("rating"),
                    detailMeta?.optString("rating"),
                    extractSerializedRating(html, id),
                    extractRating(bodyText)
                ),
                visibility = firstString(
                    candidateMeta?.optString("visibility"),
                    detailMeta?.optString("visibility"),
                    extractSerializedVisibility(html, id),
                    extractVisibility(bodyText)
                ),
                createdAt = firstString(
                    candidateMeta?.optString("createdAt"),
                    detailMeta?.optString("createdAt"),
                    extractSerializedDate(
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
                    detailMeta?.optString("updatedAt"),
                    extractSerializedDate(html, id, "updatedAt", "updated_at", "modifiedAt", "modified_at")
                ),
                durationSeconds = firstLong(candidateMeta, detailMeta, "durationSeconds")
                    ?: extractSerializedDurationSeconds(html, id)
                    ?: extractDurationSeconds(bodyText),
                likeCount = firstInt(candidateMeta, detailMeta, "likeCount")
                    ?: extractSerializedInt(html, id, "likeCount", "like_count", "likes", "numLikes", "num_likes"),
                viewCount = firstInt(candidateMeta, detailMeta, "viewCount")
                    ?: extractSerializedInt(html, id, "viewCount", "view_count", "views", "numViews", "num_views"),
                commentCount = firstInt(candidateMeta, detailMeta, "commentCount")
                    ?: extractSerializedInt(html, id, "commentCount", "comment_count", "comments", "numComments", "num_comments"),
                tags = tags,
                rawJson = raw
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
            .put("search", json)
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

    private fun isBlockingFailure(json: JSONObject): Boolean {
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
                ?.takeIf { it.isNotBlank() && it != "null" }
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
        val username: String? = null
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

        return SerializedAuthor(
            id = authorId,
            name = name,
            username = username
        )
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
}
