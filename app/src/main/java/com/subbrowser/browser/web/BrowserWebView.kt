package com.subbrowser.browser.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.subbrowser.browser.BrowserController

@SuppressLint("SetJavaScriptEnabled")
fun configureBrowserWebView(
    webView: WebView,
    controller: BrowserController,
) {
    webView.setBackgroundColor(android.graphics.Color.BLACK)
    webView.overScrollMode = WebView.OVER_SCROLL_NEVER

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = false
        loadsImagesAutomatically = true
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(false)
        builtInZoomControls = false
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        safeBrowsingEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            controller.onNavigationStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            controller.onNavigationFinished(url, view.title.orEmpty())
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail,
        ): Boolean {
            runCatching { view.stopLoading() }
            runCatching { view.destroy() }
            controller.onRendererCrashed()
            return true
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            controller.onProgressChanged(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            controller.onTitleChanged(title)
        }
    }
}
