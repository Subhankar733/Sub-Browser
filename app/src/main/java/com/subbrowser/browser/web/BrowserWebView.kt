package com.subbrowser.browser.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.subbrowser.browser.BrowserController

@SuppressLint("SetJavaScriptEnabled")
fun configureBrowserWebView(
    webView: WebView,
    controller: BrowserController,
) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(false)
        builtInZoomControls = false
        displayZoomControls = false
        safeBrowsingEnabled = true
        allowFileAccess = false
        allowContentAccess = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportZoom(true)
    }

    CookieManager.getInstance().setAcceptCookie(true)

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            val scheme = uri.scheme.orEmpty().lowercase()

            if (scheme == "http" || scheme == "https") return false

            return runCatching {
                view.context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                )
                true
            }.getOrDefault(true)
        }

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            controller.onNavigationStarted(url)
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            controller.onNavigationFinished(url, view.title.orEmpty())
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                controller.onError(error.description?.toString())
            }
        }

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponse,
        ) {
            callback.backToSafety(true)
            controller.onError("Unsafe content was blocked")
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail,
        ): Boolean {
            controller.onRendererCrashed()
            view.destroy()
            return true
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            controller.onProgressChanged(newProgress)
            controller.sync()
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            controller.onTitleChanged(title)
        }
    }

    webView.setDownloadListener(
        DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setTitle(
                    android.webkit.URLUtil.guessFileName(
                        url,
                        contentDisposition,
                        mimeType,
                    )
                )
                setDescription("Download from Sub Browser")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    android.webkit.URLUtil.guessFileName(
                        url,
                        contentDisposition,
                        mimeType,
                    )
                )
                addRequestHeader("User-Agent", userAgent)
            }

            runCatching {
                val manager =
                    webView.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
            }.onFailure {
                controller.onError("Download could not be started")
            }
        }
    )

    controller.attach(webView)

    if (WebViewFeature.isFeatureSupported(WebViewFeature.STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX)) {
        // Feature probe retained intentionally; per-profile data-directory selection
        // will be added when the multi-profile/session layer is introduced.
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        // Keep web-message support gated by WebViewFeature when browser/web messaging is added.
    }

    if (WebViewCompat.getCurrentWebViewPackage(webView.context) == null) {
        controller.onError("Android WebView provider is unavailable")
    }
}
