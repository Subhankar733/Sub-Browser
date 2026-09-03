package com.subbrowser.browser.model

data class BrowserState(
    val url: String = "about:blank",
    val title: String = "New Tab",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secureConnection: Boolean = false,
    val rendererCrashed: Boolean = false,
    val errorMessage: String? = null,
)
