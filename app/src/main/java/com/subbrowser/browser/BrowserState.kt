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
