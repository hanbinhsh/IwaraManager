package com.ice.iwaramanager.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ice.iwaramanager.data.model.IwaraLoginStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

data class IwaraSessionSnapshot(
    val status: IwaraLoginStatus,
    val message: String,
    val checkedAt: Long = System.currentTimeMillis(),
    val cookiePresent: Boolean = false,
    val cookieNames: List<String> = emptyList()
)

class IwaraSessionManager(
    context: Context
) {
    private val appContext = context.applicationContext

    fun initializeCookies(webView: WebView? = null) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (webView != null) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
    }

    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    fun cookieSummary(): Pair<Boolean, List<String>> {
        val cookie = CookieManager.getInstance().getCookie(IWARA_ORIGIN).orEmpty()
        val names = cookie.split(';')
            .mapNotNull { part -> part.substringBefore('=', missingDelimiterValue = "").trim().takeIf { it.isNotBlank() } }
            .distinct()
            .take(24)
        return names.isNotEmpty() to names
    }

    fun clearSession(onComplete: () -> Unit = {}) {
        val manager = CookieManager.getInstance()
        val names = cookieSummary().second
        val targets = listOf(IWARA_ORIGIN, IWARA_API_ORIGIN)
        names.forEach { name ->
            targets.forEach { origin ->
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/")
                manager.setCookie(origin, "$name=; Max-Age=0; Domain=.iwara.tv; Path=/")
                manager.setCookie(origin, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                manager.setCookie(origin, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Domain=.iwara.tv; Path=/")
            }
        }
        manager.flush()
        onComplete()
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun checkLoginStatus(timeoutMillis: Long = 15_000L): IwaraSessionSnapshot = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(appContext)
            val handler = Handler(Looper.getMainLooper())
            var completed = false
            var pageStarted = false
            var pageFinished = false
            var lastError: String? = null

            fun finish(status: IwaraLoginStatus, message: String) {
                if (completed) return
                completed = true
                handler.removeCallbacksAndMessages(null)
                val (cookiePresent, cookieNames) = cookieSummary()
                flushCookies()
                webView.destroy()
                continuation.resume(
                    IwaraSessionSnapshot(
                        status = status,
                        message = message,
                        cookiePresent = cookiePresent,
                        cookieNames = cookieNames
                    )
                )
            }

            fun evaluate() {
                if (completed) return
                webView.evaluateJavascript(
                    """
                    (function() {
                      var title = document.title || '';
                      var text = document.body ? document.body.innerText : '';
                      var html = document.documentElement ? document.documentElement.outerHTML : '';
                      var href = location.href || '';
                      function tokenFromValue(value, depth) {
                        if (value == null || depth > 4) return '';
                        if (typeof value === 'string') {
                          var raw = value.trim().replace(/^"|"$/g, '');
                          var bearer = raw.match(/Bearer\s+([A-Za-z0-9_\-.]+\.[A-Za-z0-9_\-.]+(?:\.[A-Za-z0-9_\-.]+)?)/i);
                          if (bearer) return bearer[0];
                          if (/^[A-Za-z0-9_\-.]+\.[A-Za-z0-9_\-.]+(?:\.[A-Za-z0-9_\-.]+)?$/.test(raw)) return raw;
                          if ((raw.charAt(0) === '{' || raw.charAt(0) === '[') && raw.length < 20000) {
                            try { return tokenFromValue(JSON.parse(raw), depth + 1); } catch (e) {}
                          }
                          return '';
                        }
                        if (Array.isArray(value)) {
                          for (var ai = 0; ai < value.length && ai < 40; ai++) {
                            var arrayToken = tokenFromValue(value[ai], depth + 1);
                            if (arrayToken) return arrayToken;
                          }
                          return '';
                        }
                        if (typeof value === 'object') {
                          var keys = Object.keys(value);
                          for (var oi = 0; oi < keys.length && oi < 80; oi++) {
                            var objectToken = tokenFromValue(value[keys[oi]], depth + 1);
                            if (objectToken) return objectToken;
                          }
                        }
                        return '';
                      }
                      function hasStoredAuthToken() {
                        var stores = [];
                        try { stores.push(localStorage); } catch (e) {}
                        try { stores.push(sessionStorage); } catch (e) {}
                        for (var si = 0; si < stores.length; si++) {
                          for (var ki = 0; ki < stores[si].length && ki < 120; ki++) {
                            var key = stores[si].key(ki);
                            if (!key) continue;
                            var value = '';
                            try { value = stores[si].getItem(key); } catch (e) { value = ''; }
                            if (tokenFromValue(value, 0)) return true;
                          }
                        }
                        return false;
                      }
                      var authTokenPresent = hasStoredAuthToken();
                      var loginSignals = /login|sign in|log in/i.test(title + ' ' + href) ||
                        /username|email|password|forgot password|sign in|log in/i.test(text);
                      var accountSignals = /logout|log out|settings|profile|notifications|subscriptions|following/i.test(text + ' ' + html);
                      accountSignals = accountSignals || authTokenPresent;
                      var cloudflare = /enable javascript and cookies|just a moment/i.test(text + ' ' + title) || /cf_chl/i.test(html);
                      var error = /error\s*\|\s*iwara/i.test(title) || /404|not found/i.test(text + ' ' + title);
                      return JSON.stringify({
                        title: title,
                        href: href,
                        loginSignals: loginSignals,
                        accountSignals: accountSignals,
                        authTokenPresent: authTokenPresent,
                        cloudflare: cloudflare,
                        error: error,
                        textLength: text.length,
                        htmlLength: html.length
                      });
                    })();
                    """.trimIndent()
                ) { raw ->
                    val value = raw.orEmpty()
                    val decoded = runCatching { JSONTokener(value).nextValue() as? String }
                        .getOrNull()
                        ?: value
                    val obj = runCatching { JSONObject(decoded) }.getOrNull() ?: JSONObject()
                    val title = obj.optString("title")
                    val loginSignals = obj.optBoolean("loginSignals")
                    val accountSignals = obj.optBoolean("accountSignals")
                    val cloudflare = obj.optBoolean("cloudflare")
                    val error = obj.optBoolean("error")
                    val (cookiePresent, cookieNames) = cookieSummary()
                    when {
                        accountSignals -> finish(IwaraLoginStatus.LoggedIn, "已检测到 Iwara 登录状态")
                        loginSignals && cookiePresent -> finish(IwaraLoginStatus.Expired, "检测到 Cookie，但页面仍要求登录，登录可能已过期")
                        loginSignals -> finish(IwaraLoginStatus.LoggedOut, "当前未登录 Iwara")
                        cloudflare -> finish(IwaraLoginStatus.Error, "Iwara 返回 Cloudflare/验证页面，暂时无法确认登录状态")
                        error && cookiePresent -> finish(IwaraLoginStatus.Expired, "Iwara 返回错误页面，登录可能已过期或账号无权限")
                        error -> finish(IwaraLoginStatus.LoggedOut, "Iwara 返回错误页面，且未检测到登录 Cookie")
                        cookiePresent -> finish(IwaraLoginStatus.LoggedIn, "检测到 Iwara Cookie：${cookieNames.joinToString(", ")}")
                        pageFinished -> finish(IwaraLoginStatus.LoggedOut, "未检测到 Iwara 登录 Cookie；页面标题：$title")
                        else -> handler.postDelayed({ evaluate() }, 1000L)
                    }
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                blockNetworkImage = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = USER_AGENT
            }
            initializeCookies(webView)
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    pageStarted = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageFinished = true
                    handler.postDelayed({ evaluate() }, 1200L)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        lastError = error?.description?.toString()
                        finish(IwaraLoginStatus.Error, "登录状态检查失败：${lastError ?: "页面加载错误"}")
                    }
                }
            }

            handler.postDelayed({
                val (cookiePresent, cookieNames) = cookieSummary()
                val message = buildString {
                    append("登录状态检查超时")
                    append("；页面开始:$pageStarted")
                    append("；页面完成:$pageFinished")
                    if (lastError != null) append("；错误:$lastError")
                    if (cookiePresent) append("；Cookie:${cookieNames.joinToString(", ")}")
                }
                finish(if (cookiePresent) IwaraLoginStatus.Expired else IwaraLoginStatus.Error, message)
            }, timeoutMillis)
            webView.loadUrl(STATUS_URL)

            continuation.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                webView.destroy()
            }
        }
    }

    companion object {
        const val IWARA_ORIGIN = "https://www.iwara.tv"
        const val IWARA_API_ORIGIN = "https://api.iwara.tv"
        const val LOGIN_URL = "https://www.iwara.tv/login"
        const val STATUS_URL = "https://www.iwara.tv"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36"
    }
}
