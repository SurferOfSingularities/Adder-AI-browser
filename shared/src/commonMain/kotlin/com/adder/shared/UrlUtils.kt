package com.adder.shared

object UrlUtils {
    /**
     * Normalizes user input into a valid URL.
     * - Adds "https://" if no scheme is present
     * - If input looks like a search query (no dots, has spaces), returns a Google search URL
     */
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"

        // Already has a scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        // Looks like a domain (contains a dot, no spaces)
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }

        // Treat as a search query
        return "https://www.google.com/search?q=${trimmed.replace(" ", "+")}"
    }
}
