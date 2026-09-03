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
