package com.adder.shared

object UrlUtils {
    /**
     * Normalizes user input into a valid URL.
     * - Adds "https://" if no scheme is present
     * - If input looks like a search query (no dots, has spaces), returns null
     */
    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Already has a scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        // Looks like a domain (contains a dot, no spaces)
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }

        // Doesn't look like a URL — could be treated as a search query in future
        return null
    }
}
