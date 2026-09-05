package com.subbrowser.browser.model

import com.subbrowser.browser.session.SessionState

data class BrowserState(
    val session: SessionState = SessionState(),
    val url: String = "about:blank",
    val title: String = "New Tab",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val rendererCrashed: Boolean = false,
    val secureConnection: Boolean = false,
    val isIncognito: Boolean = false,
    val searchEngine: String = "Google",
    val showTabSwitcher: Boolean = false,
    val showHistorySheet: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
) {
    val activeTab get() = session.tabs.firstOrNull { it.id == session.activeTabId }
}
