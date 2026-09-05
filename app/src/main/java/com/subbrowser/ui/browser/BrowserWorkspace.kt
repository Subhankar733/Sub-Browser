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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
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
import com.subbrowser.ui.theme.SubTextPrimary
import com.subbrowser.ui.theme.SubTextSecondary

private val BorderColor = Color(0xFF2C2C2E)
private val CardBg = Color(0xFF1C1C1E)
private val AccentColor = SubSaffron

private data class QuickShortcut(val name: String, val url: String, val iconText: String)

private val defaultShortcuts = listOf(
    QuickShortcut("Google", "https://www.google.com", "G"),
    QuickShortcut("YouTube", "https://www.youtube.com", "YT"),
    QuickShortcut("GitHub", "https://www.github.com", "GH"),
    QuickShortcut("Wikipedia", "https://www.wikipedia.org", "W"),
    QuickShortcut("Reddit", "https://www.reddit.com", "R"),
    QuickShortcut("Twitter", "https://www.x.com", "X")
)

@Composable
fun BrowserWorkspace(
    controller: BrowserController = remember { BrowserController() },
) {
    var state by remember { mutableStateOf(BrowserState()) }
    var menuOpen by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    var homeSearchText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    DisposableEffect(controller) {
        controller.observe { newState ->
            state = newState
            if (newState.url != "about:blank" && newState.url.isNotBlank()) {
                urlText = newState.url
            }
        }
        onDispose { controller.clearObserver() }
    }

    val isHome = state.url.isBlank() || state.url == "about:blank"

    BackHandler(enabled = menuOpen) {
        menuOpen = false
    }

    BackHandler(enabled = !menuOpen && (state.canGoBack || !isHome)) {
        if (state.canGoBack) {
            controller.goBack()
        } else {
            controller.navigate("about:blank")
            urlText = ""
            homeSearchText = ""
        }
    }

    val submitNavigation: (String) -> Unit = { rawQuery ->
        val trimmed = rawQuery.trim()
        if (trimmed.isNotEmpty()) {
            focusManager.clearFocus()
            controller.navigate(trimmed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ওপরে অ্যাড্রেস বার
        BrowserTopBar(
            currentText = if (isHome) "" else urlText,
            isLoading = state.loading,
            progress = state.progress,
            onTextChange = { urlText = it },
            onSubmit = { submitNavigation(urlText) },
            onRefresh = {
                if (state.loading) controller.stop() else controller.reload()
            }
        )

        // মূল ভিউ
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).also {
                        configureBrowserWebView(it, controller)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { controller.syncForUi() },
                onRelease = { controller.dispose(it) },
            )

            if (isHome) {
                BrowserHomeScreen(
                    searchQuery = homeSearchText,
                    onSearchChange = { homeSearchText = it },
                    onSearchSubmit = { submitNavigation(homeSearchText) },
                    onShortcutClick = { targetUrl ->
                        submitNavigation(targetUrl)
                    }
                )
            }
        }

        // নিচের নেভিগেশন বার
        BrowserBottomBar(
            canGoBack = state.canGoBack,
            canGoForward = state.canGoForward,
            tabCount = 1,
            onBack = {
                if (state.canGoBack) {
                    controller.goBack()
                } else {
                    controller.navigate("about:blank")
                    urlText = ""
                    homeSearchText = ""
                }
            },
            onForward = controller::goForward,
            onHome = {
                controller.navigate("about:blank")
                urlText = ""
                homeSearchText = ""
            },
            onTabs = { menuOpen = true },
            onMenu = { menuOpen = true }
        )
    }

    if (menuOpen) {
        BrowserActionMenu(
            onClose = { menuOpen = false },
            onNewTab = {
                controller.newTab()
                urlText = ""
                homeSearchText = ""
                menuOpen = false
            },
            onPrivateTab = {
                controller.newTab(isPrivate = true)
                urlText = ""
                homeSearchText = ""
                menuOpen = false
            },
            onReload = {
                controller.reload()
                menuOpen = false
            }
        )
    }
}

@Composable
private fun BrowserTopBar(
    currentText: String,
    isLoading: Boolean,
    progress: Int,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SubSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(22.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentText.startsWith("https://")) "🔒" else "🌐",
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            BasicTextField(
                value = currentText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = SubTextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(AccentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                decorationBox = { innerTextField ->
                    if (currentText.isEmpty()) {
                        Text("Search or enter web address...", color = SubTextSecondary, fontSize = 12.sp)
                    }
                    innerTextField()
                }
            )

            Text(
                text = if (isLoading) "✕" else "↻",
                color = SubTextSecondary,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRefresh)
                    .padding(4.dp)
            )
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { (progress.coerceIn(5, 100)) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(2.dp),
                color = AccentColor,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun BrowserHomeScreen(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onShortcutClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(2.dp, AccentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = AccentColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        Text("Sub Browser", color = SubTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Fast, private and secure browsing", color = SubTextSecondary, fontSize = 12.sp)

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔍", fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = SubTextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(AccentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text("Search Google or type a URL", color = SubTextSecondary, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            )
        }

        Spacer(Modifier.height(30.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(defaultShortcuts) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShortcutClick(item.url) }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CardBg)
                            .border(1.dp, BorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.iconText, color = SubTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(item.name, color = SubTextSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SubSurface)
            .border(1.dp, BorderColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomBarIcon(icon = "◀", enabled = canGoBack, onClick = onBack)
        BottomBarIcon(icon = "▶", enabled = canGoForward, onClick = onForward)
        BottomBarIcon(icon = "⌂", enabled = true, onClick = onHome)

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, SubTextPrimary, RoundedCornerShape(8.dp))
                .clickable(onClick = onTabs),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tabCount.toString(),
                color = SubTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        BottomBarIcon(icon = "⋮", enabled = true, onClick = onMenu)
    }
}

@Composable
private fun BottomBarIcon(
    icon: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            color = if (enabled) SubTextPrimary else Color(0xFF4A4A4A),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun BrowserActionMenu(
    onClose: () -> Unit,
    onNewTab: () -> Unit,
    onPrivateTab: () -> Unit,
    onReload: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onClose)
            .padding(bottom = 60.dp, end = 12.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(8.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Text(
                "MENU",
                color = AccentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            ActionMenuItem(icon = "＋", title = "New Tab", onClick = onNewTab)
            ActionMenuItem(icon = "🕶", title = "Private Space", onClick = onPrivateTab)
            ActionMenuItem(icon = "↻", title = "Reload Page", onClick = onReload)
            ActionMenuItem(icon = "⭐", title = "Bookmarks", onClick = onClose)
            ActionMenuItem(icon = "🕒", title = "History", onClick = onClose)
            ActionMenuItem(icon = "⚙", title = "Settings", onClick = onClose)
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: String,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp, modifier = Modifier.width(24.dp))
        Text(title, color = SubTextPrimary, fontSize = 12.sp)
    }
}
