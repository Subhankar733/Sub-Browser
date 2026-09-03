package com.subbrowser.browser

import android.webkit.WebView
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

class BrowserController {
    private var webView: WebView? = null

    val state: MutableState<BrowserState> = mutableStateOf(BrowserState())

    fun attach(view: WebView) {
        webView = view
        sync()
    }

    fun detach(view: WebView) {
        if (webView === view) {
            webView = null
        }
    }

    fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return

        val target = when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.contains("://") -> value
            value.contains(" ") -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(value, "UTF-8")}"
            value.contains(".") -> "https://$value"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(value, "UTF-8")}"
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

    fun sync() {
        webView?.let { view ->
            state.value = state.value.copy(
                url = view.url ?: "about:blank",
                title = view.title.orEmpty(),
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward()
            )
        }
    }

    fun updateLoading(progress: Int) {
        state.value = state.value.copy(
            isLoading = progress < 100,
            progress = progress
        )
    }
}
