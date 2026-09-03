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

private enum class Surface {
    NONE,
    COMMAND,
    SPACES,
    TOOLS,
    PRIVACY,
    SETTINGS,
}

@Composable
fun BrowserWorkspace(
    controller: BrowserController = remember { BrowserController() },
) {
    var state by remember { mutableStateOf(BrowserState()) }
    var webViewEpoch by remember { mutableIntStateOf(0) }
    var surface by remember { mutableStateOf(Surface.NONE) }
    val commandState = rememberTextFieldState()
    val context = LocalContext.current

    DisposableEffect(controller) {
        controller.observe { state = it }
        onDispose { controller.clearObserver() }
    }

    BackHandler(enabled = surface != Surface.NONE) {
        surface = Surface.NONE
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

            if (state.url == "about:blank" && surface == Surface.NONE) {
                HomeSurface(
                    onOpen = { surface = Surface.COMMAND },
                    onPrivate = {
                        controller.newTab(isPrivate = true)
                        webViewEpoch++
                    },
                    onSpaces = { surface = Surface.SPACES },
                )
            }

            CommandPortal(
                state = state,
                textState = commandState,
                expanded = surface == Surface.COMMAND,
                onOpen = { surface = Surface.COMMAND },
                onClose = { surface = Surface.NONE },
                onSubmit = {
                    val value = commandState.text.toString().trim()
                    if (value.isNotEmpty()) {
                        controller.navigate(value)
                    }
                    commandState.edit { replace(0, length, "") }
                    surface = Surface.NONE
                },
            )

            if (surface == Surface.NONE) {
                MinimalDock(
                    state = state,
                    onBack = controller::goBack,
                    onForward = controller::goForward,
                    onReload = {
                        if (state.loading) controller.stop() else controller.reload()
                    },
                    onSpaces = { surface = Surface.SPACES },
                    onTools = { surface = Surface.TOOLS },
                )
            }
        }

        when (surface) {
            Surface.SPACES -> SpacesSurface(
                state = state,
                onDismiss = { surface = Surface.NONE },
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

            Surface.TOOLS -> ToolsSurface(
                onDismiss = { surface = Surface.NONE },
                onSpaces = { surface = Surface.SPACES },
                onPrivacy = { surface = Surface.PRIVACY },
                onSettings = { surface = Surface.SETTINGS },
            )

            Surface.PRIVACY -> PrivacySurface(onDismiss = { surface = Surface.TOOLS })
            Surface.SETTINGS -> SettingsSurface(onDismiss = { surface = Surface.TOOLS })
            Surface.COMMAND, Surface.NONE -> Unit
        }
    }
}

@Composable
private fun HomeSurface(
    onOpen: () -> Unit,
    onPrivate: () -> Unit,
    onSpaces: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "SUB",
            color = SubSaffron,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "ready when you are",
            color = SubTextPrimary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "tap the surface above",
            color = SubTextSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TinyPill("open", onOpen)
            TinyPill("private", onPrivate)
            TinyPill("spaces", onSpaces)
        }
    }
}

@Composable
private fun CommandPortal(
    state: BrowserState,
    textState: TextFieldState,
    expanded: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    val title = when {
        expanded -> "Search or enter address"
        state.loading -> "Loading ${state.progress}%"
        state.title != "New Tab" -> state.title
        else -> "Search or enter address"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(SubSurface.copy(alpha = 0.94f))
                .border(
                    1.dp,
                    if (expanded) SubSaffron.copy(alpha = 0.50f) else SubSurfaceElevated,
                    RoundedCornerShape(15.dp),
                )
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.secureConnection) "●" else "○",
                color = if (state.secureConnection) SubSaffron else SubTextSecondary,
                fontSize = 9.sp,
            )
            Spacer(Modifier.width(7.dp))

            if (expanded) {
                BasicTextField(
                    state = textState,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = SubTextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(SubSaffron),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    onKeyboardAction = { onSubmit() },
                    decorator = { inner ->
                        if (textState.text.isEmpty()) {
                            Text(title, color = SubTextSecondary, maxLines = 1)
                        }
                        inner()
                    },
                )
                Text(
                    "×",
                    color = SubTextSecondary,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .clickable(onClick = onClose),
                )
            } else {
                Text(
                    title,
                    color = if (state.title != "New Tab") SubTextPrimary else SubTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${state.session.tabs.size}",
                    color = SubTextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun MinimalDock(
    state: BrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onSpaces: () -> Unit,
    onTools: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton("‹", onBack, state.canGoBack)
        Spacer(Modifier.width(5.dp))
        DockButton(if (state.loading) "×" else "↻", onReload, true)
        Spacer(Modifier.width(5.dp))
        DockButton("›", onForward, state.canGoForward)
        Spacer(Modifier.width(5.dp))
        DockButton("${state.session.tabs.size}", onSpaces, true, emphasized = true)
        Spacer(Modifier.width(5.dp))
        DockButton("⋯", onTools, true)
    }
}

@Composable
private fun DockButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    emphasized: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(31.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) SubSaffron.copy(alpha = 0.10f)
                else SubSurface.copy(alpha = 0.88f)
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
            fontSize = if (label.length > 1) 9.sp else 15.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SpacesSurface(
    state: BrowserState,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onPrivate: () -> Unit,
) {
    FullSurface {
        SurfaceHeader(
            eyebrow = "SPACES",
            title = "${state.session.tabs.size} page${if (state.session.tabs.size == 1) "" else "s"}",
            onDismiss = onDismiss,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(state.session.tabs, key = { it.id }) { tab ->
                CompactPageRow(
                    number = tab.id,
                    title = tab.title.ifBlank { "New Tab" },
                    url = tab.url,
                    private = tab.isPrivate,
                    active = tab.id == state.session.activeTabId,
                    onOpen = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TinyPill("new", onNew, Modifier.weight(1f))
                    TinyPill("private", onPrivate, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompactPageRow(
    number: Long,
    title: String,
    url: String,
    private: Boolean,
    active: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) SubSurfaceElevated else SubSurface)
            .border(
                1.dp,
                if (active) SubSaffron.copy(alpha = 0.48f) else SubSurfaceElevated,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (private) "P" else "$number",
            color = if (active) SubSaffron else SubTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = SubTextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                url,
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
                .clickable(onClick = onClose),
        )
    }
}

@Composable
private fun ToolsSurface(
    onDismiss: () -> Unit,
    onSpaces: () -> Unit,
    onPrivacy: () -> Unit,
    onSettings: () -> Unit,
) {
    FullSurface {
        SurfaceHeader("TOOLS", "browser controls", onDismiss)

        ToolGroup("PAGE") {
            ToolPill("Find in page", "find")
            ToolPill("Reader view", "reader")
            ToolPill("Translate", "translate")
            ToolPill("Share", "share")
            ToolPill("Save page", "save")
            ToolPill("Download", "download")
        }

        ToolGroup("VIEW") {
            ToolPill("Zoom out", "−")
            ToolPill("Zoom in", "+")
            ToolPill("Desktop site", "desktop")
            ToolPill("Fullscreen", "full")
        }

        ToolGroup("BROWSER") {
            ToolPill("History", "history")
            ToolPill("Bookmarks", "bookmarks")
            ToolPill("Downloads", "downloads")
            ToolPill("Spaces", "spaces", onSpaces)
            ToolPill("Privacy", "privacy", onPrivacy)
            ToolPill("Settings", "settings", onSettings)
        }

        Text(
            "Controls are staged here first; behavior will be wired into the browser core.",
            color = SubTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PrivacySurface(onDismiss: () -> Unit) {
    FullSurface {
        SurfaceHeader("PRIVACY", "protection surface", onDismiss)

        ToolGroup("SITE") {
            ToolPill("Connection secure", "secure")
            ToolPill("Site permissions", "permissions")
            ToolPill("Cookies", "cookies")
            ToolPill("JavaScript", "js")
        }

        ToolGroup("PROTECTION") {
            ToolPill("Safe Browsing", "on")
            ToolPill("Block trackers", "soon")
            ToolPill("Block ads", "soon")
            ToolPill("Anti-fingerprinting", "soon")
            ToolPill("Private page", "new")
        }

        Text(
            "Privacy controls are UI placeholders until the policy engine is implemented.",
            color = SubTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SettingsSurface(onDismiss: () -> Unit) {
    FullSurface {
        SurfaceHeader("SETTINGS", "browser preferences", onDismiss)

        ToolGroup("GENERAL") {
            ToolPill("Search engine", "choose")
            ToolPill("Home surface", "choose")
            ToolPill("Appearance", "dark")
            ToolPill("Language", "auto")
        }

        ToolGroup("BEHAVIOR") {
            ToolPill("Open links", "tab")
            ToolPill("Downloads", "ask")
            ToolPill("Autoplay", "block")
            ToolPill("Popups", "block")
            ToolPill("Desktop site", "off")
        }

        ToolGroup("DATA") {
            ToolPill("Clear browsing data", "clear")
            ToolPill("Site data", "manage")
            ToolPill("Permissions", "manage")
        }

        Text(
            "Settings are staged now so every surface has a defined home before wiring behavior.",
            color = SubTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FullSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack.copy(alpha = 0.985f))
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            content()
        }
    }
}

@Composable
private fun SurfaceHeader(
    eyebrow: String,
    title: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                eyebrow,
                color = SubSaffron,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                color = SubTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "×",
            color = SubTextSecondary,
            fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onDismiss),
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ToolGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        title,
        color = SubTextSecondary,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.8.sp,
        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp),
    )
    content()
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun ToolPill(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(SubSurface.copy(alpha = 0.78f))
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(9.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = SubTextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = SubTextSecondary,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun TinyPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(SubSurface)
            .border(1.dp, SubSurfaceElevated, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = SubTextPrimary, fontSize = 10.sp)
    }
}

@Composable
private fun CrashSurface(onRecover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "page engine stopped",
            color = SubTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "the browsing space is still intact",
            color = SubTextSecondary,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(10.dp))
        TinyPill("restart page", onRecover)
    }
}
