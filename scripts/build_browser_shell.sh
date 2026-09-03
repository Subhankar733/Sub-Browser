#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p \
  app/src/main/java/com/subbrowser/browser \
  app/src/main/java/com/subbrowser/ui/browser

cat > app/src/main/java/com/subbrowser/browser/BrowserState.kt <<'EOF'
package com.subbrowser.browser

import androidx.compose.runtime.Immutable

@Immutable
data class BrowserState(
    val url: String = "about:blank",
    val title: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0
)
EOF

cat > app/src/main/java/com/subbrowser/browser/BrowserController.kt <<'EOF'
package com.subbrowser.browser

import android.webkit.WebView
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

class BrowserController {
    private var webView: WebView? = null

    val state: MutableState<BrowserState> = mutableStateOf(BrowserState())

    fun attach(view: WebView) {
        webView = view
        sync()
    }

    fun detach(view: WebView) {
        if (webView === view) {
            webView = null
        }
    }

    fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return

        val target = when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.contains("://") -> value
            value.contains(" ") -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(value, "UTF-8")}"
            value.contains(".") -> "https://$value"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }

        webView?.loadUrl(target)
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

    fun sync() {
        webView?.let { view ->
            state.value = state.value.copy(
                url = view.url ?: "about:blank",
                title = view.title.orEmpty(),
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward()
            )
        }
    }

    fun updateLoading(progress: Int) {
        state.value = state.value.copy(
            isLoading = progress < 100,
            progress = progress
        )
    }
}
EOF

cat > app/src/main/java/com/subbrowser/browser/BrowserWebView.kt <<'EOF'
package com.subbrowser.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
fun createBrowserWebView(
    webView: WebView,
    controller: BrowserController
): WebView {
    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        builtInZoomControls = false
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(false)
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = false

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?
        ) {
            controller.sync()
        }

        override fun onPageFinished(
            view: WebView,
            url: String
        ) {
            controller.sync()
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(
            view: WebView,
            newProgress: Int
        ) {
            controller.updateLoading(newProgress)
            controller.sync()
        }

        override fun onReceivedTitle(
            view: WebView,
            title: String
        ) {
            controller.sync()
        }
    }

    controller.attach(webView)

    if (webView.url == null) {
        webView.loadUrl("https://www.google.com")
    }

    return webView
}
EOF

cat > app/src/main/java/com/subbrowser/ui/browser/SearchSurface.kt <<'EOF'
package com.subbrowser.ui.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subbrowser.ui.theme.SubSaffron

@Composable
fun SearchSurface(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit
) {
    val state = rememberTextFieldState(value)

    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("Search or enter address")
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SubSaffron,
            cursorColor = SubSaffron
        )
    )
}
EOF

cat > app/src/main/java/com/subbrowser/ui/browser/BrowserWorkspace.kt <<'EOF'
package com.subbrowser.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.subbrowser.browser.BrowserController
import com.subbrowser.browser.createBrowserWebView
import com.subbrowser.ui.theme.SubBlack
import com.subbrowser.ui.theme.SubSaffron
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BrowserWorkspace(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { BrowserController() }
    val browserState by controller.state
    var command by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SubBlack)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    createBrowserWebView(this, controller)
                }
            },
            update = { view ->
                if (view.parent == null) {
                    controller.attach(view)
                }
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE6000000))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = if (command.isEmpty()) browserState.url else command,
                onValueChange = { command = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White
                ),
                cursorBrush = SolidColor(SubSaffron),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        controller.navigate(command)
                        command = ""
                    }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (command.isEmpty() && browserState.url == "about:blank") {
                            Text(
                                text = "Search or enter address",
                                color = Color(0xFFAAAAAA)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            IconButton(
                onClick = {
                    if (browserState.isLoading) {
                        controller.stop()
                    } else {
                        controller.reload()
                    }
                },
                modifier = Modifier.size(42.dp)
            ) {
                Text(
                    text = if (browserState.isLoading) "×" else "↻",
                    color = SubSaffron
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xE6000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = controller::goBack,
                enabled = browserState.canGoBack
            ) {
                Text("‹", color = Color.White)
            }

            IconButton(
                onClick = controller::goForward,
                enabled = browserState.canGoForward
            ) {
                Text("›", color = Color.White)
            }

            IconButton(
                onClick = {
                    command = ""
                    controller.reload()
                }
            ) {
                Text("↻", color = Color.White)
            }
        }

        if (browserState.isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(browserState.progress / 100f)
                        .background(SubSaffron)
                        .size(2.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.sync()
        }
    }
}
EOF


chmod +x scripts/build_browser_shell.sh

echo "Browser shell generator created."
echo "Files generated:"
find app/src/main/java/com/subbrowser/browser app/src/main/java/com/subbrowser/ui/browser \
  -maxdepth 1 -type f -print | sort
