package com.subbrowser.ui.browser

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.subbrowser.browser.BrowserController
import com.subbrowser.browser.data.BrowserDatabase
import com.subbrowser.browser.model.BrowserState
import com.subbrowser.browser.web.configureBrowserWebView
import com.subbrowser.ui.theme.SubBlack
import com.subbrowser.ui.theme.SubSaffron
import com.subbrowser.ui.theme.SubTextPrimary
import com.subbrowser.ui.theme.SubTextSecondary

private val ShellSurface = Color(0xFF151719)
private val ShellCard = Color(0xFF1D2023)
private val ShellBorder = Color(0xFF30343A)
private val Accent = SubSaffron

@Composable
fun BrowserShellV2(controller: BrowserController) {
    val context = LocalContext.current
    val database = remember { BrowserDatabase(context) }
    val focusManager = LocalFocusManager.current

    var state by remember { mutableStateOf(BrowserState()) }
    var address by remember { mutableStateOf("") }
    var homeQuery by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { controller.attachDatabase(database) }

    DisposableEffect(controller) {
        controller.observe { newState ->
            state = newState
            if (newState.url.isNotBlank() && newState.url != "about:blank") {
                address = newState.url
            }
        }
        onDispose { controller.clearObserver() }
    }

    val isHome = state.url.isBlank() || state.url == "about:blank"

    val submit: (String) -> Unit = { value ->
        val query = value.trim()
        if (query.isNotEmpty()) {
            focusManager.clearFocus()
            controller.navigate(query)
        }
    }

    BackHandler(enabled = menuOpen) { menuOpen = false }
    BackHandler(enabled = !menuOpen && (state.canGoBack || !isHome)) {
        if (state.canGoBack) controller.goBack()
        else {
            controller.navigate("about:blank")
            address = ""
            homeQuery = ""
        }
    }

    Box(Modifier.fillMaxSize().background(SubBlack)) {
        Column(Modifier.fillMaxSize()) {
            BrowserCompactTopBar(
                address = if (isHome) "" else address,
                loading = state.loading,
                progress = state.progress,
                onAddressChange = { address = it },
                onSubmit = { submit(address) },
                onReload = { if (state.loading) controller.stop() else controller.reload() }
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).also { view ->
                            controller.attach(view)
                            configureBrowserWebView(view, controller)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { controller.dispose(it) }
                )

                if (isHome) {
                    BrowserNewTabPage(
                        query = homeQuery,
                        incognito = state.isIncognito,
                        onQueryChange = { homeQuery = it },
                        onSearch = { submit(homeQuery) },
                        onShortcut = { submit(it) }
                    )
                }

                if (state.rendererCrashed) {
                    RendererCrashCard {
                        controller.resetAfterRendererCrash()
                        controller.navigate("about:blank")
                    }
                }
            }
        }

        BrowserFloatingDock(
            state = state,
            onBack = {
                if (state.canGoBack) controller.goBack()
                else controller.navigate("about:blank")
            },
            onForward = controller::goForward,
            onHome = {
                controller.navigate("about:blank")
                address = ""
                homeQuery = ""
            },
            onTabs = { controller.toggleTabSwitcher() },
            onMenu = { menuOpen = true }
        )

        if (menuOpen) {
            BrowserQuickMenu(
                incognito = state.isIncognito,
                onDismiss = { menuOpen = false },
                onNewTab = {
                    controller.openNewTab()
                    address = ""
                    homeQuery = ""
                    menuOpen = false
                },
                onPrivateTab = {
                    controller.toggleIncognito()
                    menuOpen = false
                },
                onBookmark = {
                    controller.bookmarkCurrentPage()
                    menuOpen = false
                },
                onHistory = {
                    controller.toggleHistorySheet()
                    menuOpen = false
                },
                onBookmarks = {
                    controller.toggleBookmarksSheet()
                    menuOpen = false
                },
                onSettings = {
                    controller.toggleSettingsSheet()
                    menuOpen = false
                }
            )
        }

        if (state.showTabSwitcher) {
            TabSwitcherOverlay(
                state = state,
                onSelectTab = controller::switchTab,
                onCloseTab = controller::closeTab,
                onNewTab = controller::openNewTab,
                onClose = controller::toggleTabSwitcher
            )
        }

        if (state.showHistorySheet) {
            HistorySheet(
                database = database,
                onSelect = {
                    controller.toggleHistorySheet()
                    controller.navigate(it)
                },
                onClose = controller::toggleHistorySheet
            )
        }

        if (state.showBookmarksSheet) {
            BookmarksSheet(
                database = database,
                onSelect = {
                    controller.toggleBookmarksSheet()
                    controller.navigate(it)
                },
                onClose = controller::toggleBookmarksSheet
            )
        }

        if (state.showSettingsSheet) {
            SettingsSheet(
                currentEngine = state.searchEngine,
                onSelectEngine = controller::setSearchEngine,
                onClose = controller::toggleSettingsSheet
            )
        }
    }
}

@Composable
private fun BrowserCompactTopBar(
    address: String,
    loading: Boolean,
    progress: Int,
    onAddressChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ShellSurface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ShellCard)
                .border(1.dp, ShellBorder, RoundedCornerShape(22.dp))
                .padding(start = 14.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (address.startsWith("https://")) Icons.Rounded.Lock
                else Icons.Rounded.Search,
                contentDescription = null,
                tint = SubTextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))

            BasicTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = SubTextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                decorationBox = { inner ->
                    if (address.isEmpty()) {
                        Text("Search or enter address", color = SubTextSecondary, fontSize = 12.sp)
                    }
                    inner()
                }
            )

            Icon(
                if (loading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                contentDescription = null,
                tint = SubTextSecondary,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onReload() }
                    .padding(7.dp)
            )
        }

        if (loading) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
                color = Accent,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun BrowserNewTabPage(
    query: String,
    incognito: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onShortcut: (String) -> Unit
) {
    val shortcuts = listOf(
        "Google" to "https://www.google.com",
        "YouTube" to "https://www.youtube.com",
        "GitHub" to "https://github.com",
        "Wikipedia" to "https://wikipedia.org",
        "Reddit" to "https://reddit.com",
        "X" to "https://x.com"
    )

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(54.dp))
        Box(
            Modifier.size(70.dp).clip(CircleShape).background(ShellCard)
                .border(2.dp, Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("Sub Browser", color = SubTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            if (incognito) "Private browsing" else "Fast • private • focused",
            color = if (incognito) Accent else SubTextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))

        Row(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
                .background(ShellCard).border(1.dp, ShellBorder, RoundedCornerShape(25.dp))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = SubTextSecondary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = SubTextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search the web", color = SubTextSecondary, fontSize = 13.sp)
                    inner()
                }
            )
        }

        Spacer(Modifier.height(30.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            shortcuts.take(3).forEach { (name, url) -> ShortcutChip(name, url, onShortcut) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            shortcuts.drop(3).forEach { (name, url) -> ShortcutChip(name, url, onShortcut) }
        }
    }
}

@Composable
private fun RowScope.ShortcutChip(name: String, url: String, onClick: (String) -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onClick(url) }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(ShellCard)
                .border(1.dp, ShellBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(2).uppercase(), color = SubTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(name, color = SubTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BoxScope.BrowserFloatingDock(
    state: BrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 10.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .clip(RoundedCornerShape(28.dp))
            .background(ShellSurface.copy(alpha = .96f))
            .border(1.dp, ShellBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DockIcon(Icons.Rounded.ArrowBack, state.canGoBack, onBack)
        DockIcon(Icons.Rounded.ArrowForward, state.canGoForward, onForward)
        DockIcon(Icons.Rounded.Home, true, onHome)

        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .border(1.dp, SubTextSecondary, RoundedCornerShape(11.dp))
                .clickable { onTabs() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Tab, null, tint = SubTextPrimary, modifier = Modifier.size(19.dp))
            Text(
                state.session.tabs.size.toString(),
                color = SubTextPrimary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp)
            )
        }

        DockIcon(Icons.Rounded.MoreVert, true, onMenu)
    }
}

@Composable
private fun DockIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Icon(
        icon,
        null,
        tint = if (enabled) SubTextPrimary else Color(0xFF55585D),
        modifier = Modifier.size(38.dp).clip(CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(9.dp)
    )
}

@Composable
private fun BrowserQuickMenu(
    incognito: Boolean,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onPrivateTab: () -> Unit,
    onBookmark: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize().clickable { onDismiss() }) {
        Column(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 78.dp)
                .width(230.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ShellCard)
                .border(1.dp, ShellBorder, RoundedCornerShape(18.dp))
                .clickable(enabled = false) { }
        ) {
            MenuItem("New tab", onNewTab)
            MenuItem(if (incognito) "Exit private mode" else "New private tab", onPrivateTab)
            MenuItem("Add bookmark", onBookmark)
            MenuItem("History", onHistory)
            MenuItem("Bookmarks", onBookmarks)
            MenuItem("Settings", onSettings)
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 17.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SubTextPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun RendererCrashCard(onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(SubBlack.copy(alpha = .96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(30.dp)
        ) {
            Icon(Icons.Rounded.Shield, null, tint = Accent, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(14.dp))
            Text("Page renderer stopped", color = SubTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("The page can be safely reloaded.", color = SubTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Text(
                "Reload",
                color = SubBlack,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Accent)
                    .clickable { onRetry() }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}
