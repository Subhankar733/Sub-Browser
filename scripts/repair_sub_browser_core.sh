#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

printf '%s\n' '== Sub Browser: repair browser core from known-good foundation =='

mkdir -p \
  app/src/main/java/com/subbrowser/browser/model \
  app/src/main/java/com/subbrowser/browser/session \
  app/src/main/java/com/subbrowser/browser/web \
  app/src/main/java/com/subbrowser/ui/browser

# Remove superseded generators and duplicate browser implementations.
rm -f \
  scripts/build_browser_shell.sh \
  scripts/setup_sub_browser.sh \
  scripts/setup_sub_browser_v2.sh \
  scripts/setup_sub_browser_v3.sh \
  scripts/setup_sub_browser_core.sh \
  app/src/main/java/com/subbrowser/browser/BrowserWebView.kt

cat > app/src/main/java/com/subbrowser/browser/model/BrowserState.kt <<'EOF'
package com.subbrowser.browser.model

import com.subbrowser.browser.session.SessionState

data class BrowserState(
    val session: SessionState = SessionState(),
    val url: String = "about:blank",
    val title: String = "New Tab",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val rendererCrashed: Boolean = false,
    val secureConnection: Boolean = false,
) {
    val activeTab get() = session.tabs.firstOrNull { it.id == session.activeTabId }
}
EOF

cat > app/src/main/java/com/subbrowser/browser/session/TabState.kt <<'EOF'
package com.subbrowser.browser.session

data class TabState(
    val id: Long,
    val url: String = "about:blank",
    val title: String = "New Tab",
    val isPrivate: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)
EOF

cat > app/src/main/java/com/subbrowser/browser/session/SessionState.kt <<'EOF'
package com.subbrowser.browser.session

data class SessionState(
    val tabs: List<TabState> = listOf(TabState(id = 1L)),
    val activeTabId: Long = 1L,
)
EOF

cat > app/src/main/java/com/subbrowser/browser/session/SessionController.kt <<'EOF'
package com.subbrowser.browser.session

import android.os.Bundle

class SessionController {
    companion object {
        private const val KEY_TAB_IDS = "tab_ids"
        private const val KEY_TAB_URLS = "tab_urls"
        private const val KEY_TAB_TITLES = "tab_titles"
        private const val KEY_TAB_PRIVATE = "tab_private"
        private const val KEY_ACTIVE_ID = "active_id"
        private const val KEY_NEXT_ID = "next_id"
    }

    var state: SessionState = SessionState()
        private set

    private var nextId = 2L
    private var observer: ((SessionState) -> Unit)? = null

    fun observe(observer: (SessionState) -> Unit) {
        this.observer = observer
        observer(state)
    }

    fun clearObserver() {
        observer = null
    }

    fun newTab(isPrivate: Boolean = false): Long {
        val id = nextId++
        state = state.copy(
            tabs = state.tabs + TabState(id = id, isPrivate = isPrivate),
            activeTabId = id,
        )
        publish()
        return id
    }

    fun selectTab(id: Long) {
        if (state.tabs.any { it.id == id } && id != state.activeTabId) {
            state = state.copy(activeTabId = id)
            publish()
        }
    }

    fun closeTab(id: Long): Long? {
        if (state.tabs.size == 1 || state.tabs.none { it.id == id }) return null
        val wasActive = state.activeTabId == id
        val removedIndex = state.tabs.indexOfFirst { it.id == id }
        val remaining = state.tabs.filterNot { it.id == id }
        val nextActive = if (wasActive) {
            remaining.getOrNull((removedIndex - 1).coerceAtLeast(0))?.id
                ?: remaining.first().id
        } else {
            state.activeTabId
        }
        state = state.copy(tabs = remaining, activeTabId = nextActive)
        publish()
        return nextActive
    }

    fun updateTab(
        id: Long,
        url: String? = null,
        title: String? = null,
        loading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null,
    ) {
        val current = state.tabs.firstOrNull { it.id == id } ?: return
        val updated = current.copy(
            url = url ?: current.url,
            title = title ?: current.title,
            loading = loading ?: current.loading,
            progress = progress ?: current.progress,
            canGoBack = canGoBack ?: current.canGoBack,
            canGoForward = canGoForward ?: current.canGoForward,
        )
        if (updated == current) return
        state = state.copy(tabs = state.tabs.map { if (it.id == id) updated else it })
        publish()
    }

    fun saveMetadata(outState: Bundle) {
        outState.putLongArray(KEY_TAB_IDS, state.tabs.map { it.id }.toLongArray())
        outState.putStringArrayList(KEY_TAB_URLS, ArrayList(state.tabs.map { it.url }))
        outState.putStringArrayList(KEY_TAB_TITLES, ArrayList(state.tabs.map { it.title }))
        outState.putBooleanArray(KEY_TAB_PRIVATE, state.tabs.map { it.isPrivate }.toBooleanArray())
        outState.putLong(KEY_ACTIVE_ID, state.activeTabId)
        outState.putLong(KEY_NEXT_ID, nextId)
    }

    fun restoreMetadata(bundle: Bundle?) {
        val ids = bundle?.getLongArray(KEY_TAB_IDS) ?: return
        if (ids.isEmpty()) return
        val urls = bundle.getStringArrayList(KEY_TAB_URLS).orEmpty()
        val titles = bundle.getStringArrayList(KEY_TAB_TITLES).orEmpty()
        val privateFlags = bundle.getBooleanArray(KEY_TAB_PRIVATE)
        val restoredTabs = ids.mapIndexed { index, id ->
            TabState(
                id = id,
                url = urls.getOrNull(index) ?: "about:blank",
                title = titles.getOrNull(index) ?: "New Tab",
                isPrivate = privateFlags?.getOrNull(index) ?: false,
            )
        }
        val requestedActive = bundle.getLong(KEY_ACTIVE_ID, restoredTabs.first().id)
        state = SessionState(
            tabs = restoredTabs,
            activeTabId = restoredTabs.firstOrNull { it.id == requestedActive }?.id ?: restoredTabs.first().id,
        )
        nextId = maxOf(bundle.getLong(KEY_NEXT_ID, 1L), (ids.maxOrNull() ?: 0L) + 1L)
        publish()
    }

    private fun publish() {
        observer?.invoke(state)
    }
}
EOF


cat > app/src/main/java/com/subbrowser/browser/BrowserController.kt <<'EOF'
package com.subbrowser.browser

import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.NavigationParameters
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
        val next = currentState.copy(
            url = resolvedUrl,
            title = resolvedTitle,
            loading = loading ?: currentState.loading,
            progress = progress ?: currentState.progress,
            canGoBack = back,
            canGoForward = forward,
            secureConnection = Uri.parse(resolvedUrl).scheme.equals("https", ignoreCase = true),
        )
        currentState = next
        session.updateTab(
            id = activeId,
            url = resolvedUrl,
            title = resolvedTitle,
            loading = next.loading,
            progress = next.progress,
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
            loading = false,
            progress = 100,
        )
    }

    private fun navigateWithCompat(view: WebView, url: String) {
        @OptIn(WebViewCompat.ExperimentalNavigate::class)
        run {
            val params = NavigationParameters.Builder().build()
            WebViewCompat.navigate(view, url, params)
        }
    }

    private fun normalizeInput(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (value.equals("about:blank", ignoreCase = true)) return "about:blank"
        if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
            return value
        }
        val host = runCatching { Uri.parse("https://$value").host }.getOrNull()
        return if (!value.contains(' ') && host?.contains('.') == true) {
            "https://$value"
        } else {
            "https://www.google.com/search?q=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }
    }

    private fun publish() {
        observer?.invoke(currentState)
    }
}
EOF

cat > app/src/main/java/com/subbrowser/browser/web/BrowserWebView.kt <<'EOF'
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
EOF

cat > app/src/main/java/com/subbrowser/ui/browser/BrowserWorkspace.kt <<'EOF'
package com.subbrowser.ui.browser

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.subbrowser.browser.BrowserController
import com.subbrowser.browser.model.BrowserState
import com.subbrowser.browser.web.configureBrowserWebView
import com.subbrowser.ui.theme.SubBlack
import com.subbrowser.ui.theme.SubSaffron
import com.subbrowser.ui.theme.SubSurface
import com.subbrowser.ui.theme.SubSurfaceElevated
import com.subbrowser.ui.theme.SubTextPrimary
import com.subbrowser.ui.theme.SubTextSecondary

@Composable
fun BrowserWorkspace(
    controller: BrowserController = remember { BrowserController() },
) {
    var state by remember { mutableStateOf(BrowserState()) }
    var webViewEpoch by remember { mutableIntStateOf(0) }
    var showTabs by remember { mutableStateOf(false) }
    val commandState = rememberTextFieldState()

    DisposableEffect(controller) {
        controller.observe { state = it }
        onDispose { controller.clearObserver() }
    }

    BackHandler(enabled = state.canGoBack) { controller.goBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack),
    ) {
        if (state.rendererCrashed) {
            CrashSurface(
                onRecover = {
                    controller.resetAfterRendererCrash()
                    webViewEpoch++
                },
            )
        } else {
            androidx.compose.runtime.key(webViewEpoch) {
                AndroidView(
                    factory = { context ->
                        WebView(context).also { configureBrowserWebView(it, controller) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { controller.syncForUi() },
                    onRelease = { view -> controller.dispose(view) },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                CommandDock(
                    state = state,
                    textState = commandState,
                    onSubmit = {
                        controller.navigate(commandState.text.toString())
                        commandState.clearText()
                    },
                    onTabs = { showTabs = true },
                )
                BottomDock(
                    state = state,
                    onBack = controller::goBack,
                    onForward = controller::goForward,
                    onReload = { if (state.loading) controller.stop() else controller.reload() },
                    onNewTab = { controller.newTab(); webViewEpoch++ },
                )
            }
        }

        if (showTabs) {
            TabWorkspace(
                state = state,
                onDismiss = { showTabs = false },
                onSelect = { controller.selectTab(it); showTabs = false; webViewEpoch++ },
                onClose = { controller.closeTab(it); webViewEpoch++ },
                onNew = { controller.newTab(); showTabs = false; webViewEpoch++ },
            )
        }
    }
}

@Composable
private fun CommandDock(
    state: BrowserState,
    textState: androidx.compose.foundation.text.input.TextFieldState,
    onSubmit: () -> Unit,
    onTabs: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(SubSurface.copy(alpha = 0.96f))
                .border(1.dp, SubSurfaceElevated, RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            BasicTextField(
                state = textState,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = SubTextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(SubSaffron),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                onKeyboardAction = { onSubmit() },
                decorator = { inner ->
                    if (textState.text.isEmpty()) {
                        Text(
                            text = if (state.secureConnection) "Secure • Search or enter address" else "Search or enter address",
                            color = SubTextSecondary,
                            maxLines = 1,
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        DockButton(label = "${state.session.tabs.size}", onClick = onTabs)
    }
}

@Composable
private fun BottomDock(
    state: BrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onNewTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton("‹", onBack, state.canGoBack)
        DockButton("›", onForward, state.canGoForward)
        DockButton(if (state.loading) "×" else "↻", onReload)
        DockButton("+", onNewTab)
    }
}

@Composable
private fun DockButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .clip(CircleShape)
            .background(SubSurface.copy(alpha = 0.96f))
            .size(48.dp),
    ) {
        Text(label, color = if (enabled) SubTextPrimary else SubTextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun TabWorkspace(
    state: BrowserState,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Workspace", color = SubTextPrimary, fontSize = 24.sp)
                TextButton(onClick = onDismiss) { Text("Done", color = SubSaffron) }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.session.tabs.forEach { tab ->
                    TabCard(
                        tab = tab,
                        active = tab.id == state.session.activeTabId,
                        onClick = { onSelect(tab.id) },
                        onClose = { onClose(tab.id) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onNew) { Text("+ New workspace", color = SubSaffron) }
        }
    }
}

@Composable
private fun TabCard(
    tab: com.subbrowser.browser.session.TabState,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) SubSurfaceElevated else SubSurface)
            .border(1.dp, if (active) SubSaffron else SubSurfaceElevated, RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (tab.isPrivate) "Private" else "Tab ${tab.id}",
                color = if (active) SubSaffron else SubTextSecondary,
                fontSize = 12.sp,
            )
            TextButton(onClick = onClose) { Text("×", color = SubTextSecondary) }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = tab.title.ifBlank { "New Tab" },
            color = SubTextPrimary,
            maxLines = 2,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = tab.url,
            color = SubTextSecondary,
            maxLines = 2,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(if (active) "Active" else "Open", color = SubSaffron) }
    }
}

@Composable
private fun CrashSurface(onRecover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("The page engine stopped", color = SubTextPrimary, fontSize = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "The renderer was restarted safely. Your browser session remains available.",
            color = SubTextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onRecover) { Text("Restart page", color = SubSaffron) }
    }
}
EOF



# Keep the manifest compatible with edge-to-edge + IME resizing.
cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:hardwareAccelerated="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.SubBrowser"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
EOF

# Update Activity so the browser controller owns lifecycle state restoration.
cat > app/src/main/java/com/subbrowser/MainActivity.kt <<'EOF'
package com.subbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.subbrowser.browser.BrowserController
import com.subbrowser.ui.browser.BrowserWorkspace
import com.subbrowser.ui.theme.SubBrowserTheme

class MainActivity : ComponentActivity() {
    private val browserController = BrowserController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        browserController.restoreInstanceState(savedInstanceState)
        setContent {
            SubBrowserTheme {
                BrowserWorkspace(browserController)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browserController.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}
EOF




cat > app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.subbrowser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.subbrowser"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.webkit:webkit:1.17.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
EOF

# Remove the old tracked foundation files that no longer have a source-of-truth path.
rm -f app/src/main/java/com/subbrowser/browser/BrowserState.kt

# Static checks available without a local JDK/Android SDK.
bash -n "$0"
git diff --check

echo 'SUB_BROWSER_CORE_REPAIRED'
