package com.adder.shared.js

/**
 * JavaScript code for removing identified ad elements from the DOM.
 * Supports removal by CSS selector, with smooth fade-out animation
 * and a MutationObserver for dynamically loaded ads.
 */
object DomRemover {

    /**
     * Builds a JavaScript snippet that removes elements matching the given selectors.
     * Elements are hidden with a fade-out animation, then removed from the DOM.
     *
     * @param selectors List of CSS selectors identifying elements to remove
     */
    fun buildRemovalScript(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""

        val selectorsJson = selectors.joinToString(",") { "\"${escapeJs(it)}\"" }

        return """
            (function() {
                var selectors = [$selectorsJson];
                var removed = 0;
                
                selectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(el) {
                            hideElement(el);
                            removed++;
                        });
                    } catch(e) {
                        // Invalid selector, skip
                    }
                });
                
                return removed.toString();
                
                function hideElement(el) {
                    // Smooth fade-out
                    el.style.transition = 'opacity 0.3s ease-out, max-height 0.3s ease-out';
                    el.style.opacity = '0';
                    el.style.overflow = 'hidden';
                    el.style.maxHeight = el.offsetHeight + 'px';
                    
                    setTimeout(function() {
                        el.style.maxHeight = '0';
                        el.style.margin = '0';
                        el.style.padding = '0';
                        el.style.border = 'none';
                    }, 150);
                    
                    setTimeout(function() {
                        el.style.display = 'none';
                        // Remove from DOM to free resources
                        if (el.parentNode) {
                            el.parentNode.removeChild(el);
                        }
                    }, 400);
                }
            })();
        """.trimIndent()
    }

    /**
     * JavaScript that installs a MutationObserver to detect dynamically added ad elements.
     * When new elements matching ad patterns are added to the DOM, they are automatically hidden.
     *
     * @param patterns List of class/id patterns to watch for (substring matches)
     */
    fun buildMutationObserverScript(patterns: List<String>): String {
        if (patterns.isEmpty()) return ""

        val patternsJson = patterns.joinToString(",") { "\"${escapeJs(it)}\"" }

        return """
            (function() {
                if (window.__adderObserverInstalled) return 'already_installed';
                window.__adderObserverInstalled = true;
                
                var patterns = [$patternsJson];
                
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType !== 1) return; // Only element nodes
                            
                            if (isAdElement(node)) {
                                hideElement(node);
                            }
                            
                            // Also check children of added nodes
                            var children = node.querySelectorAll ? 
                                node.querySelectorAll('*') : [];
                            children.forEach(function(child) {
                                if (isAdElement(child)) {
                                    hideElement(child);
                                }
                            });
                        });
                    });
                });
                
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
                
                return 'observer_installed';
                
                function isAdElement(el) {
                    var classes = (el.className || '').toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var src = (el.getAttribute('src') || '').toLowerCase();
                    var combined = classes + ' ' + id + ' ' + src;
                    
                    for (var i = 0; i < patterns.length; i++) {
                        if (combined.indexOf(patterns[i]) !== -1) {
                            return true;
                        }
                    }
                    return false;
                }
                
                function hideElement(el) {
                    el.style.transition = 'opacity 0.2s ease-out';
                    el.style.opacity = '0';
                    setTimeout(function() {
                        el.style.display = 'none';
                        if (el.parentNode) {
                            el.parentNode.removeChild(el);
                        }
                    }, 250);
                }
            })();
        """.trimIndent()
    }

    /**
     * Default patterns for the MutationObserver to watch for dynamically loaded ads.
     */
    val defaultObserverPatterns = listOf(
        "adsbygoogle",
        "ad-slot",
        "ad-unit",
        "ad-container",
        "ad-wrapper",
        "doubleclick",
        "googlesyndication",
        "sponsored",
        "taboola",
        "outbrain"
    )

    private fun escapeJs(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
