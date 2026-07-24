package com.adder.shared.js

/**
 * Injects CSS rules early in page load to immediately hide elements matching
 * common ad selectors. This reduces the "flash of ad content" before the
 * full pipeline runs.
 *
 * Elements are hidden with visibility:hidden (not display:none) so they
 * still occupy space — preventing layout shifts. Once the pipeline confirms
 * they're ads, they'll be fully removed.
 */
object EarlyCssInjector {

    /**
     * CSS rules that hide common ad elements immediately.
     * Uses visibility:hidden + opacity:0 to prevent flash without layout shift.
     */
    val earlyHideCss: String = """
        (function() {
            if (document.getElementById('__adder_early_css')) return 'already_injected';
            
            var style = document.createElement('style');
            style.id = '__adder_early_css';
            style.textContent = `
                /* Known ad containers */
                [class*="adsbygoogle"],
                [class*="ad-container"],
                [class*="ad-wrapper"],
                [class*="ad-slot"],
                [class*="ad-unit"],
                [class*="ad-banner"],
                [id*="google_ads"],
                [id*="dfp-ad"],
                [id*="gpt-ad"],
                ins.adsbygoogle,
                [data-ad-slot],
                [data-ad-unit],
                [data-ad-client] {
                    visibility: hidden !important;
                    opacity: 0 !important;
                    transition: opacity 0.2s ease-out;
                }
                
                /* Known ad iframes by src */
                iframe[src*="doubleclick.net"],
                iframe[src*="googlesyndication"],
                iframe[src*="adservice.google"],
                iframe[src*="amazon-adsystem"],
                iframe[src*="taboola"],
                iframe[src*="outbrain"] {
                    visibility: hidden !important;
                    opacity: 0 !important;
                }
                
                /* Tracking pixels */
                img[width="1"][height="1"],
                img[style*="width:1px"][style*="height:1px"] {
                    display: none !important;
                }
            `;
            
            // Inject as early as possible
            (document.head || document.documentElement).appendChild(style);
            return 'injected';
        })();
    """.trimIndent()

    /**
     * Removes the early-hide CSS for elements confirmed NOT to be ads.
     * This "unhides" elements that were initially hidden but passed classification.
     *
     * @param selectors CSS selectors of elements to unhide
     */
    fun buildUnhideScript(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""

        val selectorsJson = selectors.joinToString(",") { "\"${escapeJs(it)}\"" }

        return """
            (function() {
                var selectors = [$selectorsJson];
                selectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(el) {
                            el.style.visibility = '';
                            el.style.opacity = '';
                        });
                    } catch(e) {}
                });
                return selectors.length.toString();
            })();
        """.trimIndent()
    }

    private fun escapeJs(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
