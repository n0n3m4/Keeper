package com.example.keeper

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/*
 * LinkPreviews — the god object for link-preview fetching. It is the network
 * counterpart of Notifier: a self-contained concern, hence its own file.
 *
 * URLs in a note body get a cached preview (page title + favicon, stored in the
 * `links` table). A preview is fetched exactly once, when the URL first
 * appears, and is never refreshed — editing a URL yields a different URL, so it
 * is treated as new. A failed fetch is recorded as a FAILED row and kept, so a
 * dead link is not retried on every keystroke or note close.
 *
 * Fetching is fired by the editor 3 s after the body text stabilises, and again
 * when the note is closed. Work runs on an application-scoped coroutine so a
 * fetch kicked off on close still completes after the editor leaves the screen.
 */
object LinkPreviews {
    /** Bumped after every link-row write; the UI keys off it to re-read the DB. */
    val version = mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val MAX_HTML = 64 * 1024
    private const val MAX_FAVICON = 256 * 1024

    private val URL_RE = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val TRAIL = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
    private val TITLE_RE = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val ICON_RE = Regex(
        """<link[^>]+rel=["'][^"']*icon[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val HREF_RE = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** Character ranges [start, end) of each http(s) URL in `text`, with
     *  trailing sentence punctuation excluded ("see https://x.com." resolves). */
    fun urlRanges(text: String): List<Pair<Int, Int>> =
        URL_RE.findAll(text)
            .map { m -> m.range.first to (m.range.first + m.value.trimEnd(*TRAIL).length) }
            .filter { it.second > it.first }
            .toList()

    /** URLs in `body`, in order of appearance, de-duplicated. */
    fun extractUrls(body: String): List<String> =
        urlRanges(body).map { (s, e) -> body.substring(s, e) }.distinct()

    /** Reconciles cached previews for a note against the URLs in its body:
     *  drops previews for URLs that are gone, fetches newly-added URLs, and
     *  leaves already-cached URLs (OK *or* FAILED) untouched. */
    fun refresh(noteId: Long, body: String) {
        if (noteId == 0L) return
        scope.launch {
            val urls = extractUrls(body)
            Db.deleteLinksNotIn(noteId, urls)
            val cached = Db.links(noteId).map { it.url }.toSet()
            version.intValue++
            for (url in urls) {
                if (url in cached) continue
                Db.upsertLink(noteId, Link(url = url, domain = domainOf(url), status = "LOADING"))
                version.intValue++
                fetch(noteId, url)
            }
        }
    }

    private fun domainOf(url: String): String =
        (Uri.parse(url).host ?: "").removePrefix("www.")

    /** Fetches one preview and writes the result (OK with favicon, or FAILED). */
    private fun fetch(noteId: Long, url: String) {
        val link = Link(url = url, domain = domainOf(url))
        try {
            val html = download(url, MAX_HTML)?.toString(Charsets.UTF_8)
                ?: throw Exception("empty response")
            val title = titleOf(html)
            if (title.isBlank()) throw Exception("no title")
            link.title = title
            // A missing favicon is fine — the chip just renders a blank box.
            link.favicon = runCatching { faviconOf(url, html) }.getOrNull()
            link.status = "OK"
        } catch (_: Exception) {
            link.status = "FAILED"
        }
        Db.upsertLink(noteId, link)
        version.intValue++
    }

    private fun titleOf(html: String): String =
        TITLE_RE.find(html)?.groupValues?.get(1)
            ?.let(::unescape)?.replace(Regex("""\s+"""), " ")?.trim()
            .orEmpty()

    /** Resolves the page's declared icon (or /favicon.ico) and returns the
     *  first candidate that decodes to a real bitmap. */
    private fun faviconOf(pageUrl: String, html: String): ByteArray? {
        val base = URL(pageUrl)
        val candidates = ICON_RE.findAll(html)
            .mapNotNull { HREF_RE.find(it.value)?.groupValues?.get(1) }
            .toMutableList()
        candidates.add("/favicon.ico")
        for (href in candidates) {
            val resolved = runCatching { URL(base, href).toString() }.getOrNull() ?: continue
            val bytes = runCatching { download(resolved, MAX_FAVICON) }.getOrNull() ?: continue
            if (bytes.isNotEmpty() &&
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null
            ) return bytes
        }
        return null
    }

    /** GETs `spec`, returning at most `max` bytes, or null on any non-200. */
    private fun download(spec: String, max: Int): ByteArray? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(spec).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val buf = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val chunk = ByteArray(8_192)
                while (buf.size() < max) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
            }
            return buf.toByteArray()
        } finally {
            conn?.disconnect()
        }
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&nbsp;", " ")
}
