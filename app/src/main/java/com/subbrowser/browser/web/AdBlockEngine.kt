package com.subbrowser.browser.web

import android.net.Uri

object AdBlockEngine {
    private val adDomains = hashSetOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "adnxs.com",
        "popads.net",
        "adroll.com",
        "criteo.com",
        "outbrain.com",
        "taboola.com",
        "amazon-adsystem.com"
    )

    fun isAd(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            adDomains.any { host.contains(it) }
        } catch (e: Exception) {
            false
        }
    }
}
