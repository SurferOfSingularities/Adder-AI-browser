package com.adder.shared.detection

import com.adder.shared.model.DomElement

/**
 * Builds prompts for the on-device LLM to classify ambiguous DOM elements.
 * Used by both Android (Gemini Nano) and iOS (Apple Foundation Models).
 */
object PromptBuilder {

    /**
     * Build a classification prompt for a batch of ambiguous elements.
     * The LLM should respond with a JSON array of booleans (true = ad, false = not ad).
     */
    fun buildBatchClassificationPrompt(elements: List<DomElement>): String {
        val elementDescriptions = elements.mapIndexed { index, element ->
            buildElementDescription(index, element)
        }.joinToString("\n\n")

        return """
You are an ad detection system. Analyze the following HTML elements extracted from a web page and determine which ones are advertisements, sponsored content, or promotional material.

For each element, respond with ONLY "ad" or "not_ad" on a separate line, in the same order as presented.

Rules:
- Ads include: banner ads, sponsored posts, promotional widgets, "recommended" content from ad networks, tracking elements, newsletter popups promoting products
- NOT ads: navigation menus, article content, legitimate recommended articles from the same site, social media embeds, video players, login forms, cookie notices

Elements to classify:

$elementDescriptions

Respond with one classification per line (ad or not_ad):
        """.trimIndent()
    }

    /**
     * Build a classification prompt for a single element.
     */
    fun buildSingleClassificationPrompt(element: DomElement): String {
        val description = buildElementDescription(0, element)

        return """
You are an ad detection system. Analyze this HTML element and determine if it is an advertisement, sponsored content, or promotional material.

Respond with ONLY "ad" or "not_ad".

Rules:
- Ads include: banner ads, sponsored posts, promotional widgets, "recommended" content from ad networks, tracking elements
- NOT ads: navigation menus, article content, legitimate recommended articles from the same site, social media embeds, video players

Element:

$description

Classification (ad or not_ad):
        """.trimIndent()
    }

    /**
     * Parse the LLM's batch response into a list of boolean classifications.
     * Returns true for "ad", false for "not_ad".
     */
    fun parseBatchResponse(response: String, expectedCount: Int): List<Boolean> {
        val lines = response.trim().lines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        return (0 until expectedCount).map { index ->
            val line = lines.getOrNull(index) ?: ""
            line.contains("ad") && !line.contains("not_ad") && !line.contains("not ad")
        }
    }

    /**
     * Parse a single element LLM response.
     */
    fun parseSingleResponse(response: String): Boolean {
        val cleaned = response.trim().lowercase()
        return cleaned.contains("ad") && !cleaned.contains("not_ad") && !cleaned.contains("not ad")
    }

    private fun buildElementDescription(index: Int, element: DomElement): String {
        val parts = mutableListOf<String>()
        parts.add("Element ${index + 1}:")
        parts.add("  Tag: <${element.tag}>")
        if (element.id.isNotEmpty()) parts.add("  ID: ${element.id}")
        if (element.classes.isNotEmpty()) parts.add("  Classes: ${element.classes.take(200)}")
        if (element.src.isNotEmpty()) parts.add("  Src: ${element.src.take(200)}")
        if (element.href.isNotEmpty()) parts.add("  Href: ${element.href.take(200)}")
        if (element.textSnippet.isNotEmpty()) parts.add("  Text: ${element.textSnippet.take(150)}")
        parts.add("  Size: ${element.width}x${element.height}")
        if (element.ariaLabel.isNotEmpty()) parts.add("  Aria/Role: ${element.ariaLabel}")
        if (element.dataAttributes.isNotEmpty()) parts.add("  Data attrs: ${element.dataAttributes.take(150)}")
        if (element.parentClasses.isNotEmpty()) parts.add("  Parent classes: ${element.parentClasses.take(100)}")
        return parts.joinToString("\n")
    }
}
