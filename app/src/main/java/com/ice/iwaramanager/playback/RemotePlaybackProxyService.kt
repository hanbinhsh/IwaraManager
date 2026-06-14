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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
            "远程视频播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "为外部播放器提供临时网络视频代理"
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
            .setContentText("外部播放器播放 WebDAV 视频时需要保持此服务运行")
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
            idleTimeoutSeconds: Int
        ): Uri {
            val running = ensureServer(readTimeoutSeconds, idleTimeoutSeconds)
            val token = UUID.randomUUID().toString()
            requests[token] = ProxyRequest(remoteUrl, headers, mimeType, readTimeoutSeconds * 1000)
            val intent = Intent(context, RemotePlaybackProxyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return Uri.parse("http://127.0.0.1:${running.port}/play/$token")
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
    val readTimeoutMillis: Int
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
                    thread(name = "iwara-remote-proxy-client", isDaemon = true) { handle(socket) }
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
                writeSimple(output, 404, "text/plain", "代理请求已失效")
                return
            }
            proxyRemote(request, headers["range"], output)
        }
    }

    private fun proxyRemote(request: ProxyRequest, range: String?, output: BufferedOutputStream) {
        val connection = (URL(request.remoteUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = request.readTimeoutMillis.takeIf { it > 0 } ?: readTimeoutMillis
            setRequestProperty("User-Agent", "IwaraManager/1.0")
            setRequestProperty("Accept", "*/*")
            if (!range.isNullOrBlank()) setRequestProperty("Range", range)
            request.headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            val code = connection.responseCode
            val stream = runCatching { connection.inputStream }.getOrElse { connection.errorStream }
            if (stream == null) {
                writeSimple(output, code, "text/plain", "远程服务器没有返回数据（HTTP $code）")
                return
            }
            val statusText = if (code == HttpURLConnection.HTTP_PARTIAL) "Partial Content" else "OK"
            output.write("HTTP/1.1 $code $statusText\r\n".toByteArray())
            val contentType = connection.contentType ?: request.mimeType
            output.write("Content-Type: $contentType\r\n".toByteArray())
            connection.getHeaderField("Content-Length")?.let { output.write("Content-Length: $it\r\n".toByteArray()) }
            connection.getHeaderField("Content-Range")?.let { output.write("Content-Range: $it\r\n".toByteArray()) }
            output.write("Accept-Ranges: bytes\r\n".toByteArray())
            output.write("Connection: close\r\n\r\n".toByteArray())
            stream.use { it.copyTo(output) }
            output.flush()
        } catch (e: Exception) {
            writeSimple(output, 502, "text/plain", "远程播放代理错误：${e.message ?: e::class.java.simpleName}")
        } finally {
            connection.disconnect()
        }
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
