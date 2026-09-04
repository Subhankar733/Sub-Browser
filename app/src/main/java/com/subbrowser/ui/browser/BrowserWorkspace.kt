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
 * Clean Sub Browser UI.
 *
 * Information architecture:
 *   1. One compact browser bar.
 *   2. One Speed Dial start page.
 *   3. One bottom navigation row.
 *   4. Full-screen sheets for tabs/menu/settings.
 *
 * This is a clean-room implementation. It does not copy another
 * browser's source code, assets, layouts, or branding.
 */

private enum class Surface {
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
    var webViewEpoch by remember { mutableIntStateOf(0) }
    var surface by remember { mutableStateOf(Surface.NONE) }
    val addressState = rememberTextFieldState()
    val context = LocalContext.current

    DisposableEffect(controller) {
        controller.observe { state = it }
        onDispose { controller.clearObserver() }
    }

    BackHandler(enabled = surface != Surface.NONE) {
        surface = when (surface) {
            Surface.SEARCH,
            Surface.TABS,
            Surface.MENU,
            Surface.PAGE_TOOLS -> Surface.NONE

            Surface.HISTORY,
            Surface.BOOKMARKS,
            Surface.DOWNLOADS,
            Surface.PRIVACY,
            Surface.SETTINGS,
            Surface.APPEARANCE -> Surface.MENU

            Surface.NONE -> Surface.NONE
        }
    }

    BackHandler(enabled = surface == Surface.NONE && state.canGoBack) {
        controller.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack),
    ) {
        if (state.rendererCrashed) {
            CrashPage(
                onRecover = {
                    controller.resetAfterRendererCrash()
                    webViewEpoch++
                },
            )
        } else {
            key(webViewEpoch) {
                AndroidView(
                    factory = {
                        WebView(context).also {
                            configureBrowserWebView(it, controller)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { controller.syncForUi() },
                    onRelease = { controller.dispose(it) },
                )
            }

            if (state.url == "about:blank" && surface == Surface.NONE) {
                StartPage(
                    onSearch = { surface = Surface.SEARCH },
                    onNewTab = {
                        controller.newTab()
                        webViewEpoch++
                    },
                    onPrivate = {
                        controller.newTab(isPrivate = true)
                        webViewEpoch++
                    },
                    onTabs = { surface = Surface.TABS },
                    onBookmarks = { surface = Surface.BOOKMARKS },
                )
            }

            if (surface == Surface.NONE || surface == Surface.SEARCH) {
                BrowserBar(
                    state = state,
                    addressState = addressState,
                    editing = surface == Surface.SEARCH,
                    onEdit = { surface = Surface.SEARCH },
                    onCancel = { surface = Surface.NONE },
                    onSubmit = {
                        val value = addressState.text.toString().trim()
                        if (value.isNotEmpty()) controller.navigate(value)
                        addressState.edit { replace(0, length, "") }
                        surface = Surface.NONE
                    },
                    onTabs = { surface = Surface.TABS },
                    onMenu = { surface = Surface.MENU },
                )
            }

            if (surface == Surface.NONE) {
                BottomNav(
                    state = state,
                    onBack = controller::goBack,
                    onHome = {
                        if (state.url == "about:blank") {
                            surface = Surface.NONE
                        } else {
                            controller.navigate("about:blank")
                        }
                    },
                    onTabs = { surface = Surface.TABS },
                    onMenu = { surface = Surface.MENU },
                )
            }
        }

        when (surface) {
            Surface.NONE -> Unit

            Surface.SEARCH -> Unit

            Surface.TABS -> TabsSheet(
                state = state,
                onBack = { surface = Surface.NONE },
                onSelect = {
                    controller.selectTab(it)
                    surface = Surface.NONE
                    webViewEpoch++
                },
                onClose = {
                    controller.closeTab(it)
                    webViewEpoch++
                },
                onNew = {
                    controller.newTab()
                    surface = Surface.NONE
                    webViewEpoch++
                },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    surface = Surface.NONE
                    webViewEpoch++
                },
            )

            Surface.MENU -> MenuSheet(
                onBack = { surface = Surface.NONE },
                onTabs = { surface = Surface.TABS },
                onHistory = { surface = Surface.HISTORY },
                onBookmarks = { surface = Surface.BOOKMARKS },
                onDownloads = { surface = Surface.DOWNLOADS },
                onPrivacy = { surface = Surface.PRIVACY },
                onSettings = { surface = Surface.SETTINGS },
                onPageTools = { surface = Surface.PAGE_TOOLS },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    surface = Surface.NONE
                    webViewEpoch++
                },
            )

            Surface.HISTORY -> ListSheet(
                title = "History",
                subtitle = "Recently visited pages",
                onBack = { surface = Surface.MENU },
                items = listOf(
                    "Today",
                    "Yesterday",
                    "Last 7 days",
                    "Search history",
                    "Clear browsing data",
                ),
            )

            Surface.BOOKMARKS -> ListSheet(
                title = "Bookmarks",
                subtitle = "Your saved websites",
                onBack = { surface = Surface.MENU },
                items = listOf(
                    "All bookmarks",
                    "Mobile bookmarks",
                    "Add bookmark",
                    "Bookmark folders",
                ),
            )

            Surface.DOWNLOADS -> ListSheet(
                title = "Downloads",
                subtitle = "Files from the web",
                onBack = { surface = Surface.MENU },
                items = listOf(
                    "All downloads",
                    "Active downloads",
                    "Download location",
                    "Clear downloads",
                ),
            )

            Surface.PRIVACY -> ListSheet(
                title = "Privacy",
                subtitle = "Protection and site controls",
                onBack = { surface = Surface.MENU },
                items = listOf(
                    "Block trackers",
                    "Block ads",
                    "Anti-fingerprinting",
                    "Safe Browsing",
                    "Cookies",
                    "Site permissions",
                    "Clear site data",
                ),
            )

            Surface.SETTINGS -> ListSheet(
                title = "Settings",
                subtitle = "Browser preferences",
                onBack = { surface = Surface.MENU },
                items = listOf(
                    "Search engine",
                    "Home page",
                    "Open links",
                    "Downloads",
                    "Autoplay",
                    "Popups",
                    "Page zoom",
                    "Cookies",
                    "JavaScript",
                    "Safe Browsing",
                    "Clear browsing data",
                ),
                extra = {
                    MenuRow(
                        "Appearance",
                        "Theme and browser layout",
                        { surface = Surface.APPEARANCE },
                    )
                },
            )

            Surface.APPEARANCE -> ListSheet(
                title = "Appearance",
                subtitle = "Keep the browser compact",
                onBack = { surface = Surface.SETTINGS },
                items = listOf(
                    "System theme",
                    "Light theme",
                    "Dark theme",
                    "Compact layout",
                    "Fullscreen",
                ),
            )

            Surface.PAGE_TOOLS -> ListSheet(
                title = "Page tools",
                subtitle = "Tools for the current page",
                onBack = { surface = Surface.NONE },
                items = listOf(
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
    addressState: TextFieldState,
    editing: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    val label = when {
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
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SubSurface)
                .border(1.dp, SubSurfaceElevated, CircleShape)
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "S",
                color = SubSaffron,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(6.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(SubSurface)
                .border(
                    1.dp,
                    if (editing) SubSaffron.copy(alpha = 0.55f)
                    else SubSurfaceElevated,
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
                    state = addressState,
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
                        if (addressState.text.isEmpty()) {
                            Text(
                                label,
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
                    modifier = Modifier.clickable(onClick = onCancel),
                )
            } else {
                Text(
                    label,
                    color = if (state.title.isNotBlank() && state.title != "New Tab")
                        SubTextPrimary else SubTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        RoundButton(
            label = "${state.session.tabs.size}",
            onClick = onTabs,
            accent = true,
        )

        Spacer(Modifier.width(5.dp))

        RoundButton(
            label = "⋮",
            onClick = onMenu,
        )
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(
            label = "‹",
            onClick = onBack,
            enabled = state.canGoBack,
        )

        Spacer(Modifier.width(8.dp))

        RoundButton(
            label = "⌂",
            onClick = onHome,
            accent = true,
        )

        Spacer(Modifier.width(8.dp))

        RoundButton(
            label = "${state.session.tabs.size}",
            onClick = onTabs,
            accent = true,
        )

        Spacer(Modifier.width(8.dp))

        RoundButton(
            label = "⋮",
            onClick = onMenu,
        )
    }
}

@Composable
private fun RoundButton(
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
                else SubSurface.copy(alpha = 0.96f),
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
            .padding(start = 18.dp, end = 18.dp, top = 86.dp, bottom = 70.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SUB",
                    color = SubSaffron,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Speed Dial",
                    color = SubTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            RoundButton(
                label = "+",
                onClick = onNewTab,
                accent = true,
            )
        }

        Spacer(Modifier.height(18.dp))

        SpeedGrid(
            onSearch = onSearch,
            onPrivate = onPrivate,
            onTabs = onTabs,
            onBookmarks = onBookmarks,
        )

        Spacer(Modifier.height(15.dp))

        Text(
            "Quick access",
            color = SubTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            QuickPill("New tab", onNewTab)
            QuickPill("Private", onPrivate)
            QuickPill("Bookmarks", onBookmarks)
        }
    }
}

@Composable
private fun SpeedGrid(
    onSearch: () -> Unit,
    onPrivate: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedCard(
                title = "Search",
                symbol = "⌕",
                onClick = onSearch,
                modifier = Modifier.weight(1f),
            )
            SpeedCard(
                title = "Private",
                symbol = "P",
                onClick = onPrivate,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedCard(
                title = "Bookmarks",
                symbol = "★",
                onClick = onBookmarks,
                modifier = Modifier.weight(1f),
            )
            SpeedCard(
                title = "Tabs",
                symbol = "▣",
                onClick = onTabs,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SpeedCard(
    title: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(SubSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol,
                color = SubSaffron,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            title,
            color = SubTextPrimary,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun QuickPill(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, color = SubTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun TabsSheet(
    state: BrowserState,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onPrivate: () -> Unit,
) {
    BrowserSheet {
        SheetHeader(
            title = "Tabs",
            subtitle = "${state.session.tabs.size} open",
            onBack = onBack,
        )

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
private fun MenuSheet(
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
    BrowserSheet {
        SheetHeader(
            title = "Menu",
            subtitle = "Browser controls",
            onBack = onBack,
        )

        MenuSection("Browse") {
            MenuRow("Tabs", "Switch between open pages", onTabs)
            MenuRow("New private tab", "Private browsing", onPrivate)
            MenuRow("History", "Recently visited pages", onHistory)
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
            MenuRow("Settings", "Search, downloads, appearance and more", onSettings)
        }
    }
}

@Composable
private fun ListSheet(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    items: List<String>,
    extra: @Composable (() -> Unit)? = null,
) {
    BrowserSheet {
        SheetHeader(title = title, subtitle = subtitle, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(items) { item ->
                MenuRow(item, "Ready to wire", {})
            }
            if (extra != null) {
                item { extra() }
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
private fun SheetHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton("‹", onBack)

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

    androidx.compose.foundation.layout.Spacer(Modifier.height(13.dp))
}

@Composable
private fun BrowserSheet(
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
private fun CrashPage(
    onRecover: () -> Unit,
) {
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
        Text(
            "The page renderer ended unexpectedly.",
            color = SubTextSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(13.dp))
        QuickPill("Reload", onRecover)
    }
}
