package com.adder.shared.model

import kotlinx.serialization.Serializable

/**
 * Represents a DOM element extracted from a web page for ad classification.
 */
@Serializable
data class DomElement(
    /** Unique identifier for this element (index in the extraction list) */
    val index: Int,
    /** HTML tag name (e.g., "div", "iframe", "section") */
    val tag: String,
    /** Element id attribute, empty if none */
    val id: String = "",
    /** Space-separated list of CSS classes */
    val classes: String = "",
    /** src attribute (for iframes, images, scripts) */
    val src: String = "",
    /** href attribute (for links) */
    val href: String = "",
    /** Truncated inner text content (first 200 chars) */
    val textSnippet: String = "",
    /** Element width in pixels */
    val width: Int = 0,
    /** Element height in pixels */
    val height: Int = 0,
    /** Parent element's tag name */
    val parentTag: String = "",
    /** Parent element's classes */
    val parentClasses: String = "",
    /** CSS selector path to uniquely identify this element */
    val selector: String = "",
    /** aria-label or role attribute if present */
    val ariaLabel: String = "",
    /** data-ad or similar data attributes */
    val dataAttributes: String = ""
)
