package com.subbrowser.browser

import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.webkit.NavigationParameters
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.subbrowser.browser.model.BrowserState
import com.subbrowser.browser.session.SessionController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BrowserController {
    companion object {
        private const val STATE_KEY = "sub_browser_webview_state"
        private const val MAX_WEBVIEW_STATE_BYTES = 96 * 1024
    }

    private val session = SessionController()
    private val savedTabStates = mutableMapOf<Long, Bundle>()
    private var webView: WebView? = null
    private var observer: ((BrowserState) -> Unit)? = null
    private var currentState = BrowserState(session = session.state)

    init {
        session.observe { sessionState ->
            currentState = currentState.copy(session = sessionState)
            val active = sessionState.tabs.firstOrNull { it.id == sessionState.activeTabId }
            if (active != null && webView == null) {
                currentState = currentState.copy(
                    url = active.url,
                    title = active.title,
                    loading = active.loading,
                    progress = active.progress,
                    canGoBack = active.canGoBack,
                    canGoForward = active.canGoForward,
                )
            }
            publish()
        }
    }

    fun observe(observer: (BrowserState) -> Unit) {
        this.observer = observer
        observer(currentState)
    }

    fun clearObserver() {
        observer = null
    }

    fun attach(view: WebView) {
        webView = view
        val active = session.state.tabs.firstOrNull { it.id == session.state.activeTabId }
        val saved = savedTabStates[session.state.activeTabId]
        if (saved != null) {
            view.restoreState(saved)
            savedTabStates.remove(session.state.activeTabId)
        } else if (active != null && active.url != "about:blank") {
            navigate(active.url)
        }
        sync()
    }

    fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    fun dispose(view: WebView) {
        detach(view)
        runCatching { view.stopLoading() }
        runCatching { view.destroy() }
    }

    fun saveActiveTabState() {
        val view = webView ?: return
        val id = session.state.activeTabId
        val bundle = Bundle()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE)) {
            WebViewCompat.saveState(view, bundle, MAX_WEBVIEW_STATE_BYTES, false)
        } else {
            @Suppress("DEPRECATION")
            view.saveState(bundle)
        }
        savedTabStates[id] = bundle
        session.updateTab(
            id = id,
            url = view.url ?: "about:blank",
            title = view.title.orEmpty().ifBlank { "New Tab" },
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
        )
    }

    fun restoreInstanceState(bundle: Bundle?) {
        session.restoreMetadata(bundle)
        bundle?.getBundle(STATE_KEY)?.let { savedTabStates[session.state.activeTabId] = it }
    }

    fun saveInstanceState(outState: Bundle) {
        session.saveMetadata(outState)
        val view = webView ?: return
        val webState = Bundle()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE)) {
            WebViewCompat.saveState(view, webState, MAX_WEBVIEW_STATE_BYTES, false)
        } else {
            @Suppress("DEPRECATION")
            view.saveState(webState)
        }
        outState.putBundle(STATE_KEY, webState)
    }

    fun newTab(isPrivate: Boolean = false) {
        saveActiveTabState()
        session.newTab(isPrivate)
        publish()
    }

    fun selectTab(id: Long) {
        if (id == session.state.activeTabId) return
        saveActiveTabState()
        session.selectTab(id)
        publish()
    }

    fun closeTab(id: Long) {
        val wasActive = id == session.state.activeTabId
        if (wasActive) saveActiveTabState()
        session.closeTab(id) ?: return
        if (wasActive) webView = null
        publish()
    }

    fun navigate(input: String) {
        val target = normalizeInput(input) ?: return
        val view = webView ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_NAVIGATE_EXPERIMENTAL_V1)) {
            navigateWithCompat(view, target)
        } else {
            view.loadUrl(target)
        }
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

    fun syncForUi() {
        val view = webView ?: return
        val url = view.url ?: "about:blank"
        val title = view.title.orEmpty().ifBlank { "New Tab" }
        val back = view.canGoBack()
        val forward = view.canGoForward()
        if (url == currentState.url &&
            title == currentState.title &&
            back == currentState.canGoBack &&
            forward == currentState.canGoForward
        ) return
        update(url = url, title = title, canGoBack = back, canGoForward = forward)
    }

    fun onNavigationStarted(url: String) {
        update(url = url, loading = true, progress = 0)
    }

    fun onNavigationFinished(url: String, title: String) {
        update(url = url, title = title, loading = false, progress = 100)
    }

    fun onProgressChanged(progress: Int) {
        update(loading = progress < 100, progress = progress.coerceIn(0, 100))
    }

    fun onTitleChanged(title: String) {
        update(title = title)
    }

    fun onRendererCrashed() {
        webView = null
        currentState = currentState.copy(loading = false, progress = 0, rendererCrashed = true)
        publish()
    }

    fun resetAfterRendererCrash() {
        currentState = currentState.copy(rendererCrashed = false)
        publish()
    }

    private fun update(
        url: String? = null,
        title: String? = null,
        loading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null,
    ) {
        val view = webView
        val activeId = session.state.activeTabId
        val resolvedUrl = url ?: view?.url ?: currentState.url
        val resolvedTitle = title ?: view?.title.orEmpty().ifBlank { currentState.title }
        val back = canGoBack ?: view?.canGoBack() ?: currentState.canGoBack
        val forward = canGoForward ?: view?.canGoForward() ?: currentState.canGoForward
        currentState = currentState.copy(
            url = resolvedUrl,
            title = resolvedTitle,
            loading = loading ?: currentState.loading,
            progress = progress ?: currentState.progress,
            canGoBack = back,
            canGoForward = forward,
            secureConnection = Uri.parse(resolvedUrl).scheme.equals("https", ignoreCase = true),
        )
        session.updateTab(
            id = activeId,
            url = resolvedUrl,
            title = resolvedTitle,
            loading = currentState.loading,
            progress = currentState.progress,
            canGoBack = back,
            canGoForward = forward,
        )
        publish()
    }

    private fun sync() {
        val view = webView ?: return
        update(
            url = view.url ?: "about:blank",
            title = view.title.orEmpty().ifBlank { "New Tab" },
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
        )
    }

    @androidx.webkit.WebViewCompat.ExperimentalNavigate
    private fun navigateWithCompat(view: WebView, url: String) {
        WebViewCompat.navigate(view, url, NavigationParameters.Builder().build())
    }

    private fun normalizeInput(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value)
        val looksLikeHost = value.contains('.') && !value.contains(' ')
        return when {
            hasScheme -> value
            looksLikeHost -> "https://$value"
            else -> {
                val encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
                "https://www.google.com/search?q=$encoded"
            }
        }
    }

    private fun publish() {
        observer?.invoke(currentState)
    }
}
