package com.subbrowser.browser.web

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
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

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            controller.onProgressChanged(newProgress)
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (AdBlockEngine.isAd(request.url.toString())) {
                return WebResourceResponse("text/plain", "UTF-8", null)
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
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

    webView.setDownloadListener { _, _, _, _, _ -> }
}
