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
