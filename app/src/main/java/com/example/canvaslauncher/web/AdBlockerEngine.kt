package com.example.canvaslauncher.web

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlockerEngine {

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "analytics.google.com",
        "google-analytics.com",
        "googletagmanager.com",
        "adnxs.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "amazon-adsystem.com",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "zedo.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "mgid.com",
        "revcontent.com",
        "teads.tv",
        "sharethrough.com",
        "moatads.com",
        "doubleverify.com",
        "ads-twitter.com",
        "connect.facebook.net"
    )

    private val blockedPathKeywords = listOf(
        "/pagead/",
        "/adsid/",
        "/adserver",
        "/adsystem",
        "/bannerad",
        "/popunder",
        "/sponsored_ad",
        "/tr?id="
    )

    fun isAdOrTrackerUrl(urlString: String): Boolean {
        return try {
            val uri = Uri.parse(urlString)
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path?.lowercase() ?: ""

            if (blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }) {
                return true
            }
            if (blockedPathKeywords.any { kw -> path.contains(kw) }) {
                return true
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun createEmptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Iniezione CSS cosmetica stile Brave Shields per nascondere riquadri pubblicitari residui.
     */
    const val COSMETIC_AD_HIDE_JS = """
        (function() {
            try {
                var style = document.createElement('style');
                style.innerHTML = 'ins.adsbygoogle, [id^="div-gpt-ad"], [class*="ad-banner"], [class*="sponsored-ad"], .advertisement, .ad-container, .adsbygoogle { display: none !important; height: 0 !important; }';
                document.head && document.head.appendChild(style);
            } catch (e) {}
        })();
    """
}
