#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

printf '%s\n' '== Sub Browser: original browsing workspace UI =='

mkdir -p \
  app/src/main/java/com/subbrowser/browser/model \
  app/src/main/java/com/subbrowser/browser/session \
  app/src/main/java/com/subbrowser/browser/web \
  app/src/main/java/com/subbrowser/ui/browser

# Keep the browser core API coherent with the UI. This is a clean-room implementation:
# no source/config/assets are copied from another browser.
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
            activeTabId = restoredTabs.firstOrNull { it.id == requestedActive }?.id
                ?: restoredTabs.first().id,
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
EOF

cat > app/src/main/java/com/subbrowser/browser/web/BrowserWebView.kt <<'EOF'
package com.subbrowser.browser.web

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
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
        allowContentAccess = false
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
        WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
    }

    webView.webViewClient = object : WebViewClient() {
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

        override fun onReceivedTitle(view: WebView, title: String) {
            controller.onTitleChanged(title)
        }
    }

    webView.setDownloadListener { _, _, _, _, _ ->
        // Download routing is deliberately kept out of the workspace UI layer.
        // A dedicated download manager will be added as a separate feature.
    }
}
EOF

cat > app/src/main/java/com/subbrowser/ui/browser/BrowserWorkspace.kt <<'EOF'
package com.subbrowser.ui.browser

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
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
    var workspaceOpen by remember { mutableStateOf(false) }
    var portalOpen by remember { mutableStateOf(false) }
    val commandState = rememberTextFieldState()
    val context = LocalContext.current

    DisposableEffect(controller) {
        controller.observe { state = it }
        onDispose { controller.clearObserver() }
    }

    BackHandler(enabled = workspaceOpen || portalOpen) {
        workspaceOpen = false
        portalOpen = false
    }

    BackHandler(enabled = !workspaceOpen && !portalOpen && state.canGoBack) {
        controller.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack),
    ) {
        if (state.rendererCrashed) {
            CrashWorkspace(
                onRecover = {
                    controller.resetAfterRendererCrash()
                    webViewEpoch++
                },
            )
        } else {
            androidx.compose.runtime.key(webViewEpoch) {
                AndroidView(
                    factory = {
                        WebView(context).also { configureBrowserWebView(it, controller) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { controller.syncForUi() },
                    onRelease = { controller.dispose(it) },
                )
            }

            if (state.url == "about:blank" && !workspaceOpen) {
                HomeCanvas(
                    onFocusPortal = { portalOpen = true },
                    onNewPrivate = {
                        controller.newTab(isPrivate = true)
                        webViewEpoch++
                    },
                    onWorkspace = { workspaceOpen = true },
                )
            }

            FloatingPortal(
                state = state,
                textState = commandState,
                expanded = portalOpen,
                onExpand = { portalOpen = true },
                onCollapse = { portalOpen = false },
                onSubmit = {
                    controller.navigate(commandState.text.toString())
                    commandState.clearText()
                    portalOpen = false
                },
            )

            if (!workspaceOpen) {
                EdgeActions(
                    state = state,
                    onBack = controller::goBack,
                    onForward = controller::goForward,
                    onReload = { if (state.loading) controller.stop() else controller.reload() },
                    onWorkspace = { workspaceOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding(),
                )
            }
        }

        if (workspaceOpen) {
            WorkspaceOverlay(
                state = state,
                onDismiss = { workspaceOpen = false },
                onSelect = {
                    controller.selectTab(it)
                    workspaceOpen = false
                    webViewEpoch++
                },
                onClose = {
                    controller.closeTab(it)
                    webViewEpoch++
                },
                onNew = {
                    controller.newTab()
                    workspaceOpen = false
                    webViewEpoch++
                },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    workspaceOpen = false
                    webViewEpoch++
                },
            )
        }
    }
}

@Composable
private fun HomeCanvas(
    onFocusPortal: () -> Unit,
    onNewPrivate: () -> Unit,
    onWorkspace: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "SUB",
            color = SubSaffron,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your browsing space.",
            color = SubTextPrimary,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Search, move, and return to what matters.",
            color = SubTextSecondary,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(28.dp))
        PortalAction(
            label = "Open a page",
            detail = "Search or enter an address",
            onClick = onFocusPortal,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniAction("Private", onNewPrivate, Modifier.weight(1f))
            MiniAction("Spaces", onWorkspace, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FloatingPortal(
    state: BrowserState,
    textState: TextFieldState,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSubmit: () -> Unit,
) {
    val label = when {
        expanded -> "Search or enter address"
        state.loading -> "Loading ${state.progress}%"
        state.title != "New Tab" -> state.title
        else -> "Open"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(if (expanded) 30.dp else 22.dp))
                .background(SubSurface.copy(alpha = 0.96f))
                .border(
                    1.dp,
                    if (expanded) SubSaffron.copy(alpha = 0.55f) else SubSurfaceElevated,
                    RoundedCornerShape(if (expanded) 30.dp else 22.dp),
                )
                .shadow(if (expanded) 18.dp else 8.dp, RoundedCornerShape(30.dp))
                .padding(horizontal = 15.dp, vertical = if (expanded) 12.dp else 10.dp)
                .clickable(onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.secureConnection) "●" else "○",
                color = if (state.secureConnection) SubSaffron else SubTextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(9.dp))

            if (expanded) {
                BasicTextField(
                    state = textState,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = SubTextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(SubSaffron),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    onKeyboardAction = { onSubmit() },
                    decorator = { inner ->
                        if (textState.text.isEmpty()) {
                            Text(label, color = SubTextSecondary, maxLines = 1)
                        }
                        inner()
                    },
                )
                TextButton(onClick = onCollapse) {
                    Text("×", color = SubTextSecondary, fontSize = 18.sp)
                }
            } else {
                Text(
                    text = label,
                    color = if (state.title != "New Tab") SubTextPrimary else SubTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.session.tabs.size}",
                    color = SubTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EdgeActions(
    state: BrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionGlyph("←", onBack, state.canGoBack)
        Spacer(Modifier.width(12.dp))
        ActionGlyph(if (state.loading) "×" else "↻", onReload, true)
        Spacer(Modifier.width(12.dp))
        ActionGlyph("→", onForward, state.canGoForward)
        Spacer(Modifier.width(18.dp))
        ActionGlyph("◈", onWorkspace, true, emphasized = true)
    }
}

@Composable
private fun ActionGlyph(
    glyph: String,
    onClick: () -> Unit,
    enabled: Boolean,
    emphasized: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(if (emphasized) 50.dp else 44.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) SubSaffron.copy(alpha = 0.16f) else SubSurface.copy(alpha = 0.92f)
            )
            .border(
                1.dp,
                if (emphasized) SubSaffron.copy(alpha = 0.55f) else SubSurfaceElevated,
                CircleShape,
            )
            .alpha(if (enabled) 1f else 0.32f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (emphasized) SubSaffron else SubTextPrimary,
            fontSize = 19.sp,
        )
    }
}

@Composable
private fun PortalAction(
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Text(label, color = SubTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = SubTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun MiniAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = SubTextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun WorkspaceOverlay(
    state: BrowserState,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onPrivate: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack.copy(alpha = 0.97f))
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("SPACES", color = SubSaffron, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${state.session.tabs.size} active page${if (state.session.tabs.size == 1) "" else "s"}",
                        color = SubTextPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Return", color = SubTextPrimary)
                }
            }

            Spacer(Modifier.height(18.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.session.tabs, key = { it.id }) { tab ->
                    SpaceCard(
                        tab = tab,
                        active = tab.id == state.session.activeTabId,
                        onOpen = { onSelect(tab.id) },
                        onClose = { onClose(tab.id) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniAction("New page", onNew, Modifier.weight(1f))
                MiniAction("Private page", onPrivate, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Pages stay separate from the browsing surface.",
                color = SubTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SpaceCard(
    tab: com.subbrowser.browser.session.TabState,
    active: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(285.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (active) SubSurfaceElevated else SubSurface)
            .border(
                1.dp,
                if (active) SubSaffron.copy(alpha = 0.72f) else SubSurfaceElevated,
                RoundedCornerShape(28.dp),
            )
            .clickable(onClick = onOpen)
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (tab.isPrivate) "PRIVATE" else "PAGE ${tab.id}",
                color = if (active) SubSaffron else SubTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            TextButton(onClick = onClose) {
                Text("×", color = SubTextSecondary, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = tab.title.ifBlank { "Untitled page" },
            color = SubTextPrimary,
            fontSize = 21.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = tab.url,
            color = SubTextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 3,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = if (active) "CURRENT SPACE" else "OPEN SPACE",
            color = if (active) SubSaffron else SubTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun CrashWorkspace(onRecover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("The page engine stopped", color = SubTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "The renderer ended unexpectedly. The browsing space is still intact.",
            color = SubTextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onRecover) {
            Text("Restart page", color = SubSaffron)
        }
    }
}
EOF

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
                BrowserWorkspace(controller = browserController)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browserController.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}
EOF

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

printf '%s\n' 'SUB_BROWSER_WORKSPACE_UI_READY'
