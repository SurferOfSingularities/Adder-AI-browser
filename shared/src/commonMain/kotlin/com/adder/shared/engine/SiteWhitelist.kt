package com.adder.shared.engine

/**
 * Manages a list of whitelisted domains where ad blocking is disabled.
 * In-memory only for MVP — could be persisted to SharedPreferences/UserDefaults later.
 */
class SiteWhitelist {

    private val whitelist = mutableSetOf<String>()

    /**
     * Add a domain to the whitelist. Strips "www." prefix for consistency.
     */
    fun add(domain: String) {
        whitelist.add(normalizeDomain(domain))
    }

    /**
     * Remove a domain from the whitelist.
     */
    fun remove(domain: String) {
        whitelist.remove(normalizeDomain(domain))
    }

    /**
     * Check if a URL's domain is whitelisted.
     */
    fun isWhitelisted(url: String): Boolean {
        val domain = extractDomain(url) ?: return false
        return whitelist.contains(normalizeDomain(domain))
    }

    /**
     * Get all whitelisted domains.
     */
    fun getAll(): Set<String> = whitelist.toSet()

    private fun normalizeDomain(domain: String): String {
        return domain.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun extractDomain(url: String): String? {
        val withoutScheme = url
            .removePrefix("http://")
            .removePrefix("https://")
        val slashIndex = withoutScheme.indexOf('/')
        return if (slashIndex > 0) {
            withoutScheme.substring(0, slashIndex)
        } else {
            withoutScheme.ifEmpty { null }
        }
    }
}
