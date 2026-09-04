package com.subbrowser.ui.browser

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

/*
 * Sub Browser — stable UI shell.
 *
 * The shell is intentionally simple:
 * - one browser bar at the top
 * - one start page
 * - one compact bottom navigation row
 * - separate full-screen panels for browser sections
 *
 * All feature entries are UI placeholders until their controllers are wired.
 */

private enum class Panel {
    NONE,
    SEARCH,
    TABS,
    MENU,
    HISTORY,
    BOOKMARKS,
    DOWNLOADS,
    PRIVACY,
    SETTINGS,
    APPEARANCE,
    PAGE_TOOLS,
}

@Composable
fun BrowserWorkspace(
    controller: BrowserController = remember { BrowserController() },
) {
    var state by remember { mutableStateOf(BrowserState()) }
    var webViewVersion by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    val address = rememberTextFieldState()
    val context = LocalContext.current

    DisposableEffect(controller) {
        controller.observe { state = it }
        onDispose { controller.clearObserver() }
    }

    BackHandler(enabled = panel != Panel.NONE) {
        panel = when (panel) {
            Panel.HISTORY,
            Panel.BOOKMARKS,
            Panel.DOWNLOADS,
            Panel.PRIVACY,
            Panel.SETTINGS,
            Panel.APPEARANCE -> Panel.MENU

            Panel.NONE,
            Panel.SEARCH,
            Panel.TABS,
            Panel.MENU,
            Panel.PAGE_TOOLS -> Panel.NONE
        }
    }

    BackHandler(enabled = panel == Panel.NONE && state.canGoBack) {
        controller.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack),
    ) {
        if (state.rendererCrashed) {
            CrashPanel(
                onReload = {
                    controller.resetAfterRendererCrash()
                    webViewVersion++
                },
            )
        } else {
            key(webViewVersion) {
                AndroidView(
                    factory = {
                        WebView(context).also { configureBrowserWebView(it, controller) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { controller.syncForUi() },
                    onRelease = { controller.dispose(it) },
                )
            }

            if (state.url == "about:blank" && panel == Panel.NONE) {
                StartPage(
                    onSearch = { panel = Panel.SEARCH },
                    onNewTab = {
                        controller.newTab()
                        webViewVersion++
                    },
                    onPrivate = {
                        controller.newTab(isPrivate = true)
                        webViewVersion++
                    },
                    onTabs = { panel = Panel.TABS },
                    onBookmarks = { panel = Panel.BOOKMARKS },
                )
            }

            if (panel == Panel.NONE || panel == Panel.SEARCH) {
                BrowserBar(
                    state = state,
                    address = address,
                    editing = panel == Panel.SEARCH,
                    onEdit = { panel = Panel.SEARCH },
                    onCancel = { panel = Panel.NONE },
                    onSubmit = {
                        val value = address.text.toString().trim()
                        if (value.isNotEmpty()) controller.navigate(value)
                        address.edit { replace(0, length, "") }
                        panel = Panel.NONE
                    },
                    onTabs = { panel = Panel.TABS },
                    onMenu = { panel = Panel.MENU },
                )
            }

            if (panel == Panel.NONE) {
                BottomNav(
                    state = state,
                    onBack = controller::goBack,
                    onHome = {
                        if (state.url != "about:blank") controller.navigate("about:blank")
                    },
                    onTabs = { panel = Panel.TABS },
                    onMenu = { panel = Panel.MENU },
                )
            }
        }

        when (panel) {
            Panel.NONE,
            Panel.SEARCH -> Unit

            Panel.TABS -> TabsPanel(
                state = state,
                onBack = { panel = Panel.NONE },
                onSelect = {
                    controller.selectTab(it)
                    panel = Panel.NONE
                    webViewVersion++
                },
                onClose = {
                    controller.closeTab(it)
                    webViewVersion++
                },
                onNew = {
                    controller.newTab()
                    panel = Panel.NONE
                    webViewVersion++
                },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    panel = Panel.NONE
                    webViewVersion++
                },
            )

            Panel.MENU -> MenuPanel(
                onBack = { panel = Panel.NONE },
                onTabs = { panel = Panel.TABS },
                onHistory = { panel = Panel.HISTORY },
                onBookmarks = { panel = Panel.BOOKMARKS },
                onDownloads = { panel = Panel.DOWNLOADS },
                onPrivacy = { panel = Panel.PRIVACY },
                onSettings = { panel = Panel.SETTINGS },
                onPageTools = { panel = Panel.PAGE_TOOLS },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    panel = Panel.NONE
                    webViewVersion++
                },
            )

            Panel.HISTORY -> SimplePanel(
                title = "History",
                subtitle = "Recently visited pages",
                onBack = { panel = Panel.MENU },
                rows = listOf(
                    "Today",
                    "Yesterday",
                    "Last 7 days",
                    "Search history",
                    "Clear browsing data",
                ),
            )

            Panel.BOOKMARKS -> SimplePanel(
                title = "Bookmarks",
                subtitle = "Saved websites",
                onBack = { panel = Panel.MENU },
                rows = listOf(
                    "All bookmarks",
                    "Mobile bookmarks",
                    "Add bookmark",
                    "Bookmark folders",
                ),
            )

            Panel.DOWNLOADS -> SimplePanel(
                title = "Downloads",
                subtitle = "Files from the web",
                onBack = { panel = Panel.MENU },
                rows = listOf(
                    "All downloads",
                    "Active downloads",
                    "Download location",
                    "Clear downloads",
                ),
            )

            Panel.PRIVACY -> SimplePanel(
                title = "Privacy",
                subtitle = "Protection and site controls",
                onBack = { panel = Panel.MENU },
                rows = listOf(
                    "Block trackers",
                    "Block ads",
                    "Anti-fingerprinting",
                    "Safe Browsing",
                    "Cookies",
                    "Site permissions",
                    "Clear site data",
                ),
            )

            Panel.SETTINGS -> SettingsPanel(
                onBack = { panel = Panel.MENU },
                onAppearance = { panel = Panel.APPEARANCE },
            )

            Panel.APPEARANCE -> SimplePanel(
                title = "Appearance",
                subtitle = "Browser layout and theme",
                onBack = { panel = Panel.SETTINGS },
                rows = listOf(
                    "System theme",
                    "Light theme",
                    "Dark theme",
                    "Compact layout",
                    "Fullscreen",
                ),
            )

            Panel.PAGE_TOOLS -> SimplePanel(
                title = "Page tools",
                subtitle = "Tools for the current page",
                onBack = { panel = Panel.NONE },
                rows = listOf(
                    "Find in page",
                    "Reader view",
                    "Translate page",
                    "Share page",
                    "Save page",
                    "Download",
                    "Desktop site",
                    "Zoom in",
                    "Zoom out",
                    "Site information",
                ),
            )
        }
    }
}

@Composable
private fun BrowserBar(
    state: BrowserState,
    address: TextFieldState,
    editing: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    val title = when {
        editing -> "Search or enter address"
        state.loading -> "Loading ${state.progress}%"
        state.title.isNotBlank() && state.title != "New Tab" -> state.title
        else -> "Search or enter address"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton("S", onEdit, accent = true)

        Spacer(Modifier.width(6.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(SubSurface)
                .border(
                    1.dp,
                    if (editing) SubSaffron.copy(alpha = 0.55f) else SubSurfaceElevated,
                    RoundedCornerShape(19.dp),
                )
                .clickable(onClick = onEdit)
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.secureConnection) "●" else "○",
                color = if (state.secureConnection) SubSaffron else SubTextSecondary,
                fontSize = 7.sp,
            )
            Spacer(Modifier.width(7.dp))

            if (editing) {
                BasicTextField(
                    state = address,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = SubTextPrimary,
                        fontSize = 12.sp,
                    ),
                    cursorBrush = SolidColor(SubSaffron),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    onKeyboardAction = { onSubmit() },
                    decorator = { inner ->
                        if (address.text.isEmpty()) {
                            Text(
                                title,
                                color = SubTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        inner()
                    },
                )

                Text(
                    "×",
                    color = SubTextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable(onClick = onCancel),
                )
            } else {
                Text(
                    title,
                    color = if (state.title.isNotBlank() && state.title != "New Tab")
                        SubTextPrimary else SubTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.width(6.dp))
        CircleButton("${state.session.tabs.size}", onTabs, accent = true)
        Spacer(Modifier.width(5.dp))
        CircleButton("⋮", onMenu)
    }
}

@Composable
private fun BottomNav(
    state: BrowserState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton("‹", onBack, enabled = state.canGoBack)
        Spacer(Modifier.width(9.dp))
        CircleButton("⌂", onHome, accent = true)
        Spacer(Modifier.width(9.dp))
        CircleButton("${state.session.tabs.size}", onTabs, accent = true)
        Spacer(Modifier.width(9.dp))
        CircleButton("⋮", onMenu)
    }
}

@Composable
private fun CircleButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (accent) SubSaffron.copy(alpha = 0.11f)
                else SubSurface.copy(alpha = 0.97f),
            )
            .border(
                1.dp,
                if (accent) SubSaffron.copy(alpha = 0.40f)
                else SubSurfaceElevated,
                CircleShape,
            )
            .alpha(if (enabled) 1f else 0.25f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (accent) SubSaffron else SubTextPrimary,
            fontSize = if (label.length > 1) 9.sp else 17.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StartPage(
    onSearch: () -> Unit,
    onNewTab: () -> Unit,
    onPrivate: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, top = 78.dp, bottom = 58.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SUB",
                    color = SubSaffron,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Speed Dial",
                    color = SubTextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            CircleButton("+", onNewTab, accent = true)
        }

        Spacer(Modifier.height(13.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(SubSurface)
                .border(1.dp, SubSurfaceElevated, RoundedCornerShape(19.dp))
                .clickable(onClick = onSearch)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕", color = SubSaffron, fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                "Search or enter address",
                color = SubTextSecondary,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CompactAction("P", "Private", onPrivate, Modifier.weight(1f))
            CompactAction("★", "Bookmarks", onBookmarks, Modifier.weight(1f))
            CompactAction("▣", "Tabs", onTabs, Modifier.weight(1f))
            CompactAction("+", "New tab", onNewTab, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Your space",
            color = SubTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(5.dp))

        Text(
            "Open a page to begin",
            color = SubTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(3.dp))

        Text(
            "Your tabs, history and tools stay out of the way.",
            color = SubTextSecondary,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun CompactAction(
    symbol: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(SubSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol,
                color = SubSaffron,
                fontSize = if (symbol == "★") 10.sp else 11.sp,
            )
        }

        Spacer(Modifier.width(5.dp))

        Text(
            title,
            color = SubTextPrimary,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TabsPanel(
    state: BrowserState,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onPrivate: () -> Unit,
) {
    BrowserPanel {
        PanelHeader("Tabs", "${state.session.tabs.size} open", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QuickPill("New tab", onNew)
            QuickPill("Private", onPrivate)
        }

        Spacer(Modifier.height(11.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(state.session.tabs, key = { it.id }) { tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (tab.id == state.session.activeTabId)
                                SubSaffron.copy(alpha = 0.09f)
                            else SubSurface,
                        )
                        .border(
                            1.dp,
                            if (tab.id == state.session.activeTabId)
                                SubSaffron.copy(alpha = 0.34f)
                            else SubSurfaceElevated,
                            RoundedCornerShape(11.dp),
                        )
                        .clickable { onSelect(tab.id) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SubSurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (tab.isPrivate) "P" else "${tab.id}",
                            color = if (tab.isPrivate) SubSaffron else SubTextSecondary,
                            fontSize = 9.sp,
                        )
                    }

                    Spacer(Modifier.width(9.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tab.title.ifBlank { "New Tab" },
                            color = SubTextPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            tab.url,
                            color = SubTextSecondary,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }

                    Text(
                        "×",
                        color = SubTextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onClose(tab.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuPanel(
    onBack: () -> Unit,
    onTabs: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onDownloads: () -> Unit,
    onPrivacy: () -> Unit,
    onSettings: () -> Unit,
    onPageTools: () -> Unit,
    onPrivate: () -> Unit,
) {
    BrowserPanel {
        PanelHeader("Menu", "Browser controls", onBack)

        MenuSection("Browse") {
            MenuRow("Tabs", "Open pages", onTabs)
            MenuRow("New private tab", "Private browsing", onPrivate)
            MenuRow("History", "Visited pages", onHistory)
            MenuRow("Bookmarks", "Saved websites", onBookmarks)
            MenuRow("Downloads", "Downloaded files", onDownloads)
        }

        MenuSection("Page") {
            MenuRow("Page tools", "Find, reader, translate, share and more", onPageTools)
        }

        MenuSection("Protection") {
            MenuRow("Privacy", "Tracking and site controls", onPrivacy)
        }

        MenuSection("Browser") {
            MenuRow("Settings", "Browser preferences", onSettings)
        }
    }
}

@Composable
private fun SettingsPanel(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
) {
    BrowserPanel {
        PanelHeader("Settings", "Browser preferences", onBack)

        MenuSection("General") {
            MenuRow("Appearance", "Theme and browser layout", onAppearance)
            MenuRow("Search engine", "Default search provider", {})
            MenuRow("Home page", "Start page behaviour", {})
            MenuRow("Open links", "Tabs and external apps", {})
            MenuRow("Language", "Browser language", {})
        }

        MenuSection("Browsing") {
            MenuRow("Downloads", "Location and behaviour", {})
            MenuRow("Autoplay", "Media playback", {})
            MenuRow("Popups", "Window handling", {})
            MenuRow("Page zoom", "Default page zoom", {})
        }

        MenuSection("Privacy & security") {
            MenuRow("Cookies", "Cookie behaviour", {})
            MenuRow("JavaScript", "Site scripting", {})
            MenuRow("Safe Browsing", "Dangerous site protection", {})
            MenuRow("Clear browsing data", "Delete local browser data", {})
        }
    }
}

@Composable
private fun SimplePanel(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    rows: List<String>,
) {
    BrowserPanel {
        PanelHeader(title, subtitle, onBack)

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(rows) { row ->
                MenuRow(row, "Ready to wire", {})
            }
        }
    }
}

@Composable
private fun MenuSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            title,
            color = SubSaffron,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        content()
        Spacer(Modifier.height(11.dp))
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SubTextPrimary, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = SubTextSecondary, fontSize = 9.sp)
        }
        Text("›", color = SubTextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun PanelHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton("‹", onBack)

        Spacer(Modifier.width(9.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = SubTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = SubTextSecondary,
                fontSize = 9.sp,
            )
        }
    }

    Spacer(Modifier.height(13.dp))
}

@Composable
private fun BrowserPanel(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        content()
    }
}

@Composable
private fun CrashPanel(onReload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Web content stopped",
            color = SubTextPrimary,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "The page renderer ended unexpectedly.",
            color = SubTextSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(13.dp))
        QuickPill("Reload", onReload)
    }
}
