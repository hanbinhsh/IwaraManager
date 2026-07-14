package com.ice.iwaramanager.data.remote

import android.util.Base64
import com.ice.iwaramanager.data.model.LibrarySource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

class WebDavClient {
    private val clientCache = ConcurrentHashMap<ClientKey, OkHttpClient>()

    // 缓存的 XML 解析工厂：禁用 DOCTYPE/外部实体（防 XXE），并避免每次 PROPFIND 重新做服务发现。
    private val documentBuilderFactory: DocumentBuilderFactory by lazy {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
        }
    }

    fun authHeaders(source: LibrarySource, password: String?): Map<String, String> {
        val username = source.webDavUsername?.trim().orEmpty()
        if (password.isNullOrBlank()) return emptyMap()
        val token = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return mapOf("Authorization" to "Basic $token")
    }

    fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.contains("://")) trimmed else "https://$trimmed"
    }

    fun validateSource(source: LibrarySource) {
        val base = normalizeBaseUrl(source.webDavBaseUrl.orEmpty())
        require(base.isNotBlank()) { "WebDAV 服务器地址不能为空" }
        val uri = URI(base)
        require(uri.scheme.equals("https", ignoreCase = true)) { "第一版 WebDAV 仅允许 HTTPS，请检查服务器地址" }
        require(!uri.host.isNullOrBlank()) { "WebDAV 服务器地址无效" }
    }

    fun testConnection(source: LibrarySource, password: String?): String {
        validateSource(source)
        val url = buildUrl(source, "")
        val entries = propfind(url, source, password)
        return "连接成功，当前目录 ${entries.size} 个条目"
    }

    fun list(source: LibrarySource, password: String?, path: String): List<WebDavEntry> {
        validateSource(source)
        return propfind(buildUrl(source, path), source, password)
    }

    fun listUrl(source: LibrarySource, password: String?, url: String): List<WebDavEntry> {
        validateSource(source)
        return propfind(url, source, password)
    }

    fun buildUrl(source: LibrarySource, path: String): String {
        val base = normalizeBaseUrl(source.webDavBaseUrl!!)
        val root = source.webDavRootPath.orEmpty().trim('/').takeIf { it.isNotBlank() }
        val rel = path.trim('/').takeIf { it.isNotBlank() }
        val combined = listOfNotNull(root, rel).joinToString("/")
        return if (combined.isBlank()) base else "$base/${combined.split('/').joinToString("/") { encodePathSegment(it) }}"
    }

    private fun propfind(url: String, source: LibrarySource, password: String?): List<WebDavEntry> {
        val client = buildClient(source)
        val requestBuilder = Request.Builder()
            .url(url)
            .method("PROPFIND", null)
            .header("Depth", "1")
            .header("Accept", "application/xml,text/xml,*/*")
            .header("User-Agent", "IwaraManager/1.0")
        authHeaders(source, password).forEach { (key, value) -> requestBuilder.header(key, value) }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                if (code == 401 || code == 403) {
                    error("WebDAV 认证失败或无权限（HTTP $code ${response.message}）")
                }
                if (code == 404) error(webDavHttpErrorMessage(source, url, code, response.message, response.body?.string()))
                if (code !in 200..299) error(webDavHttpErrorMessage(source, url, code, response.message, response.body?.string()))
                val bytes = response.body?.bytes() ?: error("WebDAV PROPFIND 成功但响应体为空")
                return parseMultistatus(bytes, url)
            }
        } catch (e: SocketTimeoutException) {
            error("WebDAV 连接或读取超时：${e.message ?: "timeout"}")
        } catch (e: SSLHandshakeException) {
            error("WebDAV HTTPS/证书错误：${e.message ?: e::class.java.simpleName}。如果这是路由器或 NAS 的自签证书，请在添加网络盘时开启“允许不受信任证书”。")
        } catch (e: javax.net.ssl.SSLException) {
            error("WebDAV HTTPS/证书错误：${e.message ?: e::class.java.simpleName}。如果这是路由器或 NAS 的自签证书，请在添加网络盘时开启“允许不受信任证书”。")
        } catch (e: IllegalArgumentException) {
            error("WebDAV 地址无效：${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun webDavHttpErrorMessage(
        source: LibrarySource,
        url: String,
        code: Int,
        message: String,
        body: String?
    ): String {
        val root = source.webDavRootPath.orEmpty().ifBlank { "/" }
        val bodyPreview = body
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(300)
            ?.takeIf { it.isNotBlank() }
        return buildString {
            if (code == 404) {
                append("WebDAV 路径不存在（HTTP 404）")
            } else {
                append("WebDAV PROPFIND 失败（HTTP $code")
                if (message.isNotBlank()) append(" $message")
                append("）")
            }
            append("\n请求URL：").append(url)
            append("\n服务器地址：").append(source.webDavBaseUrl.orEmpty())
            append("\n根路径：").append(root)
            append("\n请确认这个地址本身就是 WebDAV 入口，或把根路径改成服务实际路径，例如 /dav、/webdav、/remote.php/dav/files/用户名。")
            bodyPreview?.let { append("\n响应片段：").append(it) }
        }
    }

    private fun buildClient(source: LibrarySource): OkHttpClient {
        // 按超时与 TLS 配置缓存并复用 OkHttpClient，避免扫描大量目录时为每次 PROPFIND 新建客户端，
        // 从而复用连接池与线程池，显著降低扫描开销。
        val key = ClientKey(
            connectTimeoutSeconds = source.connectTimeoutSeconds,
            readTimeoutSeconds = source.readTimeoutSeconds,
            allowInsecureTls = source.webDavAllowInsecureTls
        )
        clientCache[key]?.let { return it }
        val builder = OkHttpClient.Builder()
            .connectTimeout(source.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(source.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout((source.connectTimeoutSeconds + source.readTimeoutSeconds).toLong(), TimeUnit.SECONDS)
        if (source.webDavAllowInsecureTls) {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build().also { clientCache[key] = it }
    }

    private data class ClientKey(
        val connectTimeoutSeconds: Int,
        val readTimeoutSeconds: Int,
        val allowInsecureTls: Boolean
    )

    private fun parseMultistatus(bytes: ByteArray, requestUrl: String): List<WebDavEntry> {
        val document = documentBuilderFactory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val requestPath = URI(requestUrl).path.trimEnd('/')
        val responses = document.getElementsByTagNameNS("*", "response")
        val result = mutableListOf<WebDavEntry>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val href = response.childText("href") ?: continue
            val hrefUri = runCatching { URI(href) }.getOrNull()
            val hrefPath = (hrefUri?.path ?: href).trimEnd('/')
            if (hrefPath == requestPath) continue
            val name = hrefPath.substringAfterLast('/').ifBlank { continue }
            val collection = response.getElementsByTagNameNS("*", "collection").length > 0
            val length = response.childText("getcontentlength")?.toLongOrNull() ?: 0L
            val modified = response.childText("getlastmodified")?.let(::parseHttpDate) ?: 0L
            val absolute = if (href.startsWith("http://") || href.startsWith("https://")) href else {
                val req = URI(requestUrl)
                URI(req.scheme, req.authority, hrefUri?.path ?: href, hrefUri?.query, null).toString()
            }
            result += WebDavEntry(
                name = decodeName(name),
                url = absolute,
                isDirectory = collection,
                size = length,
                lastModified = modified
            )
        }
        return result
    }

    private fun Element.childText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseHttpDate(value: String): Long? {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy"
        )
        for (format in formats) {
            val parsed = runCatching {
                SimpleDateFormat(format, Locale.US).parse(value)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun encodePathSegment(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun decodeName(value: String): String {
        return runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
    }
}

data class WebDavEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)
