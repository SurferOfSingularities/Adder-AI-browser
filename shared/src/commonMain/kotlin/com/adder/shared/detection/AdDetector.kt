package com.adder.shared.detection

import com.adder.shared.model.DomElement

/**
 * Heuristic-based ad detector. Classifies DOM elements using pattern matching
 * on class names, IDs, src attributes, dimensions, and data attributes.
 *
 * Elements classified as DEFINITE_AD or LIKELY_AD are removed immediately.
 * AMBIGUOUS elements are forwarded to the LLM for intelligent classification.
 * NOT_AD elements are left untouched.
 */
class AdDetector {

    /**
     * Classify a list of extracted DOM elements using heuristics.
     */
    fun classify(elements: List<DomElement>): List<ClassificationResult> {
        return elements.map { classifyElement(it) }
    }

    /**
     * Classify a single DOM element.
     */
    fun classifyElement(element: DomElement): ClassificationResult {
        // Check rules in order of confidence (highest first)

        // Rule 1: Known ad network domains in src/href
        checkAdNetworkDomain(element)?.let { return it }

        // Rule 2: Tracking pixels (1x1 elements)
        checkTrackingPixel(element)?.let { return it }

        // Rule 3: Explicit ad data attributes
        checkDataAttributes(element)?.let { return it }

        // Rule 4: Strong ad class/ID patterns (definite)
        checkDefiniteAdPatterns(element)?.let { return it }

        // Rule 5: Common ad sizes
        checkAdDimensions(element)?.let { return it }

        // Rule 6: Weak ad signals (likely/ambiguous)
        checkWeakAdSignals(element)?.let { return it }

        // Rule 7: Iframe with no obvious purpose
        checkSuspiciousIframe(element)?.let { return it }

        // Default: not an ad (or at least we can't tell)
        return ClassificationResult(element, AdClassification.NOT_AD, "No ad signals detected")
    }

    // ---------------------------------------------------------------
    // Rule implementations
    // ---------------------------------------------------------------

    private fun checkAdNetworkDomain(element: DomElement): ClassificationResult? {
        val src = element.src.lowercase()
        val href = element.href.lowercase()
        val combined = "$src $href"

        for (domain in AD_NETWORK_DOMAINS) {
            if (combined.contains(domain)) {
                return ClassificationResult(
                    element,
                    AdClassification.DEFINITE_AD,
                    "Ad network domain detected: $domain"
                )
            }
        }
        return null
    }

    private fun checkTrackingPixel(element: DomElement): ClassificationResult? {
        if (element.width <= 1 && element.height <= 1 && element.width >= 0 && element.height >= 0) {
            if (element.tag == "img" || element.tag == "iframe" || element.src.isNotEmpty()) {
                return ClassificationResult(
                    element,
                    AdClassification.DEFINITE_AD,
                    "Tracking pixel (${element.width}x${element.height})"
                )
            }
        }
        return null
    }

    private fun checkDataAttributes(element: DomElement): ClassificationResult? {
        val data = element.dataAttributes.lowercase()
        if (data.isNotEmpty()) {
            for (pattern in DEFINITE_DATA_PATTERNS) {
                if (data.contains(pattern)) {
                    return ClassificationResult(
                        element,
                        AdClassification.DEFINITE_AD,
                        "Ad data attribute: $pattern"
                    )
                }
            }
        }
        return null
    }

    private fun checkDefiniteAdPatterns(element: DomElement): ClassificationResult? {
        val classes = element.classes.lowercase()
        val id = element.id.lowercase()
        val combined = "$classes $id"

        for (pattern in DEFINITE_AD_PATTERNS) {
            if (combined.contains(pattern)) {
                // Make sure we don't false-positive on words like "address" or "loading"
                if (isFalsePositive(combined, pattern)) continue

                return ClassificationResult(
                    element,
                    AdClassification.DEFINITE_AD,
                    "Strong ad pattern in class/id: $pattern"
                )
            }
        }
        return null
    }

    private fun checkAdDimensions(element: DomElement): ClassificationResult? {
        val size = Pair(element.width, element.height)
        if (COMMON_AD_SIZES.contains(size)) {
            return ClassificationResult(
                element,
                AdClassification.LIKELY_AD,
                "Common ad dimensions: ${element.width}x${element.height}"
            )
        }
        return null
    }

    private fun checkWeakAdSignals(element: DomElement): ClassificationResult? {
        val classes = element.classes.lowercase()
        val id = element.id.lowercase()
        val combined = "$classes $id"
        val text = element.textSnippet.lowercase()
        val aria = element.ariaLabel.lowercase()

        var signals = 0
        val reasons = mutableListOf<String>()

        for (pattern in WEAK_AD_PATTERNS) {
            if (combined.contains(pattern)) {
                signals++
                reasons.add("class/id: $pattern")
            }
        }

        if (aria.contains("advertisement") || aria.contains("sponsored")) {
            signals += 2
            reasons.add("aria: $aria")
        }

        if (text.contains("advertisement") || text.contains("sponsored content")) {
            signals++
            reasons.add("text content signals ad")
        }

        return when {
            signals >= 3 -> ClassificationResult(
                element,
                AdClassification.LIKELY_AD,
                "Multiple weak ad signals: ${reasons.joinToString(", ")}"
            )
            signals >= 1 -> ClassificationResult(
                element,
                AdClassification.AMBIGUOUS,
                "Some ad signals: ${reasons.joinToString(", ")}"
            )
            else -> null
        }
    }

    private fun checkSuspiciousIframe(element: DomElement): ClassificationResult? {
        if (element.tag != "iframe") return null

        val src = element.src.lowercase()

        // Iframes with no src or about:blank are often ad placeholders
        if (src.isEmpty() || src == "about:blank") {
            return ClassificationResult(
                element,
                AdClassification.AMBIGUOUS,
                "Iframe with no/blank src"
            )
        }

        // Iframes from different domains are suspicious
        // (we can't fully check cross-origin here, but src containing known CDNs is fine)
        if (src.startsWith("http") && !KNOWN_SAFE_IFRAME_DOMAINS.any { src.contains(it) }) {
            return ClassificationResult(
                element,
                AdClassification.AMBIGUOUS,
                "Cross-origin iframe: $src"
            )
        }

        return null
    }

    // ---------------------------------------------------------------
    // False positive protection
    // ---------------------------------------------------------------

    private fun isFalsePositive(combined: String, matchedPattern: String): Boolean {
        // "ad" can match "address", "loading", "upload", "download", "shade", etc.
        if (matchedPattern == "ad-" || matchedPattern == "ad_") {
            for (safe in FALSE_POSITIVE_WORDS) {
                if (combined.contains(safe)) return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------
    // Pattern databases
    // ---------------------------------------------------------------

    companion object {
        /** Known ad network domains — presence in src/href means definite ad */
        val AD_NETWORK_DOMAINS = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com/collect",
            "adservice.google",
            "pagead2.googlesyndication",
            "adsense",
            "adnxs.com",
            "advertising.com",
            "adcolony.com",
            "admob.com",
            "facebook.com/tr",
            "amazon-adsystem.com",
            "criteo.com",
            "outbrain.com",
            "taboola.com",
            "pubmatic.com",
            "rubiconproject.com",
            "openx.net",
            "moat.com",
            "serving-sys.com",
            "2mdn.net",
            "adsrvr.org",
            "adroll.com",
            "quantserve.com",
            "scorecardresearch.com"
        )

        /** Strong patterns in class/id that almost certainly indicate ads */
        val DEFINITE_AD_PATTERNS = listOf(
            "ad-container",
            "ad-wrapper",
            "ad-slot",
            "ad-unit",
            "ad-banner",
            "ad-leaderboard",
            "ad-rectangle",
            "ad-skyscraper",
            "adsbygoogle",
            "adsbox",
            "advert-",
            "advertisement",
            "google-ad",
            "dfp-ad",
            "gpt-ad",
            "doubleclick",
            "sponsored-post",
            "sponsored-content",
            "native-ad",
            "promoted-content",
            "ad-placement",
            "ad-zone"
        )

        /** Weaker patterns that need multiple signals */
        val WEAK_AD_PATTERNS = listOf(
            "ad-",
            "ad_",
            "ads-",
            "ads_",
            "sponsor",
            "promo",
            "banner",
            "commercial",
            "monetiz",
            "outbrain",
            "taboola",
            "recommended-for-you",
            "around-the-web"
        )

        /** Data attribute patterns that indicate ads */
        val DEFINITE_DATA_PATTERNS = listOf(
            "data-ad-slot",
            "data-ad-unit",
            "data-ad-client",
            "data-adid",
            "data-ad-region",
            "data-sponsor"
        )

        /** Common ad unit sizes (width, height) */
        val COMMON_AD_SIZES = setOf(
            Pair(728, 90),   // Leaderboard
            Pair(300, 250),  // Medium Rectangle
            Pair(336, 280),  // Large Rectangle
            Pair(320, 50),   // Mobile Leaderboard
            Pair(320, 100),  // Large Mobile Banner
            Pair(160, 600),  // Wide Skyscraper
            Pair(300, 600),  // Half Page
            Pair(970, 250),  // Billboard
            Pair(970, 90),   // Large Leaderboard
            Pair(468, 60),   // Full Banner
            Pair(234, 60),   // Half Banner
            Pair(120, 600),  // Skyscraper
            Pair(250, 250),  // Square
            Pair(200, 200),  // Small Square
            Pair(300, 1050)  // Portrait
        )

        /** Words that contain "ad" but are not ads */
        val FALSE_POSITIVE_WORDS = listOf(
            "address",
            "loading",
            "upload",
            "download",
            "shade",
            "shadow",
            "gradient",
            "padding",
            "heading",
            "breadcrumb",
            "addon",
            "badge",
            "readme",
            "thread",
            "spread",
            "dashboard"
        )

        /** Iframe domains that are known safe (not ads) */
        val KNOWN_SAFE_IFRAME_DOMAINS = listOf(
            "youtube.com",
            "youtube-nocookie.com",
            "vimeo.com",
            "player.vimeo.com",
            "maps.google.com",
            "google.com/maps",
            "twitter.com",
            "platform.twitter.com",
            "instagram.com",
            "spotify.com",
            "soundcloud.com",
            "codepen.io",
            "jsfiddle.net",
            "codesandbox.io",
            "github.com",
            "gist.github.com"
        )
    }
}
