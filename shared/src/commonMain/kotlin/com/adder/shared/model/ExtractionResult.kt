package com.adder.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wrapper for the list of extracted DOM elements from a page.
 */
@Serializable
data class ExtractionResult(
    val elements: List<DomElement>,
    val url: String = "",
    val elementCount: Int = elements.size
)

/**
 * JSON parser configured for DOM element deserialization.
 * Ignores unknown keys for forward compatibility.
 */
val domJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Parses the JSON string returned by the extraction JavaScript into a list of DomElements.
 */
fun parseExtractedElements(jsonString: String): List<DomElement> {
    if (jsonString.isBlank() || jsonString == "null" || jsonString == "[]") {
        return emptyList()
    }
    return try {
        domJson.decodeFromString<List<DomElement>>(jsonString)
    } catch (e: Exception) {
        emptyList()
    }
}
