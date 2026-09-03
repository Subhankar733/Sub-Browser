package com.subbrowser.browser.session

data class TabState(
    val id: Long,
    val url: String = "about:blank",
    val title: String = "New Tab",
    val isPrivate: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)
