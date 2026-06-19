package com.ice.iwaramanager.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ice.iwaramanager.data.model.IwaraMatchNetworkOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

class IwaraSearchWebView(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun fetchById(
        id: String,
        timeoutMillis: Long,
        options: IwaraMatchNetworkOptions = IwaraMatchNetworkOptions()
    ): String {
        return loadAndExtract(
            query = id,
            url = "https://www.iwara.tv/video/${Uri.encode(id)}",
            timeoutMillis = timeoutMillis,
            isSearch = false,
            options = options
        )
    }

    suspend fun searchByTitle(
        title: String,
        timeoutMillis: Long,
        options: IwaraMatchNetworkOptions = IwaraMatchNetworkOptions()
    ): String {
        val encoded = Uri.encode(title)
        return loadAndExtract(
            query = title,
            url = "https://www.iwara.tv/search?query=$encoded",
            timeoutMillis = timeoutMillis,
            isSearch = true,
            options = options
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadAndExtract(
        query: String,
        url: String,
        timeoutMillis: Long,
        isSearch: Boolean,
        options: IwaraMatchNetworkOptions
    ): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(appContext)
            val handler = Handler(Looper.getMainLooper())
            var pageStarted = false
            var pageFinished = false
            var lastError: String? = null
            var attempts = 0
            var completed = false
            val effectiveApiEndpointTemplates =
                options.apiEndpointTemplates.ifEmpty { IwaraMatchNetworkOptions().apiEndpointTemplates }
            val apiEndpointTemplatesJson = JSONArray(effectiveApiEndpointTemplates).toString()
            val apiRequestHeadersJson = JSONObject(options.apiRequestHeaders).toString()
            val apiProbeTimeoutMillis = options.apiProbeTimeoutMillis.coerceIn(5_000L, 120_000L)
            val allowPageFallback = options.allowPageFallback

            fun finish(timedOut: Boolean) {
                if (completed) return
                completed = true

                webView.evaluateJavascript(
                    """
                    (function() {
                      function cleanText(value) {
                        return (value || '')
                          .replace(/\s+/g, ' ')
                          .trim();
                      }
                      function usefulTitle(value) {
                        var text = cleanText(value);
                        if (!text) return '';
                        if (/^(search|loading|iwara|error|404|not found)$/i.test(text)) return '';
                        if (/^(views?|likes?|comments?|seconds?|minutes?)\b/i.test(text)) return '';
                        if (/^\d+([,.]\d+)?\s*(views?|likes?|comments?)$/i.test(text)) return '';
                        if (/\b\d+\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?|mo\.?|mos|months?|y|yr|yrs|years?)\s+ago\b/i.test(text)) return '';
                        if (/\bago\b/i.test(text) && /\b(\d+([,.]\d+)?k?|[0-9]{1,2}:[0-9]{2})\b/i.test(text)) return '';
                        if (/\b[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?\b/.test(text) &&
                            /\b(views?|likes?|comments?|\d+([,.]\d+)?k?)\b/i.test(text)) return '';
                        if (/^[0-9\s,.:kKmMoOyYago]+$/.test(text)) return '';
                        return text.length > 160 ? text.slice(0, 160) : text;
                      }
                      function absoluteUrl(value) {
                        if (!value) return '';
                        try { return new URL(value, location.href).href; } catch (e) { return value || ''; }
                      }
                      function imageUrlFromAsset(value, preferredKind) {
                        preferredKind = preferredKind || 'original';
                        if (!value) return '';
                        if (Array.isArray(value)) {
                          for (var ai = 0; ai < value.length; ai++) {
                            var arrayUrl = imageUrlFromAsset(value[ai], preferredKind);
                            if (arrayUrl) return arrayUrl;
                          }
                          return '';
                        }
                        if (typeof value === 'string') {
                          var text = cleanText(value);
                          if (!text) return '';
                          if (/^\/image\//i.test(text)) return 'https://i.iwara.tv' + text;
                          if (/^image\//i.test(text)) return 'https://i.iwara.tv/' + text;
                          if (/^https?:\/\//i.test(text)) return absoluteUrl(text);
                          return '';
                        }
                        if (typeof value !== 'object') return '';
                        var direct = firstTextValue(value, [
                          'url', 'uri', 'href', 'src',
                          'avatarUrl', 'avatar_url', 'imageUrl', 'image_url',
                          'thumbnailUrl', 'thumbnail_url', 'originalUrl', 'original_url'
                        ]);
                        if (direct) {
                          if (/^\/image\//i.test(direct)) return 'https://i.iwara.tv' + direct;
                          if (/^image\//i.test(direct)) return 'https://i.iwara.tv/' + direct;
                          if (/^https?:\/\//i.test(direct)) return absoluteUrl(direct);
                        }
                        if (value.file && typeof value.file === 'object') {
                          var fileUrl = imageUrlFromAsset(value.file, preferredKind);
                          if (fileUrl) return fileUrl;
                        }
                        if (Array.isArray(value.files) && value.files.length) {
                          var filesUrl = imageUrlFromAsset(value.files[0], preferredKind);
                          if (filesUrl) return filesUrl;
                        }
                        var nestedUrl = imageUrlFromAsset(value.thumbnail, preferredKind) ||
                          imageUrlFromAsset(value.original, preferredKind) ||
                          imageUrlFromAsset(value.image, preferredKind) ||
                          imageUrlFromAsset(value.icon, preferredKind);
                        if (nestedUrl) return nestedUrl;
                        var id = firstTextValue(value, ['id', 'uuid', 'fileId', 'file_id']);
                        var name = firstTextValue(value, ['name', 'filename', 'fileName', 'file_name']);
                        if (id && name) {
                          return 'https://i.iwara.tv/image/' + encodeURIComponent(preferredKind) + '/' +
                            encodeURIComponent(String(id)) + '/' +
                            encodeURIComponent(String(name));
                        }
                        return '';
                      }
                      function thumbnailUrlFromVideo(obj) {
                        if (!obj || typeof obj !== 'object') return '';
                        var direct = imageUrlFromAsset(firstObjectValue(obj, [
                          'thumbnailUrl', 'thumbnail_url', 'preview', 'poster'
                        ]), 'thumbnail');
                        if (direct) return direct;
                        var custom = imageUrlFromAsset(obj.customThumbnail, 'thumbnail');
                        if (custom) return custom;
                        var thumbnailIndex = firstObjectValue(obj, ['thumbnail']);
                        var file = obj.file && typeof obj.file === 'object' ? obj.file :
                          (Array.isArray(obj.files) && obj.files.length ? obj.files[0] : null);
                        var fileId = file ? firstTextValue(file, ['id', 'uuid', 'fileId', 'file_id']) : '';
                        if (fileId && (typeof thumbnailIndex === 'number' || /^\d+$/.test(String(thumbnailIndex || '')))) {
                          return 'https://i.iwara.tv/image/thumbnail/' +
                            encodeURIComponent(fileId) + '/thumbnail-' +
                            encodeURIComponent(String(thumbnailIndex)) + '.jpg';
                        }
                        return imageUrlFromAsset(obj.image, 'thumbnail') ||
                          imageUrlFromAsset(obj.thumbnail, 'thumbnail');
                      }
                      function metaContent(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                          var node = document.querySelector(selectors[i]);
                          var value = node ? (node.getAttribute('content') || node.getAttribute('value') || '') : '';
                          if (cleanText(value)) return cleanText(value);
                        }
                        return '';
                      }
                      function firstUseful(root, selectors) {
                        root = root || document;
                        for (var i = 0; i < selectors.length; i++) {
                          var node = root.querySelector(selectors[i]);
                          if (!node) continue;
                          var value = usefulTitle(node.getAttribute('title')) ||
                            usefulTitle(node.getAttribute('aria-label')) ||
                            usefulTitle(node.innerText) ||
                            usefulTitle(node.textContent);
                          if (value) return value;
                        }
                        return '';
                      }
                      function usernameFromHref(href) {
                        var match = (href || '').match(/\/(?:profile|user|users|channel|channels)\/([^/?#]+)/i);
                        return match ? decodeURIComponent(match[1]) : '';
                      }
                      function cleanAuthorName(value, username) {
                        var text = cleanText(value);
                        if (!text) return '';
                        if (username) {
                          text = text
                            .replace(new RegExp('@?' + username.replace(/[.*+?^${'$'}{}()|[\]\\]/g, '\\$&'), 'ig'), ' ')
                            .replace(/\s+/g, ' ')
                            .trim();
                        }
                        text = text.replace(/^(by|author|creator|profile)\s*[:：]?\s*/i, '').trim();
                        return text && !/^user\d+$/i.test(text) ? text : '';
                      }
                      function authorFrom(root) {
                        root = root || document;
                        var links = Array.prototype.slice.call(root.querySelectorAll('a[href]'));
                        var best = {};
                        for (var i = 0; i < links.length; i++) {
                          var href = links[i].href || links[i].getAttribute('href') || '';
                          var username = usernameFromHref(href);
                          if (!username) continue;
                          var raw = links[i].innerText || links[i].textContent || links[i].getAttribute('title') || links[i].getAttribute('aria-label') || '';
                          var image = links[i].querySelector('img[alt]');
                          if (image && image.getAttribute('alt')) raw = image.getAttribute('alt') + ' ' + raw;
                          var imageUrl = image ? (
                            image.currentSrc ||
                            image.getAttribute('src') ||
                            image.getAttribute('data-src') ||
                            (image.getAttribute('srcset') || '').split(',')[0].trim().split(/\s+/)[0]
                          ) : '';
                          var name = cleanAuthorName(raw, username);
                          if (!name) {
                            var parent = links[i].closest('[class*="author"], [class*="user"], [class*="profile"], [class*="owner"], [class*="creator"]');
                            if (parent) {
                              name = cleanAuthorName(parent.innerText || parent.textContent || '', username);
                              if (!imageUrl) {
                                var parentImage = parent.querySelector('img[src], img[data-src], img[srcset]');
                                if (parentImage) {
                                  imageUrl = parentImage.currentSrc ||
                                    parentImage.getAttribute('src') ||
                                    parentImage.getAttribute('data-src') ||
                                    (parentImage.getAttribute('srcset') || '').split(',')[0].trim().split(/\s+/)[0];
                                }
                              }
                            }
                          }
                          var candidate = {
                            authorName: name,
                            authorUsername: username,
                            authorId: '',
                            authorAvatarUrl: absoluteUrl(imageUrl)
                          };
                          if (candidate.authorName) return candidate;
                          if (!best.authorUsername) best = candidate;
                        }
                        if (best.authorUsername) {
                          return {
                            authorName: '',
                            authorUsername: best.authorUsername,
                            authorId: best.authorId,
                            authorAvatarUrl: best.authorAvatarUrl || ''
                          };
                        }
                        var text = root.innerText || root.textContent || '';
                        var by = text.match(/(?:by|author|creator)\s*[:：]?\s*([^\n\r|·]+)/i);
                        return by ? { authorName: cleanText(by[1]), authorUsername: '', authorId: '', authorAvatarUrl: '' } : {};
                      }
                      function thumbFrom(root, allowDocumentFallback) {
                        root = root || document;
                        if (allowDocumentFallback) {
                          var imageMeta = metaContent([
                            'meta[property="og:image"]',
                            'meta[name="twitter:image"]',
                            'meta[property="twitter:image"]'
                          ]);
                          if (imageMeta) return absoluteUrl(imageMeta);
                        }
                        var img = root.querySelector('img[src], img[data-src], img[srcset]');
                        if (!img) return '';
                        var value = img.currentSrc ||
                          img.getAttribute('src') ||
                          img.getAttribute('data-src') ||
                          (img.getAttribute('srcset') || '').split(',')[0].trim().split(/\s+/)[0];
                        return absoluteUrl(value);
                      }
                      function readTags(root) {
                        root = root || document;
                        var seen = {};
                        var result = [];
                        function add(namespace, name) {
                          name = cleanText(name).replace(/^#/, '');
                          namespace = cleanText(namespace) || 'tag';
                          if (!name || name.length < 2 || name.length > 60) return;
                          if (/^(search|video|videos|profile|more|tag)$/i.test(name)) return;
                          var key = (namespace + ':' + name).toLowerCase();
                          if (seen[key]) return;
                          seen[key] = true;
                          result.push({ namespace: namespace, name: name });
                        }
                        var links = Array.prototype.slice.call(root.querySelectorAll('a[href]'));
                        for (var i = 0; i < links.length; i++) {
                          var href = links[i].href || links[i].getAttribute('href') || '';
                          var text = cleanText(links[i].innerText || links[i].textContent || links[i].getAttribute('title') || '');
                          if (/\/videos\?/i.test(href)) {
                            try {
                              var parsedVideos = new URL(href, location.href);
                              var tagsValues = parsedVideos.searchParams.getAll('tags');
                              if (!tagsValues.length && parsedVideos.searchParams.get('tag')) {
                                tagsValues = [parsedVideos.searchParams.get('tag')];
                              }
                              for (var tv = 0; tv < tagsValues.length; tv++) {
                                add('tag', tagsValues[tv] || text);
                              }
                            } catch (e) {
                              add('tag', text);
                            }
                          } else if (/\/search\?/i.test(href)) {
                            try {
                              var parsed = new URL(href, location.href);
                              var query = parsed.searchParams.get('query') ||
                                parsed.searchParams.get('tag') ||
                                parsed.searchParams.get('tags') ||
                                text;
                              add('tag', query || text);
                            } catch (e) {
                              add('tag', text);
                            }
                          } else if (/\/(?:tag|tags|category|categories)\//i.test(href)) {
                            var namespace = /\/categor/i.test(href) ? 'category' : 'tag';
                            add(namespace, text || href.split('/').pop());
                          }
                        }
                        var hashText = root.innerText || root.textContent || '';
                        var match = null;
                        var pattern = /#([\p{L}\p{N}_-]{2,60})/gu;
                        while ((match = pattern.exec(hashText)) !== null && result.length < 40) {
                          add('tag', match[1]);
                        }
                        return result.slice(0, 40);
                      }
                      function parseNumber(text, words) {
                        var re = new RegExp('([0-9][0-9,.]*)\\s*(' + words + ')', 'i');
                        var m = (text || '').match(re);
                        if (!m) return null;
                        var n = parseInt(m[1].replace(/[,\.]/g, ''), 10);
                        return isNaN(n) ? null : n;
                      }
                      function parseDurationSeconds(text) {
                        var source = text || '';
                        var iso = source.match(/\bPT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?\b/i);
                        if (iso) {
                          return parseInt(iso[1] || '0', 10) * 3600 +
                            parseInt(iso[2] || '0', 10) * 60 +
                            parseInt(iso[3] || '0', 10);
                        }
                        var m = source.match(/\b(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b/);
                        if (!m) return null;
                        var h = parseInt(m[1] || '0', 10);
                        var min = parseInt(m[2] || '0', 10);
                        var sec = parseInt(m[3] || '0', 10);
                        return h * 3600 + min * 60 + sec;
                      }
                      function durationFrom(root) {
                        root = root || document;
                        var metaDuration = metaContent([
                          'meta[property="video:duration"]',
                          'meta[name="duration"]',
                          'meta[itemprop="duration"]'
                        ]);
                        var parsedMeta = parseDurationSeconds(metaDuration);
                        if (parsedMeta != null) return parsedMeta;
                        var nodes = root.querySelectorAll('[class*="duration"], [data-testid*="duration"], time, span, div');
                        for (var i = 0; i < Math.min(nodes.length, 80); i++) {
                          var parsed = parseDurationSeconds(nodes[i].getAttribute('datetime') || nodes[i].innerText || nodes[i].textContent || '');
                          if (parsed != null) return parsed;
                        }
                        return null;
                      }
                      function visibilityFromText(text) {
                        var match = (text || '').match(/\b(public|private|unlisted|friends|followers)\b/i);
                        return match ? match[1] : '';
                      }
                      function statsFrom(root) {
                        root = root || document;
                        var text = root.innerText || root.textContent || '';
                        var time = root.querySelector('time[datetime]');
                        var createdAt = time ? cleanText(time.getAttribute('datetime') || time.innerText || '') : '';
                        var rating = '';
                        var ratingMatch = text.match(/\b(general|ecchi|r-?18|restricted|safe)\b/i);
                        if (ratingMatch) rating = ratingMatch[1];
                        return {
                          rating: rating,
                          visibility: visibilityFromText(text),
                          createdAt: createdAt,
                          updatedAt: '',
                          durationSeconds: durationFrom(root) || parseDurationSeconds(text),
                          likeCount: parseNumber(text, 'likes?|赞'),
                          viewCount: parseNumber(text, 'views?|观看|播放'),
                          commentCount: parseNumber(text, 'comments?|评论')
                        };
                      }
                      function metaFrom(root, allowDocumentFallback) {
                        root = root || document;
                        var author = authorFrom(root);
                        var stats = statsFrom(root);
                        var ogTitle = allowDocumentFallback ?
                          usefulTitle(metaContent(['meta[property="og:title"]', 'meta[name="twitter:title"]'])) :
                          '';
                        var headingTitle = firstUseful(root, ['h1', 'h2', 'h3', '[data-testid*="title"]']);
                        var documentTitle = allowDocumentFallback ?
                          usefulTitle(document.title.replace(/\s*\|\s*Iwara\s*$/i, '')) :
                          '';
                        var descriptionMeta = allowDocumentFallback ?
                          cleanText(metaContent(['meta[property="og:description"]', 'meta[name="description"]', 'meta[name="twitter:description"]'])) :
                          '';
                        return {
                          title: ogTitle || headingTitle || documentTitle,
                          description: descriptionMeta ||
                            cleanText((root.querySelector('[class*="description"], [data-testid*="description"]') || {}).innerText || ''),
                          authorId: author.authorId || '',
                          authorName: author.authorName || '',
                          authorUsername: author.authorUsername || '',
                          authorAvatarUrl: author.authorAvatarUrl || '',
                          thumbnailUrl: thumbFrom(root, allowDocumentFallback),
                          rating: stats.rating || '',
                          visibility: stats.visibility || '',
                          createdAt: stats.createdAt || '',
                          updatedAt: stats.updatedAt || '',
                          durationSeconds: stats.durationSeconds,
                          likeCount: stats.likeCount,
                          viewCount: stats.viewCount,
                          commentCount: stats.commentCount,
                          tags: readTags(root)
                        };
                      }
                      function currentVideoId() {
                        var match = (location.pathname || '').match(/\/video\/([^/?#]+)/i);
                        return match ? decodeURIComponent(match[1]) : '';
                      }
                      function firstObjectValue(obj, keys) {
                        if (!obj || typeof obj !== 'object') return null;
                        for (var i = 0; i < keys.length; i++) {
                          var value = obj[keys[i]];
                          if (value !== undefined && value !== null && value !== '') return value;
                        }
                        return null;
                      }
                      function firstTextValue(obj, keys) {
                        var value = firstObjectValue(obj, keys);
                        if (value === undefined || value === null) return '';
                        if (typeof value === 'object') return '';
                        return cleanText(String(value));
                      }
                      function numberFromValue(value) {
                        if (value === undefined || value === null || value === '') return null;
                        if (typeof value === 'number') return isFinite(value) ? value : null;
                        var text = cleanText(String(value));
                        if (!text) return null;
                        var duration = parseDurationSeconds(text);
                        if (duration != null) return duration;
                        var number = Number(text.replace(/,/g, ''));
                        return isFinite(number) ? number : null;
                      }
                      function durationValue(obj) {
                        var value = null;
                        if (obj.file) {
                          value = firstObjectValue(obj.file, [
                            'durationSeconds', 'duration_seconds', 'duration',
                            'lengthSeconds', 'length_seconds', 'length',
                            'durationText', 'duration_text'
                          ]);
                          if (value == null && obj.file.metadata) {
                            value = firstObjectValue(obj.file.metadata, [
                              'durationSeconds', 'duration_seconds', 'duration',
                              'lengthSeconds', 'length_seconds', 'length'
                            ]);
                          }
                        }
                        if (value == null && Array.isArray(obj.files)) {
                          for (var f = 0; f < obj.files.length && value == null; f++) {
                            value = firstObjectValue(obj.files[f], [
                              'durationSeconds', 'duration_seconds', 'duration',
                              'lengthSeconds', 'length_seconds', 'length',
                              'durationText', 'duration_text'
                            ]);
                          }
                        }
                        if (value == null && obj.video) {
                          value = firstObjectValue(obj.video, [
                            'durationSeconds', 'duration_seconds', 'duration',
                            'lengthSeconds', 'length_seconds', 'length',
                            'durationText', 'duration_text'
                          ]);
                        }
                        if (value == null) {
                          value = firstObjectValue(obj, [
                            'durationSeconds', 'duration_seconds',
                            'lengthSeconds', 'length_seconds', 'length',
                            'durationText', 'duration_text',
                            'duration'
                          ]);
                        }
                        var parsed = numberFromValue(value);
                        if (parsed == null) return null;
                        return parsed > 86400 ? Math.round(parsed / 1000) : Math.round(parsed);
                      }
                      function countValue(obj, keys) {
                        var value = firstObjectValue(obj, keys);
                        if (value == null && obj.stats) value = firstObjectValue(obj.stats, keys);
                        if (value == null && obj.counts) value = firstObjectValue(obj.counts, keys);
                        var parsed = numberFromValue(value);
                        return parsed == null ? null : Math.round(parsed);
                      }
                      function dateValue(obj, keys) {
                        var value = firstTextValue(obj, keys);
                        if (value) return value;
                        if (obj.timestamps) return firstTextValue(obj.timestamps, keys);
                        return '';
                      }
                      function readStructuredTags(obj) {
                        var seen = {};
                        var result = [];
                        function add(namespace, name) {
                          namespace = cleanText(namespace) || 'tag';
                          name = cleanText(name).replace(/^#/, '');
                          if (!name || name.length < 2 || name.length > 80) return;
                          var key = (namespace + ':' + name).toLowerCase();
                          if (seen[key]) return;
                          seen[key] = true;
                          result.push({ namespace: namespace, name: name });
                        }
                        function readArray(value, namespace) {
                          if (!Array.isArray(value)) return;
                          for (var i = 0; i < value.length && result.length < 60; i++) {
                            var item = value[i];
                            if (typeof item === 'string') {
                              add(namespace || 'tag', item);
                          } else if (item && typeof item === 'object') {
                              var itemNamespace = item.namespace || item.type || namespace || 'tag';
                              if (/^general$/i.test(itemNamespace)) itemNamespace = 'tag';
                              add(
                                itemNamespace,
                                item.name || item.slug || item.id || item.label || item.title || ''
                              );
                            }
                          }
                        }
                        readArray(obj.tags, 'tag');
                        readArray(obj.tagList, 'tag');
                        readArray(obj.categories, 'category');
                        readArray(obj.category, 'category');
                        return result;
                      }
                      function readStructuredAuthor(obj) {
                        var user = obj.user || obj.author || obj.owner || obj.creator || {};
                        var username = firstTextValue(user, ['username', 'slug']);
                        var id = firstTextValue(user, ['id', 'userId', 'user_id', 'uuid']);
                        var name = firstTextValue(user, ['displayName', 'display_name', 'nickname', 'name']);
                        var avatar = user.avatar && typeof user.avatar === 'object' ? user.avatar : {};
                        var avatarUrl = imageUrlFromAsset(avatar, 'avatar') ||
                          imageUrlFromAsset(user.avatar, 'avatar') ||
                          imageUrlFromAsset(user.icon, 'avatar') ||
                          imageUrlFromAsset(user.image, 'avatar') ||
                          imageUrlFromAsset(user.picture, 'avatar') ||
                          firstTextValue(user, ['avatarUrl', 'avatar_url', 'picture', 'image', 'icon']);
                        if (!username && /^user\d+$/i.test(name)) {
                          username = name;
                          name = '';
                        }
                        if (name && username && name.toLowerCase() === username.toLowerCase()) {
                          name = '';
                        }
                        if (id && username && id.toLowerCase() === username.toLowerCase()) {
                          id = '';
                        }
                        return {
                          authorId: id,
                          authorName: name,
                          authorUsername: username,
                          authorAvatarUrl: imageUrlFromAsset(avatarUrl, 'avatar') || absoluteUrl(avatarUrl)
                        };
                      }
                      function structuredVideoFromObject(obj, expectedId) {
                        if (!obj || typeof obj !== 'object') return null;
                        var url = firstTextValue(obj, ['url', 'path', 'href']);
                        var urlId = '';
                        var urlMatch = url.match(/\/video\/([^/?#]+)/i);
                        if (urlMatch) urlId = decodeURIComponent(urlMatch[1]);
                        var rawId = firstTextValue(obj, ['id', 'uuid']);
                        var publicId = firstTextValue(obj, [
                          'slugId', 'slug_id', 'videoId', 'video_id',
                          'publicId', 'public_id', 'shortId', 'short_id'
                        ]) || urlId || rawId;
                        var id = expectedId && rawId === expectedId ? expectedId : publicId;
                        var title = usefulTitle(firstTextValue(obj, ['title', 'name', 'videoTitle', 'video_title']));
                        var expectedMatch = !expectedId ||
                          id === expectedId ||
                          rawId === expectedId ||
                          url.indexOf('/video/' + expectedId) >= 0;
                        if (!expectedMatch) return null;
                        var duration = durationValue(obj);
                        var tags = readStructuredTags(obj);
                        var author = readStructuredAuthor(obj);
                        var thumbnail = thumbnailUrlFromVideo(obj);
                        var videoUrl = url.indexOf('/video/') >= 0;
                        var hasVideoShape = videoUrl || duration != null || thumbnail ||
                          author.authorName || author.authorUsername ||
                          firstTextValue(obj, ['rating', 'contentRating', 'content_rating']) ||
                          dateValue(obj, ['createdAt', 'created_at', 'publishedAt', 'published_at', 'date']) ||
                          obj.file || obj.video;
                        var hasShape = id && (
                          (expectedId && id === expectedId && (title || hasVideoShape || tags.length)) ||
                          (title && hasVideoShape)
                        );
                        if (!hasShape) return null;
                        return {
                          id: id,
                          url: url || ('https://www.iwara.tv/video/' + id),
                          searchTitle: title,
                          title: title,
                          description: firstTextValue(obj, ['description', 'body']),
                          authorId: author.authorId || '',
                          authorName: author.authorName || '',
                          authorUsername: author.authorUsername || '',
                          authorAvatarUrl: author.authorAvatarUrl || '',
                          thumbnailUrl: thumbnail,
                          rating: firstTextValue(obj, ['rating', 'contentRating', 'content_rating']),
                          visibility: obj.private === true ? 'private' :
                            (obj.unlisted === true ? 'unlisted' :
                              (obj.private === false ? 'public' : firstTextValue(obj, ['visibility', 'privacy', 'access']))),
                          createdAt: dateValue(obj, ['createdAt', 'created_at', 'publishedAt', 'published_at', 'date']),
                          updatedAt: dateValue(obj, ['updatedAt', 'updated_at']),
                          durationSeconds: duration,
                          likeCount: countValue(obj, ['likeCount', 'like_count', 'likes', 'numLikes']),
                          viewCount: countValue(obj, ['viewCount', 'view_count', 'views', 'numViews']),
                          commentCount: countValue(obj, ['commentCount', 'comment_count', 'comments', 'numComments']),
                          tags: tags
                        };
                      }
                      function collectStructuredResults() {
                        var expectedId = currentVideoId();
                        var seenObjects = [];
                        var seenIds = {};
                        var results = [];
                        function seenObject(value) {
                          for (var i = 0; i < seenObjects.length; i++) if (seenObjects[i] === value) return true;
                          seenObjects.push(value);
                          return false;
                        }
                        function add(video) {
                          if (!video || !video.id || seenIds[video.id]) return;
                          seenIds[video.id] = true;
                          results.push(video);
                        }
                        function scan(value, depth) {
                          if (!value || depth > 8 || results.length >= 60) return;
                          if (typeof value !== 'object') return;
                          if (seenObject(value)) return;
                          if (Array.isArray(value)) {
                            for (var i = 0; i < value.length && i < 120; i++) scan(value[i], depth + 1);
                            return;
                          }
                          add(structuredVideoFromObject(value, expectedId));
                          var keys = Object.keys(value);
                          for (var k = 0; k < keys.length && k < 120; k++) {
                            var child = null;
                            try { child = value[keys[k]]; } catch (e) { child = null; }
                            scan(child, depth + 1);
                          }
                        }
                        var roots = [];
                        try { if (window.__iwaraManagerApiResults) roots.push(window.__iwaraManagerApiResults); } catch (e) {}
                        try { if (window.__NUXT__) roots.push(window.__NUXT__); } catch (e) {}
                        try { if (window.__NUXT_DATA__) roots.push(window.__NUXT_DATA__); } catch (e) {}
                        try { if (window.__NEXT_DATA__) roots.push(window.__NEXT_DATA__); } catch (e) {}
                        try { if (window.__INITIAL_STATE__) roots.push(window.__INITIAL_STATE__); } catch (e) {}
                        try { if (window.__APOLLO_STATE__) roots.push(window.__APOLLO_STATE__); } catch (e) {}
                        try { if (window.__PINIA__) roots.push(window.__PINIA__); } catch (e) {}
                        try { if (window.__pinia) roots.push(window.__pinia); } catch (e) {}
                        try {
                          if (window.${'$'}nuxt) {
                            if (window.${'$'}nuxt.payload) roots.push(window.${'$'}nuxt.payload);
                            if (window.${'$'}nuxt.${'$'}store && window.${'$'}nuxt.${'$'}store.state) roots.push(window.${'$'}nuxt.${'$'}store.state);
                          }
                        } catch (e) {}
                        var scripts = document.querySelectorAll('script[type="application/json"], script#__NUXT_DATA__');
                        for (var s = 0; s < scripts.length && s < 12; s++) {
                          try {
                            var text = scripts[s].textContent || '';
                            if (text.length > 0 && text.length < 4000000) roots.push(JSON.parse(text));
                          } catch (e) {}
                        }
                        for (var r = 0; r < roots.length; r++) scan(roots[r], 0);
                        return results;
                      }
                      function cardFromLink(link) {
                        var node = link;
                        var best = link;
                        var steps = 0;
                        while (node && node !== document.body && steps < 8) {
                          var text = cleanText(node.innerText || node.textContent || '');
                          var hasVideoLink = !!(node.querySelector && node.querySelector('a[href*="/video/"]'));
                          var hasImage = !!(node.querySelector && node.querySelector('img[src], img[data-src], img[srcset]'));
                          var hasDuration = parseDurationSeconds(text) != null;
                          var hasTitle = !!firstUseful(node, ['[data-testid*="title"], h1, h2, h3, h4, strong, .title, [class~="title"], [class$="-title"], [class*="video-title"], [class*="name"]']);
                          if (hasVideoLink && (hasImage || hasDuration || hasTitle)) best = node;
                          if (/^(article|li)$/i.test(node.tagName || '')) {
                            best = node;
                            break;
                          }
                          node = node.parentElement;
                          steps++;
                        }
                        return best;
                      }
                      function titleFromLink(link) {
                        var values = [
                          link.getAttribute('title'),
                          link.getAttribute('aria-label')
                        ];
                        var titleChild = link.querySelector('[data-testid*="title"], h1, h2, h3, h4, strong, .title, [class~="title"], [class$="-title"], [class*="video-title"], [class*="name"]');
                        if (titleChild) {
                          values.unshift(titleChild.getAttribute('title'), titleChild.innerText, titleChild.textContent);
                        }
                        var image = link.querySelector('img[alt]');
                        if (image) {
                          values.unshift(image.getAttribute('alt'));
                        }
                        var card = cardFromLink(link);
                        if (card) {
                          var cardTitle = card.querySelector('[data-testid*="title"], h1, h2, h3, h4, strong, .title, [class~="title"], [class$="-title"], [class*="video-title"], [class*="name"]');
                          if (cardTitle) {
                            values.unshift(cardTitle.getAttribute('title'), cardTitle.innerText, cardTitle.textContent);
                          }
                        }
                        for (var j = 0; j < values.length; j++) {
                          var title = usefulTitle(values[j]);
                          if (title) return title;
                        }
                        return '';
                      }
                      function collectResults() {
                        var seen = {};
                        var results = [];
                        var searchPage = /\/search/i.test(location.pathname);
                        var links = Array.prototype.slice.call(document.querySelectorAll('a[href]'));
                        for (var i = 0; i < links.length; i++) {
                          var link = links[i];
                          var href = link.href || link.getAttribute('href') || '';
                          var match = href.match(/\/video\/([A-Za-z0-9_-]{8,})/);
                          if (!match) continue;
                          var id = match[1];
                          if (!id || seen[id]) continue;
                          var card = cardFromLink(link);
                          var meta = metaFrom(card || link, false);
                          var candidateTitle = titleFromLink(link) || meta.title || '';
                          if (searchPage && !candidateTitle) continue;
                          seen[id] = true;
                          results.push({
                            id: id,
                            url: href,
                            searchTitle: candidateTitle,
                            title: meta.title || '',
                            description: meta.description || '',
                            authorId: meta.authorId || '',
                            authorName: meta.authorName || '',
                            authorUsername: meta.authorUsername || '',
                            authorAvatarUrl: meta.authorAvatarUrl || '',
                            thumbnailUrl: meta.thumbnailUrl || '',
                            rating: meta.rating || '',
                            visibility: meta.visibility || '',
                            createdAt: meta.createdAt || '',
                            updatedAt: meta.updatedAt || '',
                            durationSeconds: meta.durationSeconds,
                            likeCount: meta.likeCount,
                            viewCount: meta.viewCount,
                            commentCount: meta.commentCount,
                            tags: meta.tags || []
                          });
                          if (results.length >= 30) break;
                        }
                        return results;
                      }
                      return JSON.stringify({
                        url: location.href,
                        title: document.title || '',
                        bodyText: document.body ? document.body.innerText : '',
                        html: document.documentElement ? document.documentElement.outerHTML : '',
                        pageMeta: metaFrom(document, true),
                        results: collectResults(),
                        structuredResults: collectStructuredResults(),
                        apiProbeStarted: window.__iwaraManagerApiStarted === true,
                        apiProbeStartedAt: window.__iwaraManagerApiStartedAt || 0,
                        apiProbePendingMillis: window.__iwaraManagerApiStartedAt ? Date.now() - window.__iwaraManagerApiStartedAt : 0,
                        apiProbeTimeoutMillis: window.__iwaraManagerApiTimeoutMillis || 0,
                        apiProbeDone: window.__iwaraManagerApiDone === true,
                        apiProbeErrors: window.__iwaraManagerApiErrors || [],
                        apiProbeSummaries: window.__iwaraManagerApiSummaries || []
                      });
                    })();
                    """.trimIndent()
                ) { value ->
                    val payload = parseEvaluateObject(value)

                    val pageUrl = payload.optString("url", webView.url.orEmpty())
                    val title = payload.optString("title", webView.title.orEmpty())
                    val bodyText = payload.optString("bodyText")
                    val html = payload.optString("html")
                    val cloudflare = bodyText.contains("Enable JavaScript and cookies", true) ||
                        title.contains("Just a moment", true) ||
                        html.contains("cf_chl", true)
                    val noResults = bodyText.contains("No results", true) ||
                        bodyText.contains("0 results", true) ||
                        bodyText.contains("No videos found", true) ||
                        bodyText.contains("Nothing found", true)
                    val loading = title.contains("Loading", true) ||
                        bodyText.trim().equals("Loading", ignoreCase = true) ||
                        bodyText.trim().equals("Loading...", ignoreCase = true)
                    val notFound = title.contains("404", true) ||
                        title.contains("Not Found", true) ||
                        bodyText.contains("404", true) ||
                        bodyText.contains("Not Found", true) ||
                        bodyText.contains("Video not found", true)
                    val pageError = title.contains("Error | Iwara", true) ||
                        title.equals("Error", ignoreCase = true) ||
                        title.startsWith("Error ", ignoreCase = true) ||
                        notFound
                    val emptyDocument = bodyText.isBlank() && html.isBlank()
                    val structuredResults = readResultObjects(payload.optJSONArray("structuredResults"))
                    val pageResults = if (isSearch) {
                        mergeResultObjects(
                            primary = readResultObjects(payload.optJSONArray("results")),
                            supplemental = structuredResults
                        )
                    } else {
                        emptyList()
                    }
                    val currentPageId = if (!isSearch) currentVideoIdFromUrl(pageUrl) else null
                    val currentStructuredResult = currentPageId?.let { expectedId ->
                        structuredResults.firstOrNull { it.optString("id") == expectedId }
                    }
                    val fallbackIds = if (
                        pageResults.isEmpty() &&
                        currentStructuredResult == null &&
                        currentPageId != null &&
                        allowPageFallback &&
                        !timedOut &&
                        !emptyDocument &&
                        !loading &&
                        !pageError &&
                        !cloudflare &&
                        (bodyText.length > 80 || html.length > 3000)
                    ) {
                        listOf(currentPageId)
                    } else {
                        emptyList()
                    }
                    val hasStructuredVideo = pageResults.isNotEmpty() || currentStructuredResult != null
                    val apiProbeErrors = payload.optJSONArray("apiProbeErrors") ?: JSONArray()
                    val apiAllNotFound = apiProbeErrors.length() > 0 &&
                        (0 until apiProbeErrors.length()).all { index ->
                            apiProbeErrors.optString(index).contains("HTTP 404", ignoreCase = true)
                        }
                    val resultObjects = if (
                        (pageError && !hasStructuredVideo) ||
                        cloudflare ||
                        noResults ||
                        (loading && !hasStructuredVideo) ||
                        (timedOut && emptyDocument && !hasStructuredVideo)
                    ) {
                        emptyList()
                    } else if (pageResults.isNotEmpty()) {
                        pageResults
                    } else if (currentStructuredResult != null) {
                        listOf(currentStructuredResult)
                    } else {
                        fallbackIds.map { id ->
                            val pageMeta = payload.optJSONObject("pageMeta") ?: JSONObject()
                            JSONObject()
                                .put("id", id)
                                .put("url", "https://www.iwara.tv/video/$id")
                                .put(
                                    "searchTitle",
                                    pageMeta.optString("title").ifBlank {
                                        cleanedPageTitle(title).ifBlank { query }
                                    }
                                )
                                .put("title", pageMeta.optString("title"))
                                .put("description", pageMeta.optString("description"))
                                .put("authorId", pageMeta.optString("authorId"))
                                .put("authorName", pageMeta.optString("authorName"))
                                .put("authorUsername", pageMeta.optString("authorUsername"))
                                .put("authorAvatarUrl", pageMeta.optString("authorAvatarUrl"))
                                .put("thumbnailUrl", pageMeta.optString("thumbnailUrl"))
                                .put("rating", pageMeta.optString("rating"))
                                .put("visibility", pageMeta.optString("visibility"))
                                .put("createdAt", pageMeta.optString("createdAt"))
                                .put("updatedAt", pageMeta.optString("updatedAt"))
                                .put("durationSeconds", pageMeta.opt("durationSeconds"))
                                .put("likeCount", pageMeta.opt("likeCount"))
                                .put("viewCount", pageMeta.opt("viewCount"))
                                .put("commentCount", pageMeta.opt("commentCount"))
                                .put("tags", pageMeta.optJSONArray("tags") ?: JSONArray())
                        }
                    }
                    val ids = resultObjects.mapNotNull { it.optString("id").takeIf { id -> id.isNotBlank() } }

                    val failureReason = when {
                        ids.isNotEmpty() -> null
                        pageError && apiAllNotFound -> "Iwara 返回错误页面，API 也返回 404（可能视频已删除、设为私密，或 API 端点/Header 配置不匹配）"
                        pageError -> "Iwara 返回错误页面"
                        timedOut -> "Iwara 页面超时（${timeoutMillis / 1000} 秒）"
                        loading && ids.isEmpty() -> "Iwara 页面仍停留在 Loading，未提取到有效视频"
                        cloudflare -> "Iwara Cloudflare 校验未通过"
                        noResults -> "Iwara 搜索无结果"
                        lastError != null -> lastError
                        else -> "页面已加载，但未提取到候选 ID"
                    }

                    val result = JSONObject()
                        .put("query", query)
                        .put("url", url)
                        .put("pageUrl", pageUrl)
                        .put("pageTitle", title)
                        .put("bodyTextLength", bodyText.length)
                        .put("htmlLength", html.length)
                        .put("attempts", attempts)
                        .put("pageStarted", pageStarted)
                        .put("pageFinished", pageFinished)
                        .put("timedOut", timedOut)
                        .put("timeoutMillis", timeoutMillis)
                        .put("apiEndpointTemplates", JSONArray(effectiveApiEndpointTemplates))
                        .put("apiRequestHeaderNames", JSONArray(options.apiRequestHeaders.keys))
                        .put("allowPageFallback", allowPageFallback)
                        .put("lastError", lastError)
                        .put("cloudflare", cloudflare)
                        .put("noResults", noResults)
                        .put("pageError", pageError)
                        .put("loading", loading)
                        .put("structuredResultCount", structuredResults.size)
                        .put("apiProbeStarted", payload.optBoolean("apiProbeStarted"))
                        .put("apiProbeStartedAt", payload.optLong("apiProbeStartedAt"))
                        .put("apiProbePendingMillis", payload.optLong("apiProbePendingMillis"))
                        .put("apiProbeTimeoutMillis", payload.optLong("apiProbeTimeoutMillis"))
                        .put("apiProbeDone", payload.optBoolean("apiProbeDone"))
                        .put("apiProbeErrors", apiProbeErrors)
                        .put("apiProbeSummaries", payload.optJSONArray("apiProbeSummaries") ?: JSONArray())
                        .put("candidateIds", JSONArray(ids))
                        .put("results", JSONArray(resultObjects))
                        .put("failureReason", failureReason)
                        .put(
                            "diagnosticSummary",
                            buildString {
                                append(failureReason ?: "搜索成功")
                                append("；提取候选ID ${ids.size} 个")
                                append("；尝试 $attempts 次")
                                append("；页面标题：$title")
                                append("；正文长度：${bodyText.length}")
                                append("；HTML长度：${html.length}")
                                if (cloudflare) append("；Cloudflare:true")
                                if (pageError) append("；错误页:true")
                                if (loading) append("；Loading:true")
                                append("；结构化结果:${structuredResults.size}")
                                if (payload.optBoolean("apiProbeStarted")) {
                                    val apiProbeState = if (payload.optBoolean("apiProbeDone")) "完成" else "等待中"
                                    append("；API探测:$apiProbeState")
                                    append("；API等待:${payload.optLong("apiProbePendingMillis")}ms")
                                    if (apiProbeErrors.length() > 0) {
                                        append("；API错误:${apiProbeErrors.length()}个")
                                    }
                                }
                            }
                        )

                    webView.destroy()
                    continuation.resume(result.toString(2))
                }
            }

            val timeout = Runnable {
                finish(timedOut = true)
            }

            val poll = object : Runnable {
                override fun run() {
                    if (completed) return
                    attempts += 1
                    webView.evaluateJavascript(
                        """
                        (function() {
                          var searchPage = ${if (isSearch) "true" else "false"};
                          var title = document.title || '';
                          var text = document.body ? document.body.innerText : '';
                          var html = document.documentElement ? document.documentElement.outerHTML : '';
                          var cloudflare = /enable javascript and cookies|just a moment/i.test(text + ' ' + title) || /cf_chl/i.test(html);
                          var loading = /loading/i.test(title) || /^\s*loading\.?\s*$/i.test(text);
                          var notFound = /404|not found|video not found/i.test(text + ' ' + title);
                          var pageError = /error\s*\|\s*iwara/i.test(title) || /^error\b/i.test(title) || notFound;
                          var noResults = /no results|0 results|no videos found|nothing found/i.test(text);
                          function currentVideoId() {
                            var match = (location.pathname || '').match(/\/video\/([^/?#]+)/i);
                            return match ? decodeURIComponent(match[1]) : '';
                          }
                          function startApiProbe() {
                            var id = currentVideoId();
                            if (searchPage || !id || window.__iwaraManagerApiStarted) return;
                            var endpointTemplates = $apiEndpointTemplatesJson;
                            var configuredHeaders = $apiRequestHeadersJson;
                            if (!Array.isArray(endpointTemplates) || endpointTemplates.length === 0) return;
                            window.__iwaraManagerApiStarted = true;
                            window.__iwaraManagerApiDone = false;
                            window.__iwaraManagerApiStartedAt = Date.now();
                            window.__iwaraManagerApiTimeoutMillis = $apiProbeTimeoutMillis;
                            window.__iwaraManagerApiResults = [];
                            window.__iwaraManagerApiErrors = [];
                            window.__iwaraManagerApiSummaries = [];
                            var encoded = encodeURIComponent(id);
                            var urls = endpointTemplates.map(function(template) {
                              return String(template)
                                .replace(/\{id\}/g, encoded)
                                .replace(/\{rawId\}/g, id);
                            }).filter(function(value, index, array) {
                              return value && array.indexOf(value) === index;
                            });
                            if (!urls.length) return;
                            function buildRequestHeaders() {
                              var headers = {};
                              var forbidden = /^(accept-charset|accept-encoding|access-control-request-headers|access-control-request-method|connection|content-length|cookie|date|dnt|expect|host|keep-alive|origin|permissions-policy|referer|set-cookie|te|trailer|transfer-encoding|upgrade|via)$/i;
                              Object.keys(configuredHeaders || {}).forEach(function(name) {
                                var value = configuredHeaders[name];
                                if (!name || forbidden.test(name) || value == null || String(value).trim() === '') return;
                                headers[name] = String(value);
                              });
                              if (!headers.accept && !headers.Accept) {
                                headers.accept = 'application/json, text/plain, */*';
                              }
                              return headers;
                            }
                            function summarize(apiUrl, parsed) {
                              var root = parsed && typeof parsed === 'object' ? parsed : {};
                              var data = root.data && typeof root.data === 'object' ? root.data : root;
                              var file = data.file && typeof data.file === 'object' ? data.file :
                                (Array.isArray(data.files) && data.files.length ? data.files[0] : {});
                              var user = data.user && typeof data.user === 'object' ? data.user : {};
                              return {
                                url: apiUrl,
                                rootKeys: Object.keys(root).slice(0, 80),
                                dataKeys: Object.keys(data).slice(0, 80),
                                fileKeys: Object.keys(file || {}).slice(0, 80),
                                userKeys: Object.keys(user || {}).slice(0, 80),
                                dataPreview: JSON.stringify(data).slice(0, 3000)
                              };
                            }
                            function markDone() {
                              if (window.__iwaraManagerApiDone === true) return;
                              window.__iwaraManagerApiDone = true;
                            }
                            var remaining = urls.length;
                            var globalTimer = setTimeout(function() {
                              if (window.__iwaraManagerApiDone === true) return;
                              window.__iwaraManagerApiErrors.push('API probe timeout after ' + window.__iwaraManagerApiTimeoutMillis + 'ms');
                              markDone();
                            }, window.__iwaraManagerApiTimeoutMillis);
                            function completeOne() {
                              remaining -= 1;
                              if (remaining <= 0) {
                                clearTimeout(globalTimer);
                                markDone();
                              }
                            }
                            urls.forEach(function(apiUrl) {
                              var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
                              var requestTimer = controller ? setTimeout(function() {
                                try { controller.abort(); } catch (e) {}
                              }, window.__iwaraManagerApiTimeoutMillis - 1000) : null;
                              fetch(apiUrl, {
                                credentials: 'include',
                                headers: buildRequestHeaders(),
                                signal: controller ? controller.signal : undefined
                              }).then(function(response) {
                                return response.text().then(function(body) {
                                  if (!response.ok) {
                                    window.__iwaraManagerApiErrors.push(apiUrl + ' HTTP ' + response.status);
                                    return;
                                  }
                                  try {
                                    var parsed = JSON.parse(body);
                                    if (parsed && typeof parsed === 'object') {
                                      window.__iwaraManagerApiResults.push(parsed);
                                      window.__iwaraManagerApiSummaries.push(summarize(apiUrl, parsed));
                                    }
                                  } catch (e) {
                                    window.__iwaraManagerApiErrors.push(apiUrl + ' JSON parse failed');
                                  }
                                });
                              }).catch(function(error) {
                                window.__iwaraManagerApiErrors.push(apiUrl + ' ' + String(error && error.message || error || 'fetch failed'));
                              }).finally(function() {
                                if (requestTimer) clearTimeout(requestTimer);
                                completeOne();
                              });
                            });
                          }
                          startApiProbe();
                          var linkFound = /\/video\/([A-Za-z0-9_-]{8,})/.test(html);
                          var pathHasId = /\/video\/[A-Za-z0-9_-]{8,}/.test(location.pathname);
                          var pathFound = pathHasId && !cloudflare && (text.length > 80 || html.length > 3000);
                          var titleReady = !/^(search|loading|iwara|error|404|not found)$/i.test((title || '').replace(/\s*\|\s*Iwara\s*$/i, '').trim()) ||
                              !!document.querySelector('h1, h2, [data-testid*="title"], meta[property="og:title"]');
                          var apiDone = window.__iwaraManagerApiDone === true;
                          var apiStarted = window.__iwaraManagerApiStarted === true;
                          var apiHasVideo = Array.isArray(window.__iwaraManagerApiResults) && window.__iwaraManagerApiResults.length > 0;
                          var metadataReady = /"(durationSeconds|duration_seconds|duration|rating|visibility|createdAt|created_at|publishedAt|published_at|tags|likeCount|viewCount|commentCount)"\s*:/i.test(html) ||
                              /\b(tags?|views?|likes?|comments?|r-?18|ecchi|general|public|private|unlisted)\b/i.test(text);
                          var searchReady = linkFound && !loading && !pageError && !cloudflare;
                          var enoughDomWait = ${attempts} >= 4;
                          var allowPageFallback = ${if (allowPageFallback) "true" else "false"};
                          var domFallbackReady = allowPageFallback && !loading && !pageError && titleReady && metadataReady &&
                              (apiDone || !apiStarted) && enoughDomWait;
                          var apiFallbackReady = allowPageFallback && !loading && !pageError && titleReady && apiDone && metadataReady;
                          var detailReady = pathFound && (apiHasVideo || apiFallbackReady || domFallbackReady);
                          var found = searchPage ? searchReady : detailReady;
                          return JSON.stringify({
                            found: found,
                            noResults: noResults,
                            cloudflare: cloudflare,
                            pageError: pageError,
                            loading: loading,
                            apiStarted: apiStarted,
                            apiDone: apiDone,
                            apiHasVideo: apiHasVideo
                          });
                        })();
                        """.trimIndent()
                    ) { value ->
                        val obj = parseEvaluateObject(value)
                        val terminalPageError = obj.optBoolean("pageError") &&
                            (isSearch || obj.optBoolean("apiDone") || !obj.optBoolean("apiStarted"))
                        if (
                            obj.optBoolean("found") ||
                            obj.optBoolean("noResults") ||
                            obj.optBoolean("cloudflare") ||
                            terminalPageError
                        ) {
                            finish(timedOut = false)
                        } else {
                            handler.postDelayed(this, 1000L)
                        }
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
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                        "Chrome/125.0.0.0 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    pageStarted = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageFinished = true
                    handler.postDelayed(poll, 2500L)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        lastError = error?.description?.toString()
                        finish(timedOut = false)
                    }
                }
            }

            handler.postDelayed(timeout, timeoutMillis)
            webView.loadUrl(url)

            continuation.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                webView.destroy()
            }
        }
    }

    private fun currentVideoIdFromUrl(pageUrl: String): String? {
        return Regex("""/video/([A-Za-z0-9_-]{8,})""")
            .find(pageUrl)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun readResultObjects(array: JSONArray?): List<JSONObject> {
        if (array == null) return emptyList()
        val seen = linkedSetOf<String>()
        val result = mutableListOf<JSONObject>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optString("id").trim()
            if (id.isBlank() || id in seen) continue
            seen += id
            result += JSONObject()
                .put("id", id)
                .put(
                    "url",
                    obj.optString("url").ifBlank { "https://www.iwara.tv/video/$id" }
                )
                .put("searchTitle", obj.optString("searchTitle").trim())
                .put("title", obj.optString("title").trim())
                .put("description", obj.optString("description").trim())
                .put("authorId", obj.optString("authorId").trim())
                .put("authorName", obj.optString("authorName").trim())
                .put("authorUsername", obj.optString("authorUsername").trim())
                .put("authorAvatarUrl", obj.optString("authorAvatarUrl").trim())
                .put("thumbnailUrl", obj.optString("thumbnailUrl").trim())
                .put("rating", obj.optString("rating").trim())
                .put("visibility", obj.optString("visibility").trim())
                .put("createdAt", obj.optString("createdAt").trim())
                .put("updatedAt", obj.optString("updatedAt").trim())
                .put("durationSeconds", obj.opt("durationSeconds"))
                .put("likeCount", obj.opt("likeCount"))
                .put("viewCount", obj.opt("viewCount"))
                .put("commentCount", obj.opt("commentCount"))
                .put("tags", obj.optJSONArray("tags") ?: JSONArray())
        }
        return result
    }

    private fun mergeResultObjects(
        primary: List<JSONObject>,
        supplemental: List<JSONObject>
    ): List<JSONObject> {
        if (primary.isEmpty()) return supplemental
        if (supplemental.isEmpty()) return primary
        val supplementalById = supplemental.associateBy { it.optString("id") }
        val used = linkedSetOf<String>()
        val merged = primary.map { item ->
            val id = item.optString("id")
            used += id
            val extra = supplementalById[id] ?: return@map item
            mergeResultObject(item, extra)
        }.toMutableList()
        return merged
    }

    private fun mergeResultObject(
        primary: JSONObject,
        supplemental: JSONObject
    ): JSONObject {
        val result = JSONObject(primary.toString())
        listOf("title", "description", "thumbnailUrl").forEach { key ->
            if (isMissing(result.opt(key)) && !isMissing(supplemental.opt(key))) {
                result.put(key, supplemental.opt(key))
            }
        }
        listOf(
            "authorId",
            "authorName",
            "authorUsername",
            "authorAvatarUrl",
            "rating",
            "visibility",
            "createdAt",
            "updatedAt",
            "durationSeconds",
            "likeCount",
            "viewCount",
            "commentCount",
            "tags"
        ).forEach { key ->
            if (!isMissing(supplemental.opt(key))) {
                result.put(key, supplemental.opt(key))
            }
        }
        if (result.optString("searchTitle").isBlank() && supplemental.optString("searchTitle").isNotBlank()) {
            result.put("searchTitle", supplemental.optString("searchTitle"))
        }
        return result
    }

    private fun isMissing(value: Any?): Boolean {
        return value == null ||
            value == JSONObject.NULL ||
            (value is String && value.isBlank()) ||
            (value is JSONArray && value.length() == 0)
    }

    private fun cleanedPageTitle(title: String): String {
        return title
            .replace(" | Iwara", "")
            .replace("Iwara", "")
            .trim()
            .takeUnless {
                it.isBlank() ||
                    it.equals("Search", ignoreCase = true) ||
                    it.equals("Loading", ignoreCase = true) ||
                    it.equals("Error", ignoreCase = true) ||
                    it.equals("404", ignoreCase = true) ||
                    it.contains("Not Found", ignoreCase = true)
            }
            .orEmpty()
    }

    private fun parseEvaluateObject(raw: String?): JSONObject {
        if (raw.isNullOrBlank() || raw == "null") return JSONObject()
        return runCatching {
            when (val token = JSONTokener(raw).nextValue()) {
                is JSONObject -> {
                    val result = token.optString("result")
                    if (result.isBlank()) token else JSONObject(result)
                }
                is String -> JSONObject(token)
                else -> JSONObject(token.toString())
            }
        }.getOrDefault(JSONObject())
    }
}
