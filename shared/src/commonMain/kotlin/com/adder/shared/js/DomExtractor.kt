package com.adder.shared.js

/**
 * JavaScript code for extracting DOM element metadata from a loaded web page.
 * This script traverses the DOM, identifies candidate elements that could be ads,
 * and returns a JSON array of element descriptors.
 */
object DomExtractor {

    /**
     * Returns the JavaScript code that extracts candidate DOM elements.
     * When evaluated, this JS returns a JSON string of element metadata.
     */
    val extractionScript: String = """
        (function() {
            var elements = [];
            var index = 0;
            
            // Candidate selectors: elements likely to contain ads
            var candidateSelectors = [
                'iframe',
                'ins',
                '[class*="ad"]',
                '[class*="Ad"]',
                '[class*="banner"]',
                '[class*="Banner"]',
                '[class*="sponsor"]',
                '[class*="Sponsor"]',
                '[class*="promo"]',
                '[class*="Promo"]',
                '[id*="ad"]',
                '[id*="Ad"]',
                '[id*="banner"]',
                '[id*="Banner"]',
                '[id*="sponsor"]',
                '[id*="Sponsor"]',
                '[data-ad]',
                '[data-ad-slot]',
                '[data-ad-unit]',
                '[data-adid]',
                '[aria-label*="ad"]',
                '[aria-label*="Ad"]',
                '[aria-label*="sponsor"]',
                '[aria-label*="advertisement"]',
                '[role="banner"]',
                'div[style*="position: fixed"]',
                'div[style*="position:fixed"]',
                'aside',
                'section'
            ];
            
            var candidateElements = new Set();
            
            candidateSelectors.forEach(function(selector) {
                try {
                    var found = document.querySelectorAll(selector);
                    found.forEach(function(el) {
                        candidateElements.add(el);
                    });
                } catch(e) {}
            });
            
            // Also check elements with common ad sizes
            var allDivs = document.querySelectorAll('div, section, aside, article');
            allDivs.forEach(function(el) {
                var rect = el.getBoundingClientRect();
                // Common ad sizes
                if ((rect.width === 728 && rect.height === 90) ||
                    (rect.width === 300 && rect.height === 250) ||
                    (rect.width === 336 && rect.height === 280) ||
                    (rect.width === 320 && rect.height === 50) ||
                    (rect.width === 320 && rect.height === 100) ||
                    (rect.width === 160 && rect.height === 600) ||
                    (rect.width === 970 && rect.height === 250) ||
                    (rect.width === 1 && rect.height === 1)) {
                    candidateElements.add(el);
                }
            });
            
            candidateElements.forEach(function(el) {
                // Skip invisible elements
                var style = window.getComputedStyle(el);
                if (style.display === 'none' && style.visibility === 'hidden') {
                    return;
                }
                
                var rect = el.getBoundingClientRect();
                var parent = el.parentElement;
                
                // Build a unique CSS selector for this element
                var selector = buildSelector(el);
                
                // Get data-* attributes related to ads
                var dataAttrs = '';
                Array.from(el.attributes).forEach(function(attr) {
                    if (attr.name.startsWith('data-ad') || 
                        attr.name.startsWith('data-sponsor') ||
                        attr.name.startsWith('data-track')) {
                        dataAttrs += attr.name + '=' + attr.value + '; ';
                    }
                });
                
                // Get truncated text content
                var textContent = (el.innerText || '').substring(0, 200).trim();
                
                elements.push({
                    index: index++,
                    tag: el.tagName.toLowerCase(),
                    id: el.id || '',
                    classes: el.className || '',
                    src: el.getAttribute('src') || '',
                    href: el.getAttribute('href') || '',
                    textSnippet: textContent,
                    width: Math.round(rect.width),
                    height: Math.round(rect.height),
                    parentTag: parent ? parent.tagName.toLowerCase() : '',
                    parentClasses: parent ? (parent.className || '') : '',
                    selector: selector,
                    ariaLabel: el.getAttribute('aria-label') || el.getAttribute('role') || '',
                    dataAttributes: dataAttrs.trim()
                });
            });
            
            return JSON.stringify(elements);
            
            function buildSelector(el) {
                if (el.id) {
                    return '#' + CSS.escape(el.id);
                }
                var path = [];
                var current = el;
                while (current && current !== document.body && path.length < 5) {
                    var tag = current.tagName.toLowerCase();
                    if (current.id) {
                        path.unshift('#' + CSS.escape(current.id) + ' > ' + tag);
                        break;
                    }
                    var siblings = current.parentElement ? 
                        Array.from(current.parentElement.children).filter(function(c) {
                            return c.tagName === current.tagName;
                        }) : [];
                    if (siblings.length > 1) {
                        var idx = siblings.indexOf(current) + 1;
                        tag += ':nth-of-type(' + idx + ')';
                    }
                    path.unshift(tag);
                    current = current.parentElement;
                }
                return path.join(' > ');
            }
        })();
    """.trimIndent()
}
