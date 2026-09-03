package com.subbrowser.browser.session

data class SessionState(
    val tabs: List<TabState> = listOf(TabState(id = 1L)),
    val activeTabId: Long = 1L,
)
