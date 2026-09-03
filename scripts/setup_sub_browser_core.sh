#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== Sub Browser: browser core functionality =="

mkdir -p app/src/main/java/com/subbrowser/browser/{model,session,web}

cat > app/src/main/java/com/subbrowser/browser/model/BrowserState.kt <<'EOF'
package com.subbrowser.browser.model

data class BrowserState(
    val url: String = "about:blank",
    val title: String = "New Tab",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secureConnection: Boolean = false,
    val rendererCrashed: Boolean = false,
    val errorMessage: String? = null,
)
EOF

cat > app/src/main/java/com/subbrowser/browser/BrowserController.kt <<'EOF'
package com.subbrowser.browser

import android.net.Uri
import android.webkit.WebView
import com.subbrowser.browser.model.BrowserState
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BrowserController {
    private var webView: WebView? = null
    private var listener: ((BrowserState) -> Unit)? = null
    private var currentState = BrowserState()

    fun observe(listener: (BrowserState) -> Unit) {
        this.listener = listener
        listener(currentState)
    }

    fun clearObserver() {
        listener = null
    }

    fun attach(view: WebView) {
        webView = view
        sync(
            url = view.url ?: "about:blank",
            title = view.title ?: "New Tab",
            loading = false,
            progress = 0,
        )
    }

    fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return

        val target = when {
            value.equals("about:blank", ignoreCase = true) -> "about:blank"
            value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("www.", ignoreCase = true) -> "https://$value"
            isHostLike(value) -> "https://$value"
            else -> "https://www.google.com/search?q=${encode(value)}"
        }

        webView?.loadUrl(target)
    }

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun stop() {
        webView?.stopLoading()
    }

    fun onNavigationStarted(url: String) {
        publish(
            currentState.copy(
                url = url,
                loading = true,
                progress = 0,
                secureConnection = isHttps(url),
                rendererCrashed = false,
                errorMessage = null,
            )
        )
    }

    fun onNavigationFinished(url: String, title: String) {
        sync(
            url = url,
            title = title.ifBlank { "New Tab" },
            loading = false,
            progress = 100,
        )
    }

    fun onProgressChanged(progress: Int) {
        publish(
            currentState.copy(
                loading = progress < 100,
                progress = progress.coerceIn(0, 100),
            )
        )
    }

    fun onTitleChanged(title: String) {
        publish(currentState.copy(title = title.ifBlank { "New Tab" }))
    }

    fun onError(message: String?) {
        publish(
            currentState.copy(
                loading = false,
                errorMessage = message ?: "Unable to load this page",
            )
        )
    }

    fun sync(
        url: String? = null,
        title: String? = null,
        loading: Boolean? = null,
        progress: Int? = null,
    ) {
        val view = webView ?: return
        publish(
            currentState.copy(
                url = url ?: view.url ?: "about:blank",
                title = title ?: view.title.orEmpty().ifBlank { "New Tab" },
                loading = loading ?: currentState.loading,
                progress = progress ?: currentState.progress,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward(),
                secureConnection = isHttps(url ?: view.url.orEmpty()),
            )
        )
    }

    fun onRendererCrashed() {
        webView = null
        publish(
            currentState.copy(
                loading = false,
                progress = 0,
                rendererCrashed = true,
                errorMessage = "The page renderer stopped unexpectedly.",
            )
        )
    }

    fun resetAfterRendererCrash() {
        publish(BrowserState())
    }

    private fun publish(state: BrowserState) {
        currentState = state
        listener?.invoke(state)
    }

    private fun isHostLike(value: String): Boolean {
        if (value.contains(' ')) return false
        val host = runCatching { Uri.parse("https://$value").host }.getOrNull()
        return host?.contains('.') == true
    }

    private fun isHttps(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
EOF

cat > app/src/main/java/com/subbrowser/browser/web/BrowserWebView.kt <<'EOF'
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
EOF

# Keep the existing UI but make the current browser state useful without
# requiring another UI rewrite at this stage.
python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/subbrowser/ui/browser/BrowserWorkspace.kt")
s = p.read_text()

# Show a real loading/error indicator without depending on extra libraries.
s = s.replace(
    'text = "${state.progress}%",',
    'text = if (state.loading) "${state.progress}%" else if (state.secureConnection) "Secure" else "",'
)

p.write_text(s)
PY

bash -n "$0"

echo "SUB_BROWSER_CORE_READY"
