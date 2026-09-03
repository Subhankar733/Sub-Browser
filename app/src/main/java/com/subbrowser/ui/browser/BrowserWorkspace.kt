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
                    commandState.edit { replace(0, length, "") }
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
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SUB",
            color = SubSaffron,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ready when you are",
            color = SubTextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "tap the surface above",
            color = SubTextSecondary.copy(alpha = 0.6f),
            fontSize = 11.sp,
        )
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
        state.loading -> "${state.progress}%"
        state.title != "New Tab" -> state.title
        else -> "Search or enter address"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SubSurface.copy(alpha = 0.92f))
                .border(
                    1.dp,
                    if (expanded) SubSaffron.copy(alpha = 0.5f) else SubSurfaceElevated,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 11.dp, vertical = 7.dp)
                .clickable(onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.secureConnection) "•" else "○",
                color = if (state.secureConnection) SubSaffron else SubTextSecondary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(7.dp))
            if (expanded) {
                BasicTextField(
                    state = textState,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = SubTextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(SubSaffron),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    onKeyboardAction = { onSubmit() },
                    decorator = { inner ->
                        if (textState.text.isEmpty()) {
                            Text(label, color = SubTextSecondary, fontSize = 14.sp, maxLines = 1)
                        }
                        inner()
                    },
                )
                TextButton(
                    onClick = onCollapse,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 3.dp, vertical = 0.dp
                    ),
                ) {
                    Text("×", color = SubTextSecondary, fontSize = 17.sp)
                }
            } else {
                Text(
                    text = label,
                    color = if (state.title != "New Tab") SubTextPrimary else SubTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.session.tabs.size}",
                    color = SubTextSecondary,
                    fontSize = 10.sp,
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
        modifier = modifier.padding(bottom = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactAction("‹", onBack, state.canGoBack)
        Spacer(Modifier.width(5.dp))
        CompactAction(if (state.loading) "×" else "↻", onReload, true)
        Spacer(Modifier.width(5.dp))
        CompactAction("›", onForward, state.canGoForward)
        Spacer(Modifier.width(5.dp))
        CompactAction("${state.session.tabs.size}", onWorkspace, true, emphasized = true)
    }
}

@Composable
private fun CompactAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    emphasized: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) SubSaffron.copy(alpha = 0.09f)
                else SubSurface.copy(alpha = 0.9f)
            )
            .border(
                1.dp,
                if (emphasized) SubSaffron.copy(alpha = 0.4f) else SubSurfaceElevated,
                CircleShape,
            )
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (emphasized) SubSaffron else SubTextPrimary,
            fontSize = if (label.length > 1) 10.sp else 16.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
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
