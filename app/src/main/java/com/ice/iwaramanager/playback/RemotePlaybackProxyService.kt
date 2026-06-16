package com.ice.iwaramanager.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ice.iwaramanager.MainActivity
import com.ice.iwaramanager.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

class RemotePlaybackProxyService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "远程视频代理",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "为应用内和外部播放器提供临时网络视频代理"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Iwara Manager 正在代理远程视频")
            .setContentText("播放或完整索引 WebDAV 视频时需要保持此服务运行")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "remote_playback_proxy"
        private const val NOTIFICATION_ID = 4042
        private val requests = ConcurrentHashMap<String, ProxyRequest>()
        @Volatile private var server: ProxyServer? = null

        fun createProxyUri(
            context: Context,
            remoteUrl: String,
            headers: Map<String, String>,
            mimeType: String,
            readTimeoutSeconds: Int,
            idleTimeoutSeconds: Int,
            allowInsecureTls: Boolean = false
        ): Uri {
            val running = ensureServer(readTimeoutSeconds, idleTimeoutSeconds)
            val token = UUID.randomUUID().toString()
            requests[token] = ProxyRequest(remoteUrl, headers, mimeType, readTimeoutSeconds * 1000, allowInsecureTls)
            val intent = Intent(context, RemotePlaybackProxyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return Uri.parse("http://localhost:${running.port}/play/$token")
        }

        private fun ensureServer(readTimeoutSeconds: Int, idleTimeoutSeconds: Int): ProxyServer {
            server?.let { if (it.isRunning) return it }
            return synchronized(this) {
                server?.let { if (it.isRunning) return@synchronized it }
                ProxyServer(
                    requests = requests,
                    readTimeoutMillis = readTimeoutSeconds * 1000,
                    idleTimeoutMillis = idleTimeoutSeconds * 1000L
                ).also {
                    it.start()
                    server = it
                }
            }
        }
    }
}

data class ProxyRequest(
    val remoteUrl: String,
    val headers: Map<String, String>,
    val mimeType: String,
    val readTimeoutMillis: Int,
    val allowInsecureTls: Boolean
)

private class ProxyServer(
    private val requests: ConcurrentHashMap<String, ProxyRequest>,
    private val readTimeoutMillis: Int,
    private val idleTimeoutMillis: Long
) {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    @Volatile var isRunning: Boolean = false
        private set
    @Volatile private var lastAccess: Long = System.currentTimeMillis()
    val port: Int get() = serverSocket.localPort

    fun start() {
        isRunning = true
        thread(name = "iwara-remote-proxy", isDaemon = true) {
            while (isRunning) {
                try {
                    val socket = serverSocket.accept()
                    lastAccess = System.currentTimeMillis()
                    thread(name = "iwara-remote-proxy-client", isDaemon = true) {
                        runCatching { handle(socket) }
                        runCatching { socket.close() }
                    }
                } catch (_: Exception) {
                    isRunning = false
                }
                if (System.currentTimeMillis() - lastAccess > idleTimeoutMillis) {
                    stop()
                }
            }
        }
    }

    private fun stop() {
        isRunning = false
        runCatching { serverSocket.close() }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val requestLine = readLine(input) ?: return
            val path = requestLine.split(' ').getOrNull(1).orEmpty()
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isBlank()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val token = path.substringAfter("/play/", "").substringBefore('?')
            val request = requests[token]
            if (request == null) {
                safeWriteSimple(output, 404, "text/plain", "代理请求已失效")
                return
            }
            proxyRemote(request, headers["range"], output)
        }
    }

    private fun proxyRemote(request: ProxyRequest, range: String?, output: BufferedOutputStream) {
        val client = buildClient(request)
        val requestBuilder = Request.Builder()
            .url(request.remoteUrl)
            .get()
            .header("User-Agent", "IwaraManager/1.0")
            .header("Accept", "*/*")
        if (!range.isNullOrBlank()) requestBuilder.header("Range", range)
        request.headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                val body = response.body
                if (body == null) {
                    safeWriteSimple(output, code, "text/plain", "远程服务器没有返回数据（HTTP $code）")
                    return
                }
                val statusText = if (code == 206) "Partial Content" else response.message.ifBlank { "OK" }
                output.write("HTTP/1.1 $code $statusText\r\n".toByteArray())
                val contentType = response.header("Content-Type") ?: request.mimeType
                output.write("Content-Type: $contentType\r\n".toByteArray())
                response.header("Content-Length")?.let { output.write("Content-Length: $it\r\n".toByteArray()) }
                response.header("Content-Range")?.let { output.write("Content-Range: $it\r\n".toByteArray()) }
                output.write("Accept-Ranges: bytes\r\n".toByteArray())
                output.write("Connection: close\r\n\r\n".toByteArray())
                body.byteStream().use { it.copyTo(output) }
                output.flush()
            }
        } catch (e: Exception) {
            safeWriteSimple(output, 502, "text/plain", "远程播放代理错误：${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun buildClient(request: ProxyRequest): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(request.readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(request.readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        if (request.allowInsecureTls) {
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
        return builder.build()
    }

    private fun writeSimple(output: BufferedOutputStream, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        output.write("HTTP/1.1 $code Error\r\n".toByteArray())
        output.write("Content-Type: $contentType; charset=utf-8\r\n".toByteArray())
        output.write("Content-Length: ${bytes.size}\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun safeWriteSimple(output: BufferedOutputStream, code: Int, contentType: String, body: String) {
        runCatching { writeSimple(output, code, contentType, body) }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        while (true) {
            val next = input.read()
            if (next == -1) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.UTF_8)
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes += next.toByte()
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
