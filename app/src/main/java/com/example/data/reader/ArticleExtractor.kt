package com.example.data.reader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val TAG = "ArticleExtractor"

data class ExtractedArticle(
    val cleanUrl: String,
    val originalUrl: String,
    val title: String,
    val sourceDomain: String,
    val siteName: String,
    val author: String,
    val publishedDate: String,
    val leadImageUrl: String,
    val paragraphs: List<String>,
    val readingTimeMinutes: Int,
    val trackersStripped: Int
)

class ArticleExtractor(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val adAndClutterSelectors = listOf(
        "script", "style", "noscript", "iframe", "ins", "aside", "nav", "footer",
        "form", "button", "svg", "canvas", "video", "audio",
        "[aria-hidden='true']",
        "[data-ad]", "[data-ad-slot]", "[data-ad-client]", "[data-testid*='ad']",
        "[class*='ad-']", "[class*='-ad']", "[class*='ads-']", "[class*='advert']",
        "[class*='sponsor']", "[class*='promo']", "[class*='banner']",
        "[class*='popup']", "[class*='modal']", "[class*='cookie']",
        "[class*='newsletter']", "[class*='subscribe']", "[class*='subscription']",
        "[class*='social']", "[class*='share']", "[class*='follow']",
        "[class*='related']", "[class*='recommend']", "[class*='more-news']",
        "[class*='trending']", "[class*='most-read']", "[class*='comment']",
        "[class*='sidebar']", "[class*='widget']", "[class*='breadcrumb']",
        "[class*='breaking']", "[class*='ticker']", "[class*='taboola']",
        "[class*='outbrain']", "[class*='mgid']", "[class*='disqus']",
        "[id*='ad-']", "[id*='-ad']", "[id*='google_ads']", "[id*='taboola']",
        "[id*='outbrain']", "[id*='related']", "[id*='comments']", "[id*='sidebar']",
        ".adsbygoogle", ".advertisement", ".ad-container", ".ad-wrapper"
    )

    private val highConfidenceContainers = listOf(
        "[itemprop='articleBody']",
        "article [itemprop='articleBody']",
        ".jw_detail_content_holder",
        ".detail_holder",
        ".details_content",
        ".news-details",
        ".story-element-text",
        ".story-content",
        ".article-content",
        ".article-body",
        ".post-content",
        ".entry-content",
        ".news_content",
        ".detail-content",
        "article",
        "main"
    )

    private val boilerplatePhrases = listOf(
        "advertisement", "sponsored", "বিজ্ঞাপন", "আরও পড়ুন", "আরও পড়ুন",
        "বিস্তারিত পড়ুন", "ফলো করুন", "শেয়ার করুন", "শেয়ার করুন",
        "read more", "also read", "related news", "follow us", "subscribe now",
        "all rights reserved", "copyright ©", "share this", "comments are closed",
        "গুগল নিউজে", "হোয়াটসঅ্যাপে", "টেলিগ্রামে"
    )

    suspend fun fetchAndExtract(rawInput: String): ExtractedArticle = withContext(Dispatchers.IO) {
        val cleaned = UrlCleaner.extractAndClean(rawInput)
            ?: throw IllegalArgumentException("Please provide a valid http or https news link.")

        val request = Request.Builder()
            .url(cleaned.cleanUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
            )
            .header("Accept-Language", "bn-BD,bn;q=0.95,en-US;q=0.85,en;q=0.7")
            .header("Cache-Control", "no-cache")
            .build()

        val (htmlBytes, finalUrl) = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code} for ${cleaned.domain}")
            }
            val body = response.body ?: throw IOException("Empty response from ${cleaned.domain}")
            val bytes = body.bytes()
            if (bytes.isEmpty()) throw IOException("Empty page received from ${cleaned.domain}")
            bytes to response.request.url.toString()
        }

        val finalCleaned = UrlCleaner.extractAndClean(finalUrl) ?: cleaned
        val doc: Document = Jsoup.parse(ByteArrayInputStream(htmlBytes), null, finalCleaned.cleanUrl)

        val jsonLd = extractJsonLdArticle(doc)
        val rawTitle = firstNonBlank(
            jsonLd?.headline,
            doc.selectFirst("meta[property='og:title']")?.attr("content"),
            doc.selectFirst("meta[name='twitter:title']")?.attr("content"),
            doc.selectFirst("h1")?.text(),
            doc.title()
        ).cleanHeadline(finalCleaned.domain)

        val siteName = firstNonBlank(
            jsonLd?.publisher,
            doc.selectFirst("meta[property='og:site_name']")?.attr("content"),
            finalCleaned.domain
        )

        val author = firstNonBlank(
            jsonLd?.author,
            doc.selectFirst("meta[name='author']")?.attr("content"),
            doc.selectFirst("[rel='author']")?.text(),
            doc.selectFirst("[class*='author']")?.text()
        ).take(80)

        val publishedDate = formatPublishedDate(
            firstNonBlank(
                jsonLd?.datePublished,
                doc.selectFirst("meta[property='article:published_time']")?.attr("content"),
                doc.selectFirst("meta[name='pubdate']")?.attr("content"),
                doc.selectFirst("time[datetime]")?.attr("datetime"),
                doc.selectFirst("time")?.text()
            )
        )

        val leadImageUrl = resolveImageUrl(
            firstNonBlank(
                jsonLd?.imageUrl,
                doc.selectFirst("meta[property='og:image']")?.attr("content"),
                doc.selectFirst("meta[name='twitter:image']")?.attr("content")
            ),
            finalCleaned.cleanUrl
        )

        // Strip all advertisements, scripts, sidebars, social boxes, and clutter before DOM scoring
        val cleanDoc = doc.clone()
        adAndClutterSelectors.forEach { selector ->
            runCatching { cleanDoc.select(selector).remove() }
        }

        var paragraphs = extractParagraphsFromDom(cleanDoc, rawTitle)

        // Fallback or enrich from JSON-LD articleBody if DOM extraction yielded sparse text
        val jsonLdParagraphs = jsonLd?.articleBody
            ?.split(Regex("\\r?\\n+"))
            ?.map { it.trim() }
            ?.filter { isUsefulParagraph(it, rawTitle) }
            .orEmpty()

        if (paragraphs.sumOf { it.length } < 180 && jsonLdParagraphs.size >= paragraphs.size) {
            paragraphs = jsonLdParagraphs
        }

        // Last resort fallback: meta description + visible paragraphs in body
        if (paragraphs.isEmpty()) {
            val fallbackParagraphs = cleanDoc.select("p")
                .map { it.text().trim() }
                .filter { isUsefulParagraph(it, rawTitle) }
                .distinct()
            if (fallbackParagraphs.isNotEmpty()) {
                paragraphs = fallbackParagraphs
            } else {
                val metaDesc = doc.selectFirst("meta[property='og:description']")?.attr("content")
                    ?: doc.selectFirst("meta[name='description']")?.attr("content")
                if (!metaDesc.isNullOrBlank()) {
                    paragraphs = listOf(metaDesc.trim())
                }
            }
        }

        if (paragraphs.isEmpty()) {
            throw IOException("Could not extract readable article content from ${finalCleaned.domain}. This page may require interactive login or heavy JavaScript.")
        }

        val totalWords = paragraphs.sumOf { it.split(Regex("\\s+")).count { w -> w.isNotBlank() } }
        val readingTime = max(1, (totalWords + 179) / 180)

        ExtractedArticle(
            cleanUrl = finalCleaned.cleanUrl,
            originalUrl = cleaned.originalUrl,
            title = rawTitle.ifBlank { finalCleaned.domain },
            sourceDomain = finalCleaned.domain,
            siteName = siteName.ifBlank { finalCleaned.domain },
            author = author,
            publishedDate = publishedDate,
            leadImageUrl = leadImageUrl,
            paragraphs = paragraphs,
            readingTimeMinutes = readingTime,
            trackersStripped = cleaned.strippedTrackersCount
        )
    }

    private fun extractParagraphsFromDom(doc: Document, title: String): List<String> {
        // 1. Check high-confidence semantic news containers
        for (selector in highConfidenceContainers) {
            val candidates = doc.select(selector)
            var bestFromSelector: List<String> = emptyList()
            for (el in candidates) {
                val extracted = extractCleanBlocks(el, title)
                if (extracted.sumOf { it.length } > bestFromSelector.sumOf { it.length }) {
                    bestFromSelector = extracted
                }
            }
            if (bestFromSelector.sumOf { it.length } >= 260) {
                return bestFromSelector
            }
        }

        // 2. Readability-style heuristic scoring across div/section/article nodes
        var bestContainer: Element? = null
        var bestScore = 0.0

        val candidates = doc.select("article, section, div, main")
        for (el in candidates) {
            val paragraphs = el.select("> p, > div > p")
            if (paragraphs.isEmpty()) continue

            val textLength = paragraphs.sumOf { it.text().trim().length }
            if (textLength < 120) continue

            val totalTextLen = el.text().length.coerceAtLeast(1)
            val linkTextLen = el.select("a").sumOf { it.text().length }
            val linkDensity = linkTextLen.toDouble() / totalTextLen.toDouble()
            if (linkDensity > 0.35) continue

            val punctuationCount = el.text().count { it == '.' || it == '।' || it == ',' || it == '?' }
            val score = textLength + (paragraphs.size * 25.0) + (punctuationCount * 4.0) - (linkTextLen * 1.5)
            if (score > bestScore) {
                bestScore = score
                bestContainer = el
            }
        }

        return bestContainer?.let { extractCleanBlocks(it, title) } ?: emptyList()
    }

    private fun extractCleanBlocks(container: Element, title: String): List<String> {
        val result = mutableListOf<String>()
        val seen = HashSet<String>()

        val blocks = container.select("p, h2, h3, blockquote")
        for (block in blocks) {
            // Skip blocks that are primarily a link list ("Related: ...")
            val text = block.text().replace(Regex("\\s+"), " ").trim()
            if (!isUsefulParagraph(text, title)) continue

            val linkLen = block.select("a").sumOf { it.text().length }
            if (text.isNotEmpty() && linkLen.toDouble() / text.length.toDouble() > 0.6 && text.length < 140) {
                continue
            }

            if (seen.add(text)) {
                result.add(text)
            }
        }

        // Handle sites that put text directly inside <br>-separated divs without <p>
        if (result.isEmpty()) {
            val rawHtml = container.html()
            val textWithNewlines = rawHtml
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</(p|div|li|h[1-6])>"), "\n")
            val plain = Jsoup.parse(textWithNewlines).wholeText()
            plain.lines()
                .map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { isUsefulParagraph(it, title) }
                .forEach {
                    if (seen.add(it)) result.add(it)
                }
        }

        return result
    }

    private fun isUsefulParagraph(text: String, title: String): Boolean {
        if (text.length < 28) return false
        if (text.equals(title, ignoreCase = true)) return false
        val lower = text.lowercase()
        if (boilerplatePhrases.any { phrase ->
                lower == phrase || (text.length < 90 && lower.contains(phrase))
            }
        ) {
            return false
        }
        return true
    }

    private data class JsonLdArticle(
        val headline: String = "",
        val articleBody: String = "",
        val datePublished: String = "",
        val author: String = "",
        val imageUrl: String = "",
        val publisher: String = ""
    )

    private fun extractJsonLdArticle(doc: Document): JsonLdArticle? {
        val scripts = doc.select("script[type='application/ld+json']")
        for (script in scripts) {
            val raw = script.data().trim()
            if (raw.isBlank()) continue
            try {
                val parsed = parseJsonLdNode(raw)
                if (parsed != null) return parsed
            } catch (e: Exception) {
                Log.d(TAG, "Skipping malformed JSON-LD block: ${e.message}")
            }
        }
        return null
    }

    private fun parseJsonLdNode(raw: String): JsonLdArticle? {
        if (raw.startsWith("[")) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                findArticleInJsonObject(obj)?.let { return it }
            }
            return null
        }
        val obj = JSONObject(raw)
        return findArticleInJsonObject(obj)
    }

    private fun findArticleInJsonObject(obj: JSONObject): JsonLdArticle? {
        val type = obj.opt("@type")?.toString().orEmpty()
        if (type.contains("Article", ignoreCase = true) || type.contains("NewsArticle", ignoreCase = true)) {
            val authorStr = when (val auth = obj.opt("author")) {
                is JSONObject -> auth.optString("name")
                is JSONArray -> (0 until auth.length())
                    .mapNotNull { auth.optJSONObject(it)?.optString("name") }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                is String -> auth
                else -> ""
            }
            val imageStr = when (val img = obj.opt("image")) {
                is JSONObject -> img.optString("url")
                is JSONArray -> {
                    val first = img.opt(0)
                    when (first) {
                        is JSONObject -> first.optString("url")
                        is String -> first
                        else -> ""
                    }
                }
                is String -> img
                else -> ""
            }
            val publisherStr = (obj.opt("publisher") as? JSONObject)?.optString("name").orEmpty()
            return JsonLdArticle(
                headline = obj.optString("headline"),
                articleBody = obj.optString("articleBody"),
                datePublished = obj.optString("datePublished"),
                author = authorStr,
                imageUrl = imageStr,
                publisher = publisherStr
            )
        }
        val graph = obj.optJSONArray("@graph")
        if (graph != null) {
            for (i in 0 until graph.length()) {
                val child = graph.optJSONObject(i) ?: continue
                findArticleInJsonObject(child)?.let { return it }
            }
        }
        return null
    }

    private fun resolveImageUrl(raw: String, baseUrl: String): String {
        if (raw.isBlank()) return ""
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> runCatching { java.net.URI(baseUrl).resolve(raw).toString() }.getOrDefault("")
        }
    }

    private fun String.cleanHeadline(domain: String): String {
        return this.replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(" | $domain")
            .removeSuffix(" - $domain")
            .trim()
    }

    private fun formatPublishedDate(raw: String): String {
        if (raw.isBlank()) return ""
        val trimmed = raw.trim()
        return if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            trimmed.substring(0, 10)
        } else {
            trimmed.take(36)
        }
    }

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }
}
