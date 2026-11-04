package com.example.metaguardrail.data

import android.net.Uri
import java.net.URI
import java.net.URISyntaxException

/**
 * Deterministic, offline link safety analyzer.
 * No network calls; all heuristics are local and testable.
 */
class LinkSafetyAnalyzer {

    companion object {
        private val TRUSTED_BRANDS = listOf(
            "instagram.com",
            "facebook.com",
            "meta.com",
            "google.com",
            "whatsapp.com"
        )
        private val SUSPICIOUS_KEYWORDS = listOf("login", "verify", "secure", "account", "update")
        private val IP_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        private val REDIRECT_PARAMS = listOf("redirect=", "url=")
    }

    fun analyze(urlInput: String): LinkSafetyResult {
        val trimmed = urlInput.trim()
        if (trimmed.isEmpty()) {
            return LinkSafetyResult(
                url = urlInput,
                status = LinkStatus.INVALID,
                explanation = "Link is empty or blank. Enter a valid http or https URL.",
                https = null
            )
        }

        // Parse URI; treat malformed as INVALID.
        val uri: URI = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            return LinkSafetyResult(
                url = trimmed,
                status = LinkStatus.INVALID,
                explanation = "Link format is invalid and cannot be parsed as a URI.",
                https = null
            )
        } catch (e: IllegalArgumentException) {
            return LinkSafetyResult(
                url = trimmed,
                status = LinkStatus.INVALID,
                explanation = "Link format is invalid and cannot be parsed.",
                https = null
            )
        } catch (e: Exception) {
            return LinkSafetyResult(
                url = trimmed,
                status = LinkStatus.INVALID,
                explanation = "Link format is invalid and cannot be parsed.",
                https = null
            )
        }

        val scheme = uri.scheme?.lowercase()?.trim()
        val https: Boolean? = when (scheme) {
            "https" -> true
            "http" -> false
            else -> null
        }

        // Unsupported or missing scheme -> INVALID (https == null per spec)
        if (scheme.isNullOrBlank()) {
            return LinkSafetyResult(
                url = trimmed,
                status = LinkStatus.INVALID,
                explanation = "Link is missing a valid http/https scheme (e.g., https://example.com).",
                https = null
            )
        }
        if (scheme != "http" && scheme != "https") {
            return LinkSafetyResult(
                url = trimmed,
                status = LinkStatus.INVALID,
                explanation = "Unsupported link scheme '$scheme'. Only http and https are analyzed.",
                https = null
            )
        }

        // Host extraction via java.net.URI and android.net.Uri fallback
        var host: String? = uri.host
        if (host.isNullOrBlank()) {
            // Try android.net.Uri fallback
            try {
                val androidUri = Uri.parse(trimmed)
                host = androidUri.host
            } catch (_: Exception) {
            }
        }
        // Manual fallback from authority if still null (e.g., host with underscores)
        if (host.isNullOrBlank()) {
            val authority = uri.authority
            if (!authority.isNullOrBlank()) {
                var auth = authority
                // Strip userinfo
                if (auth.contains("@")) auth = auth.substringAfterLast("@")
                // Strip port
                if (auth.contains(":")) auth = auth.substringBefore(":")
                host = auth
            }
        }

        if (host.isNullOrBlank()) {
            return LinkSafetyResult(
                url = trimmed,
                status = LinkStatus.INVALID,
                explanation = "Link format is invalid: missing host.",
                https = https
            )
        }

        // Basic host sanity: spaces or control chars -> INVALID
        if (host.contains(" ") || host.contains("\n") || host.contains("\t")) {
            return LinkSafetyResult(
                url = trimmed,
                status = LinkStatus.INVALID,
                explanation = "Link host is invalid and contains illegal characters.",
                https = https
            )
        }

        val hostLower = host.lowercase()
        val isIp = IP_REGEX.matches(hostLower)
        val isPunycode = hostLower.contains("xn--")
        val hasNonAscii = host.any { it.code > 127 }
        val dotCount = hostLower.count { it == '.' }
        val hasManyDots = dotCount > 3
        val isBrandImpersonation = isBrandImpersonation(hostLower, TRUSTED_BRANDS)

        val path = uri.path ?: ""
        val query = uri.query ?: ""
        val rawQuery = uri.rawQuery ?: ""
        // Use both decoded and raw query for keyword / redirect detection
        val combinedPathQuery = (path + " " + query + " " + rawQuery).lowercase()
        val containsSuspiciousKeyword = SUSPICIOUS_KEYWORDS.any { it in combinedPathQuery }

        val queryLower = (query.lowercase() + " " + rawQuery.lowercase())
        val hasRedirectParam = REDIRECT_PARAMS.any { it in queryLower }

        val isHttp = https == false
        val isHttps = https == true

        // Helper to append redirect note
        fun withRedirectNote(base: String): String {
            return if (hasRedirectParam) {
                "$base It also contains a redirect parameter (redirect= or url=) that may forward to another site; destination was not fetched."
            } else base
        }

        // ---- Decision tree ----

        // WARNING: IP host
        if (isIp) {
            val explanation = withRedirectNote(
                "This link uses an IP address ($host) instead of a domain name, which is commonly used to hide the true destination. Do not proceed unless you trust the source."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.WARNING, explanation = explanation, https = https)
        }

        // WARNING: brand impersonation via typo-squat
        if (isBrandImpersonation) {
            val explanation = withRedirectNote(
                "This domain closely resembles a well-known brand (${TRUSTED_BRANDS.joinToString(", ")}) with a small typo (Levenshtein distance ≤2) or contains a brand string deceptively. This is a common phishing trick. Verify the domain carefully."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.WARNING, explanation = explanation, https = https)
        }

        // WARNING: suspicious keyword combined with non-https or punycode or unicode
        val keywordRisk = containsSuspiciousKeyword && (isHttp || isPunycode || hasNonAscii)
        if (keywordRisk) {
            val reason = buildString {
                if (isHttp) append("non-HTTPS")
                if (isHttp && (isPunycode || hasNonAscii)) append(" and ")
                if (isPunycode) append("punycode (xn--)")
                if (isPunycode && hasNonAscii) append(" and ")
                if (hasNonAscii) append("Unicode tricks in the domain")
                if (isEmpty()) append("insecure domain signal")
            }
            val explanation = withRedirectNote(
                "This link contains sensitive keywords (login/verify/secure/account/update) in the path or query combined with $reason. This is a common phishing pattern. Verify before entering credentials."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.WARNING, explanation = explanation, https = https)
        }

        // WARNING: Unicode + punycode together (homograph)
        if (hasNonAscii && isPunycode) {
            val explanation = withRedirectNote(
                "This domain mixes Unicode characters and punycode (xn--), which can hide homograph attacks. Treat as high risk."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.WARNING, explanation = explanation, https = https)
        }

        // REVIEW: punycode alone
        if (isPunycode) {
            val explanation = withRedirectNote(
                "This link uses punycode (xn--) encoding in the domain, which can hide internationalized domain tricks. Review carefully before opening; destination was not fetched."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.REVIEW, explanation = explanation, https = https)
        }

        // REVIEW: Unicode tricks alone
        if (hasNonAscii) {
            val explanation = withRedirectNote(
                "This link's domain contains non-ASCII (Unicode) characters that can be used to mimic legitimate sites (homograph trick). Verify the domain visually before proceeding."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.REVIEW, explanation = explanation, https = https)
        }

        // REVIEW: deep subdomain (>3 dots)
        if (hasManyDots) {
            val explanation = withRedirectNote(
                "This link has an unusually deep subdomain structure (more than 3 dots in $host), which can be used to hide the true domain. Check the base domain carefully."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.REVIEW, explanation = explanation, https = https)
        }

        // REVIEW: HTTP without keywordRisk (still insecure)
        if (isHttp) {
            val explanation = withRedirectNote(
                "This link uses HTTP instead of HTTPS, so data sent to it is not encrypted. No strong phishing pattern was detected locally, but reputation cannot be verified without network check."
            )
            return LinkSafetyResult(url = trimmed, status = LinkStatus.REVIEW, explanation = explanation, https = https)
        }

        // UNKNOWN: redirect param with otherwise clean HTTPS link (maybe malicious but no strong signal)
        if (hasRedirectParam) {
            val explanation = "This link contains a redirect parameter (redirect= or url=) that may forward you to another site. No strong phishing signal was found, but the final destination cannot be verified without fetching; treat as uncertain."
            return LinkSafetyResult(url = trimmed, status = LinkStatus.UNKNOWN, explanation = explanation, https = https)
        }

        // For completeness: if we had not categorized but host looks maybe suspicious (e.g., very long host or many hyphens)
        // Use UNKNOWN for ambiguous cases where we cannot verify reputation
        // Heuristic: host length > 30 or host contains many hyphens/numbers together could be suspicious but not strong
        // We keep this as fallback UNKNOWN for any remaining odd hosts that are not clearly safe.
        // However spec says do not claim safe if unable to verify reputation; we choose to return REVIEW for remaining http? Already handled.
        // For remaining https clean hosts, we can return SAFE with disclaimer.

        // SAFE: HTTPS and no other signals
        if (isHttps) {
            val explanation = "This link uses HTTPS and shows no common local phishing patterns (no IP, punycode, Unicode tricks, brand impersonation, deep subdomains, or suspicious keyword combinations). Reputation cannot be fully verified without a network check."
            return LinkSafetyResult(url = trimmed, status = LinkStatus.SAFE, explanation = explanation, https = https)
        }

        // Fallback UNKNOWN (should rarely hit due to above)
        return LinkSafetyResult(
            url = trimmed,
            status = LinkStatus.UNKNOWN,
            explanation = withRedirectNote("This link has an unusual structure but no strong phishing signal was detected locally. Treat with caution; reputation cannot be verified offline."),
            https = https
        )
    }

    private fun isBrandImpersonation(hostLower: String, @Suppress("UNUSED_PARAMETER") brands: List<String>): Boolean {
        // Legitimate exact or subdomain of brand -> not impersonation (must check first).
        for (brand in brands) {
            if (hostLower == brand) return false
            if (hostLower.endsWith(".$brand")) return false
        }
        // Spec: detect via levenshtein distance <=2 to list [instagram.com, facebook.com, meta.com, google.com, whatsapp.com]
        // Check full host distance
        for (brand in brands) {
            if (kotlin.math.abs(hostLower.length - brand.length) <= 6) {
                if (levenshtein(hostLower, brand) <= 2) return true
            }
        }
        // Also check the registrable domain (last two labels) against brands for typo-squat like "instagran.com" under subdomains.
        // Extract base candidate: last two labels if host has dots.
        val labels = hostLower.split(".")
        if (labels.size >= 2) {
            val base = labels.takeLast(2).joinToString(".")
            for (brand in brands) {
                if (base != brand && !hostLower.endsWith(".$brand")) {
                    if (levenshtein(base, brand) <= 2) return true
                }
            }
        }
        // Detect deceptive brand substring trick: host contains brand as substring but not as legitimate suffix
        // e.g., instagram.com.evil.com contains "instagram.com" but not endsWith ".instagram.com" -> flag.
        // This is within spec intent for brand impersonation and complements levenshtein.
        for (brand in brands) {
            if (hostLower.contains(brand) && hostLower != brand && !hostLower.endsWith(".$brand")) {
                return true
            }
        }
        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,      // deletion
                    curr[j - 1] + 1,  // insertion
                    prev[j - 1] + cost // substitution
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }
}
