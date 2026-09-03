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
 * Opera-Mini-inspired browser shell, implemented clean-room for Sub Browser.
 *
 * Design goals:
 * - familiar mobile-browser information architecture
 * - compact combined search/address field
 * - bottom thumb-friendly navigation
 * - Speed Dial start page
 * - visual tab tray
 * - single menu entry for the complete feature catalogue
 * - feature actions are intentionally staged; behaviour can be wired later
 * - no dependency on another browser's source code, assets, or branding
 */

private enum class BrowserSurface {
    NONE,
    SEARCH,
    TABS,
    MENU,
    SPEED_DIAL,
    HISTORY,
    BOOKMARKS,
    DOWNLOADS,
    SAVED,
    PRIVACY,
    SETTINGS,
    APPEARANCE,
    READER,
    TRANSLATE,
    FIND,
    SITE_INFO,
}

@Composable
fun BrowserWorkspace(
    controller: BrowserController = remember { BrowserController() },
) {
    var state by remember { mutableStateOf(BrowserState()) }
    var webViewEpoch by remember { mutableIntStateOf(0) }
    var surface by remember { mutableStateOf(BrowserSurface.NONE) }
    val searchState = rememberTextFieldState()
    val context = LocalContext.current

    DisposableEffect(controller) {
        controller.observe { state = it }
        onDispose { controller.clearObserver() }
    }

    BackHandler(enabled = surface != BrowserSurface.NONE) {
        surface = when (surface) {
            BrowserSurface.SEARCH -> BrowserSurface.NONE
            BrowserSurface.TABS -> BrowserSurface.NONE
            BrowserSurface.MENU -> BrowserSurface.NONE
            BrowserSurface.SPEED_DIAL -> BrowserSurface.NONE
            BrowserSurface.HISTORY -> BrowserSurface.MENU
            BrowserSurface.BOOKMARKS -> BrowserSurface.MENU
            BrowserSurface.DOWNLOADS -> BrowserSurface.MENU
            BrowserSurface.SAVED -> BrowserSurface.MENU
            BrowserSurface.PRIVACY -> BrowserSurface.MENU
            BrowserSurface.SETTINGS -> BrowserSurface.MENU
            BrowserSurface.APPEARANCE -> BrowserSurface.SETTINGS
            BrowserSurface.READER -> BrowserSurface.MENU
            BrowserSurface.TRANSLATE -> BrowserSurface.MENU
            BrowserSurface.FIND -> BrowserSurface.NONE
            BrowserSurface.SITE_INFO -> BrowserSurface.NONE
            BrowserSurface.NONE -> BrowserSurface.NONE
        }
    }

    BackHandler(enabled = surface == BrowserSurface.NONE && state.canGoBack) {
        controller.goBack()
    }

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
            key(webViewEpoch) {
                AndroidView(
                    factory = {
                        WebView(context).also { configureBrowserWebView(it, controller) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { controller.syncForUi() },
                    onRelease = { controller.dispose(it) },
                )
            }

            if (state.url == "about:blank" && surface == BrowserSurface.NONE) {
                SpeedDialPage(
                    onSearch = { surface = BrowserSurface.SEARCH },
                    onNewTab = {
                        controller.newTab()
                        webViewEpoch++
                    },
                    onPrivate = {
                        controller.newTab(isPrivate = true)
                        webViewEpoch++
                    },
                    onTabs = { surface = BrowserSurface.TABS },
                    onBookmarks = { surface = BrowserSurface.BOOKMARKS },
                )
            }

            if (surface == BrowserSurface.NONE || surface == BrowserSurface.SEARCH) {
                TopBrowserBar(
                    state = state,
                    textState = searchState,
                    expanded = surface == BrowserSurface.SEARCH,
                    onSearch = { surface = BrowserSurface.SEARCH },
                    onDismiss = { surface = BrowserSurface.NONE },
                    onSubmit = {
                        val value = searchState.text.toString().trim()
                        if (value.isNotEmpty()) controller.navigate(value)
                        searchState.edit { replace(0, length, "") }
                        surface = BrowserSurface.NONE
                    },
                    onTabs = { surface = BrowserSurface.TABS },
                    onMenu = { surface = BrowserSurface.MENU },
                    onSiteInfo = { surface = BrowserSurface.SITE_INFO },
                )
            }

            if (surface == BrowserSurface.NONE) {
                BottomBrowserBar(
                    state = state,
                    onBack = controller::goBack,
                    onForward = controller::goForward,
                    onSpeedDial = {
                        if (state.url == "about:blank") surface = BrowserSurface.SPEED_DIAL
                        else controller.navigate("about:blank")
                    },
                    onTabs = { surface = BrowserSurface.TABS },
                    onMenu = { surface = BrowserSurface.MENU },
                )
            }
        }

        when (surface) {
            BrowserSurface.SEARCH -> Unit
            BrowserSurface.TABS -> TabTray(
                state = state,
                onDismiss = { surface = BrowserSurface.NONE },
                onSelect = {
                    controller.selectTab(it)
                    surface = BrowserSurface.NONE
                    webViewEpoch++
                },
                onClose = {
                    controller.closeTab(it)
                    webViewEpoch++
                },
                onNew = {
                    controller.newTab()
                    surface = BrowserSurface.NONE
                    webViewEpoch++
                },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    surface = BrowserSurface.NONE
                    webViewEpoch++
                },
            )
            BrowserSurface.MENU -> MainMenu(
                onDismiss = { surface = BrowserSurface.NONE },
                onSpeedDial = { surface = BrowserSurface.SPEED_DIAL },
                onTabs = { surface = BrowserSurface.TABS },
                onHistory = { surface = BrowserSurface.HISTORY },
                onBookmarks = { surface = BrowserSurface.BOOKMARKS },
                onDownloads = { surface = BrowserSurface.DOWNLOADS },
                onSaved = { surface = BrowserSurface.SAVED },
                onPrivacy = { surface = BrowserSurface.PRIVACY },
                onSettings = { surface = BrowserSurface.SETTINGS },
                onFind = { surface = BrowserSurface.FIND },
                onReader = { surface = BrowserSurface.READER },
                onTranslate = { surface = BrowserSurface.TRANSLATE },
                onNewPrivate = {
                    controller.newTab(isPrivate = true)
                    webViewEpoch++
                    surface = BrowserSurface.NONE
                },
            )
            BrowserSurface.SPEED_DIAL -> SpeedDialPage(
                onSearch = { surface = BrowserSurface.SEARCH },
                onNewTab = {
                    controller.newTab()
                    surface = BrowserSurface.NONE
                    webViewEpoch++
                },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    surface = BrowserSurface.NONE
                    webViewEpoch++
                },
                onTabs = { surface = BrowserSurface.TABS },
                onBookmarks = { surface = BrowserSurface.BOOKMARKS },
            )
            BrowserSurface.HISTORY -> FeatureListSurface(
                title = "History",
                subtitle = "Recently visited pages",
                onBack = { surface = BrowserSurface.MENU },
                items = listOf("Today", "Yesterday", "Last 7 days", "Search history"),
            )
            BrowserSurface.BOOKMARKS -> FeatureListSurface(
                title = "Bookmarks",
                subtitle = "Your saved sites",
                onBack = { surface = BrowserSurface.MENU },
                items = listOf("All bookmarks", "Mobile bookmarks", "Add bookmark", "Bookmark folders"),
            )
            BrowserSurface.DOWNLOADS -> FeatureListSurface(
                title = "Downloads",
                subtitle = "Files saved from the web",
                onBack = { surface = BrowserSurface.MENU },
                items = listOf("All downloads", "Active downloads", "Download location", "Clear downloads"),
            )
            BrowserSurface.SAVED -> FeatureListSurface(
                title = "Saved pages",
                subtitle = "Offline reading area",
                onBack = { surface = BrowserSurface.MENU },
                items = listOf("All saved pages", "Reader list", "Offline pages"),
            )
            BrowserSurface.PRIVACY -> PrivacySurface(onBack = { surface = BrowserSurface.MENU })
            BrowserSurface.SETTINGS -> SettingsSurface(
                onBack = { surface = BrowserSurface.MENU },
                onAppearance = { surface = BrowserSurface.APPEARANCE },
            )
            BrowserSurface.APPEARANCE -> FeatureListSurface(
                title = "Appearance",
                subtitle = "Browser layout and visual preferences",
                onBack = { surface = BrowserSurface.SETTINGS },
                items = listOf("Light", "Dark", "System", "Compact toolbar", "Fullscreen"),
            )
            BrowserSurface.READER -> FeatureListSurface(
                title = "Reader view",
                subtitle = "Reading tools",
                onBack = { surface = BrowserSurface.MENU },
                items = listOf("Reader mode", "Font size", "Line spacing", "Page width"),
            )
            BrowserSurface.TRANSLATE -> FeatureListSurface(
                title = "Translate",
                subtitle = "Translate the current page",
                onBack = { surface = BrowserSurface.MENU },
                items = listOf("Translate page", "Choose language", "Always translate this language"),
            )
            BrowserSurface.FIND -> FeatureListSurface(
                title = "Find in page",
                subtitle = "Search within the current page",
                onBack = { surface = BrowserSurface.NONE },
                items = listOf("Find text", "Previous match", "Next match"),
            )
            BrowserSurface.SITE_INFO -> SiteInfoSurface(
                state = state,
                onBack = { surface = BrowserSurface.NONE },
            )
            BrowserSurface.NONE -> Unit
        }
    }
}

@Composable
private fun TopBrowserBar(
    state: BrowserState,
    textState: TextFieldState,
    expanded: Boolean,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    onSiteInfo: () -> Unit,
) {
    val title = when {
        expanded -> "Search or enter address"
        state.loading -> "Loading ${state.progress}%"
        state.title.isNotBlank() && state.title != "New Tab" -> state.title
        else -> "Search or enter address"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniIconButton("⌂", onClick = onSearch, emphasized = !expanded)

        Spacer(Modifier.width(5.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(SubSurface.copy(alpha = 0.96f))
                .border(
                    1.dp,
                    if (expanded) SubSaffron.copy(alpha = 0.55f) else SubSurfaceElevated,
                    RoundedCornerShape(19.dp),
                )
                .clickable(onClick = onSearch)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.secureConnection) "●" else "○",
                color = if (state.secureConnection) SubSaffron else SubTextSecondary,
                fontSize = 8.sp,
                modifier = Modifier.clickable(onClick = onSiteInfo),
            )
            Spacer(Modifier.width(7.dp))

            if (expanded) {
                BasicTextField(
                    state = textState,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = SubTextPrimary, fontSize = 12.sp),
                    cursorBrush = SolidColor(SubSaffron),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    onKeyboardAction = { onSubmit() },
                    decorator = { inner ->
                        if (textState.text.isEmpty()) {
                            Text(title, color = SubTextSecondary, fontSize = 12.sp, maxLines = 1)
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
                        .clickable(onClick = onDismiss),
                )
            } else {
                Text(
                    title,
                    color = if (state.title != "New Tab") SubTextPrimary else SubTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.width(5.dp))
        MiniIconButton("${state.session.tabs.size}", onClick = onTabs, emphasized = true)
        Spacer(Modifier.width(4.dp))
        MiniIconButton("⋮", onClick = onMenu)
    }
}

@Composable
private fun BottomBrowserBar(
    state: BrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSpeedDial: () -> Unit,
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
        MiniIconButton("‹", onBack, enabled = state.canGoBack)
        Spacer(Modifier.width(7.dp))
        MiniIconButton("›", onForward, enabled = state.canGoForward)
        Spacer(Modifier.width(9.dp))
        MiniIconButton("⌂", onSpeedDial, emphasized = true)
        Spacer(Modifier.width(9.dp))
        MiniIconButton("${state.session.tabs.size}", onTabs, emphasized = true)
        Spacer(Modifier.width(7.dp))
        MiniIconButton("⋮", onMenu)
    }
}

@Composable
private fun MiniIconButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) SubSaffron.copy(alpha = 0.12f)
                else SubSurface.copy(alpha = 0.94f),
            )
            .border(
                1.dp,
                if (emphasized) SubSaffron.copy(alpha = 0.42f)
                else SubSurfaceElevated,
                CircleShape,
            )
            .alpha(if (enabled) 1f else 0.28f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (emphasized) SubSaffron else SubTextPrimary,
            fontSize = if (label.length > 1) 9.sp else 16.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SpeedDialPage(
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
            .padding(horizontal = 16.dp, vertical = 56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SUB BROWSER",
                    color = SubSaffron,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Speed Dial",
                    color = SubTextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            MiniIconButton("+", onClick = onNewTab, emphasized = true)
        }

        Spacer(Modifier.height(15.dp))

        SearchStartCard(onClick = onSearch)

        Spacer(Modifier.height(15.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedTile("Google", "G", onSearch, Modifier.weight(1f))
            SpeedTile("YouTube", "▶", onSearch, Modifier.weight(1f))
            SpeedTile("Wikipedia", "W", onSearch, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedTile("Bookmarks", "★", onBookmarks, Modifier.weight(1f))
            SpeedTile("Private", "P", onPrivate, Modifier.weight(1f))
            SpeedTile("Tabs", "▣", onTabs, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Quick actions",
            color = SubTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(7.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TinyAction("New tab", onNewTab)
            TinyAction("Private", onPrivate)
            TinyAction("Tabs", onTabs)
        }
    }
}

@Composable
private fun SearchStartCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(43.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = SubSaffron, fontSize = 17.sp)
        Spacer(Modifier.width(8.dp))
        Text("Search or enter address", color = SubTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SpeedTile(
    title: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(SubSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, color = SubSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Text(title, color = SubTextPrimary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun TinyAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(label, color = SubTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun TabTray(
    state: BrowserState,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onPrivate: () -> Unit,
) {
    FullBrowserSurface {
        SurfaceHeader(
            title = "Tabs",
            subtitle = "${state.session.tabs.size} open",
            onBack = onDismiss,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TinyAction("New tab", onNew)
            TinyAction("Private", onPrivate)
        }

        Spacer(Modifier.height(10.dp))

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
                            .size(27.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SubSurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${tab.id}",
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
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.url,
                            color = SubTextSecondary,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                    Text(
                        if (tab.isPrivate) "PRIVATE" else "×",
                        color = if (tab.isPrivate) SubSaffron else SubTextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { if (!tab.isPrivate) onClose(tab.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMenu(
    onDismiss: () -> Unit,
    onSpeedDial: () -> Unit,
    onTabs: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onDownloads: () -> Unit,
    onSaved: () -> Unit,
    onPrivacy: () -> Unit,
    onSettings: () -> Unit,
    onFind: () -> Unit,
    onReader: () -> Unit,
    onTranslate: () -> Unit,
    onNewPrivate: () -> Unit,
) {
    FullBrowserSurface {
        SurfaceHeader(
            title = "Menu",
            subtitle = "Everything in one place",
            onBack = onDismiss,
        )

        MenuSection("Browse") {
            MenuRow("Speed Dial", "Start page", onSpeedDial)
            MenuRow("Tabs", "Open pages", onTabs)
            MenuRow("New private tab", "Private browsing", onNewPrivate)
        }

        MenuSection("Page") {
            MenuRow("Find in page", "Search page text", onFind)
            MenuRow("Reader view", "Distraction-free reading", onReader)
            MenuRow("Translate", "Translate this page", onTranslate)
            MenuRow("Share", "Share current page", {})
            MenuRow("Save page", "Save for later", {})
            MenuRow("Download", "Save file", {})
        }

        MenuSection("Library") {
            MenuRow("History", "Visited pages", onHistory)
            MenuRow("Bookmarks", "Saved sites", onBookmarks)
            MenuRow("Downloads", "Downloaded files", onDownloads)
            MenuRow("Saved pages", "Offline reading", onSaved)
        }

        MenuSection("Protection") {
            MenuRow("Privacy", "Protection controls", onPrivacy)
            MenuRow("Site permissions", "Camera, mic, location", {})
            MenuRow("Connection", "Secure connection", {})
        }

        MenuSection("Browser") {
            MenuRow("Settings", "All browser settings", onSettings)
            MenuRow("Appearance", "Layout and theme", {})
            MenuRow("Clear browsing data", "History, cookies, cache", {})
        }
    }
}

@Composable
private fun MenuSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            color = SubSaffron,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        content()
        Spacer(Modifier.height(12.dp))
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
private fun SettingsSurface(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
) {
    FullBrowserSurface {
        SurfaceHeader(
            title = "Settings",
            subtitle = "Browser preferences",
            onBack = onBack,
        )

        MenuSection("General") {
            MenuRow("Appearance", "Theme, layout, fullscreen", onAppearance)
            MenuRow("Search engine", "Default search provider", {})
            MenuRow("Home page", "Start page behaviour", {})
            MenuRow("Open links", "Tabs and external apps", {})
            MenuRow("Language", "Browser language", {})
        }

        MenuSection("Browsing") {
            MenuRow("Downloads", "Location and behaviour", {})
            MenuRow("Autoplay", "Media playback", {})
            MenuRow("Popups", "Window and popup handling", {})
            MenuRow("Page zoom", "Default page zoom", {})
        }

        MenuSection("Privacy & security") {
            MenuRow("Privacy protection", "Tracking and ad controls", {})
            MenuRow("Cookies", "Cookie behaviour", {})
            MenuRow("JavaScript", "Site scripting", {})
            MenuRow("Safe Browsing", "Malicious site protection", {})
            MenuRow("Clear browsing data", "Delete local browser data", {})
        }
    }
}

@Composable
private fun PrivacySurface(onBack: () -> Unit) {
    FullBrowserSurface {
        SurfaceHeader(
            title = "Privacy",
            subtitle = "Protection controls",
            onBack = onBack,
        )

        MenuSection("Protection") {
            MenuRow("Block trackers", "Prevent common tracking scripts", {})
            MenuRow("Block ads", "Content filtering", {})
            MenuRow("Anti-fingerprinting", "Reduce identifying signals", {})
            MenuRow("Safe Browsing", "Warn about dangerous sites", {})
            MenuRow("Private browsing", "No normal history for private tabs", {})
        }

        MenuSection("Site data") {
            MenuRow("Cookies", "Control site cookies", {})
            MenuRow("Site permissions", "Camera, microphone, location", {})
            MenuRow("Clear site data", "Remove current site data", {})
            MenuRow("Clear browsing data", "History, cache, cookies", {})
        }
    }
}

@Composable
private fun FeatureListSurface(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    items: List<String>,
) {
    FullBrowserSurface {
        SurfaceHeader(title = title, subtitle = subtitle, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(items) { item ->
                MenuRow(item, "Ready to wire", {})
            }
        }
    }
}

@Composable
private fun SiteInfoSurface(
    state: BrowserState,
    onBack: () -> Unit,
) {
    FullBrowserSurface {
        SurfaceHeader(
            title = "Site information",
            subtitle = "Connection and permissions",
            onBack = onBack,
        )

        MenuSection("Connection") {
            MenuRow(
                if (state.secureConnection) "Secure connection" else "Connection not verified",
                state.url,
                {},
            )
        }

        MenuSection("Permissions") {
            MenuRow("Camera", "Ask / Allow / Block", {})
            MenuRow("Microphone", "Ask / Allow / Block", {})
            MenuRow("Location", "Ask / Allow / Block", {})
            MenuRow("Notifications", "Ask / Allow / Block", {})
        }
    }
}

@Composable
private fun SurfaceHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniIconButton("‹", onBack)
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = SubTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
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
private fun FullBrowserSurface(
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
private fun CrashSurface(onRecover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Web content stopped", color = SubTextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(7.dp))
        Text(
            "The page renderer ended unexpectedly.",
            color = SubTextSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(14.dp))
        TinyAction("Reload", onRecover)
    }
}
