package com.subbrowser.browser.web

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.subbrowser.browser.BrowserController

fun configureBrowserWebView(
    webView: WebView,
    controller: BrowserController,
) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = false
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        allowFileAccess = false
        allowContentAccess = true
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
        WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String) {
            controller.onTitleChanged(title)
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            controller.attach(webView)
            controller.onNavigationStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            controller.onNavigationFinished(url, view.title.orEmpty().ifBlank { "New Tab" })
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail,
        ): Boolean {
            controller.onRendererCrashed()
            runCatching { view.destroy() }
            return true
        }

    }

    webView.setDownloadListener { _, _, _, _, _ ->
        // Download routing is deliberately kept out of the workspace UI layer.
        // A dedicated download manager will be added as a separate feature.
    }
}
