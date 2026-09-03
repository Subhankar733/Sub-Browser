package com.subbrowser.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
fun createBrowserWebView(
    webView: WebView,
    controller: BrowserController
): WebView {
    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        builtInZoomControls = false
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(false)
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = false

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?
        ) {
            controller.sync()
        }

        override fun onPageFinished(
            view: WebView,
            url: String
        ) {
            controller.sync()
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(
            view: WebView,
            newProgress: Int
        ) {
            controller.updateLoading(newProgress)
            controller.sync()
        }

        override fun onReceivedTitle(
            view: WebView,
            title: String
        ) {
            controller.sync()
        }
    }

    controller.attach(webView)

    if (webView.url == null) {
        webView.loadUrl("https://www.google.com")
    }

    return webView
}
