package com.example.data.reader

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class CleanedUrlResult(
    val cleanUrl: String,
    val originalUrl: String,
    val domain: String,
    val strippedTrackersCount: Int
)

object UrlCleaner {
    private val trackingParams = setOf(
        "fbclid",
        "gclid",
        "dclid",
        "gbraid",
        "wbraid",
        "msclkid",
        "igshid",
        "mc_cid",
        "mc_eid",
        "mibextid",
        "fb_action_ids",
        "fb_action_types",
        "fb_source",
        "fb_ref",
        "yclid",
        "_ga",
        "_gl",
        "_hsenc",
        "_hsmi",
        "hsctatracking",
        "ref",
        "ref_src",
        "ref_url",
        "spm",
        "scm",
        "share_source"
    )

    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    fun extractAndClean(rawInput: String): CleanedUrlResult? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        val extracted = urlRegex.find(trimmed)?.value?.trimEnd('.', ',', ')', ']', ';')
            ?: run {
                val candidate = if (trimmed.contains('.') && !trimmed.contains(' ')) {
                    "https://$trimmed"
                } else {
                    return null
                }
                candidate
            }

        val parsed = extracted.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null

        val builder = parsed.newBuilder()
        var strippedCount = 0
        val paramNames = parsed.queryParameterNames.toList()
        for (name in paramNames) {
            val lower = name.lowercase()
            if (lower in trackingParams || lower.startsWith("utm_") || lower.startsWith("fb_")) {
                builder.removeAllQueryParameters(name)
                strippedCount++
            }
        }

        val cleanHttpUrl = builder.build()
        val domain = cleanHttpUrl.host.removePrefix("www.")
        return CleanedUrlResult(
            cleanUrl = cleanHttpUrl.toString(),
            originalUrl = parsed.toString(),
            domain = domain,
            strippedTrackersCount = strippedCount
        )
    }
}
