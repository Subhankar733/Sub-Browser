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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import com.subbrowser.browser.BrowserController
import com.subbrowser.browser.model.BrowserState
import com.subbrowser.browser.web.configureBrowserWebView
import com.subbrowser.ui.theme.SubBlack
import com.subbrowser.ui.theme.SubSaffron
import com.subbrowser.ui.theme.SubSurface
import com.subbrowser.ui.theme.SubTextPrimary
import com.subbrowser.ui.theme.SubTextSecondary

private val CanvasWhite = Color(0xFFF5F5F2)
private val CanvasLine = Color(0xFF252525)
private val CanvasMuted = Color(0xFF777777)

@Composable
fun BrowserWorkspace(
    controller: BrowserController = remember { BrowserController() },
) {
    var state by remember { mutableStateOf(BrowserState()) }
    var webViewEpoch by remember { mutableIntStateOf(0) }
    var commandOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val command = rememberTextFieldState()

    DisposableEffect(controller) {
        controller.observe { state = it }
        onDispose { controller.clearObserver() }
    }

    BackHandler(enabled = commandOpen || menuOpen) {
        commandOpen = false
        menuOpen = false
    }

    BackHandler(enabled = !commandOpen && !menuOpen && state.canGoBack) {
        controller.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubBlack)
    ) {
        key(webViewEpoch) {
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
        }

        if (state.url == "about:blank") {
            NewTabCanvas(
                onSearch = { commandOpen = true },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    webViewEpoch++
                },
                onTabs = { menuOpen = true },
            )
        }

        if (state.url != "about:blank") {
            PageHud(
                state = state,
                onCommand = { commandOpen = true },
                onBack = controller::goBack,
                onForward = controller::goForward,
                onReload = {
                    if (state.loading) controller.stop() else controller.reload()
                },
                onMenu = { menuOpen = true },
            )
        } else {
            MinimalHud(
                onCommand = { commandOpen = true },
                onMenu = { menuOpen = true },
            )
        }

        if (commandOpen) {
            CommandSheet(
                state = command,
                onClose = { commandOpen = false },
                onSubmit = {
                    val value = command.text.toString().trim()
                    if (value.isNotEmpty()) {
                        controller.navigate(value)
                        command.edit { replace(0, length, "") }
                        commandOpen = false
                    }
                },
            )
        }

        if (menuOpen) {
            MenuSheet(
                onClose = { menuOpen = false },
                onNew = {
                    controller.newTab()
                    menuOpen = false
                    webViewEpoch++
                },
                onPrivate = {
                    controller.newTab(isPrivate = true)
                    menuOpen = false
                    webViewEpoch++
                },
                onReload = {
                    controller.reload()
                    menuOpen = false
                },
            )
        }
    }
}

@Composable
private fun NewTabCanvas(
    onSearch: () -> Unit,
    onPrivate: () -> Unit,
    onTabs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            "SUB",
            color = SubSaffron,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Browse without the clutter.",
            color = CanvasWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(7.dp))

        Text(
            "One surface. Your pages. Nothing unnecessary.",
            color = SubTextSecondary,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(22.dp))

        CommandBar(
            hint = "Search or enter address",
            onClick = onSearch,
        )

        Spacer(Modifier.height(11.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallAction("PRIVATE", "P", onPrivate, Modifier.weight(1f))
            SmallAction("TABS", "□", onTabs, Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        Text(
            "SUB BROWSER",
            color = CanvasMuted,
            fontSize = 8.sp,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun CommandBar(
    hint: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SubSurface)
            .border(1.dp, CanvasLine, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(SubSaffron)
        )
        Spacer(Modifier.width(9.dp))
        Text(hint, color = SubTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun SmallAction(
    title: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SubSurface)
            .border(1.dp, CanvasLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(symbol, color = SubSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(7.dp))
        Text(title, color = CanvasWhite, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun MinimalHud(
    onCommand: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("S", color = SubSaffron, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f))
        HudButton("⌘", onCommand)
        Spacer(Modifier.width(7.dp))
        HudButton("⋮", onMenu)
    }
}

@Composable
private fun PageHud(
    state: BrowserState,
    onCommand: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onMenu: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudButton("‹", onBack, enabled = state.canGoBack)
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0xCC080808))
                    .border(1.dp, CanvasLine, RoundedCornerShape(17.dp))
                    .clickable(onClick = onCommand)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    state.title.ifBlank { state.url },
                    color = CanvasWhite,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(5.dp))
            HudButton("›", onForward, enabled = state.canGoForward)
            Spacer(Modifier.width(5.dp))
            HudButton(if (state.loading) "×" else "↻", onReload)
            Spacer(Modifier.width(5.dp))
            HudButton("⋮", onMenu)
        }
    }
}

@Composable
private fun HudButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xCC080808) else Color(0x66080808))
            .border(1.dp, if (enabled) CanvasLine else Color(0x33252525), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) CanvasWhite else CanvasMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun CommandSheet(
    state: TextFieldState,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB8000000))
            .clickable(onClick = onClose)
            .padding(horizontal = 14.dp, vertical = 72.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0C0C0C))
                .border(1.dp, CanvasLine, RoundedCornerShape(18.dp))
                .padding(12.dp)
                .clickable(enabled = false, onClick = {}),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("COMMAND", color = SubSaffron, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("×", color = CanvasWhite, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onClose))
            }

            Spacer(Modifier.height(10.dp))

            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                textStyle = TextStyle(color = CanvasWhite, fontSize = 12.sp),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                decorator = { inner ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SubSurface)
                            .border(1.dp, CanvasLine, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⌕", color = SubSaffron, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (state.text.isEmpty()) {
                                Text("Search, address or command", color = SubTextSecondary, fontSize = 11.sp)
                            }
                            inner()
                        }
                        Text(
                            "→",
                            color = SubSaffron,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable(onClick = onSubmit),
                        )
                    }
                },
            )

            Spacer(Modifier.height(10.dp))
            Text("QUICK COMMANDS", color = CanvasMuted, fontSize = 8.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(7.dp))
            QuickLine("SEARCH WEB")
            QuickLine("OPEN ADDRESS")
            QuickLine("NEW PRIVATE SPACE")
            QuickLine("TABS")
        }
    }
}

@Composable
private fun QuickLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(SubSaffron))
        Spacer(Modifier.width(9.dp))
        Text(text, color = CanvasWhite, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun MenuSheet(
    onClose: () -> Unit,
    onNew: () -> Unit,
    onPrivate: () -> Unit,
    onReload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB8000000))
            .clickable(onClick = onClose)
            .padding(horizontal = 14.dp, vertical = 70.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0C0C0C))
                .border(1.dp, CanvasLine, RoundedCornerShape(18.dp))
                .padding(10.dp)
                .clickable(enabled = false, onClick = {}),
        ) {
            Text("SUB", color = SubSaffron, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(7.dp))
            MenuItem("NEW TAB", onNew)
            MenuItem("PRIVATE SPACE", onPrivate)
            MenuItem("RELOAD", onReload)
            MenuItem("HISTORY", {})
            MenuItem("BOOKMARKS", {})
            MenuItem("DOWNLOADS", {})
            MenuItem("PRIVACY", {})
            MenuItem("SETTINGS", {})
            Spacer(Modifier.height(4.dp))
            Text("v0.1 • browser workspace", color = CanvasMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(SubSaffron))
        Spacer(Modifier.width(9.dp))
        Text(text, color = CanvasWhite, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}
