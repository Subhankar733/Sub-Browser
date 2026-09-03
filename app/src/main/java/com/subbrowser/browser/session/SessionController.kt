package com.subbrowser.browser.session

import android.os.Bundle

class SessionController {
    companion object {
        private const val KEY_TAB_IDS = "tab_ids"
        private const val KEY_TAB_URLS = "tab_urls"
        private const val KEY_TAB_TITLES = "tab_titles"
        private const val KEY_TAB_PRIVATE = "tab_private"
        private const val KEY_ACTIVE_ID = "active_id"
        private const val KEY_NEXT_ID = "next_id"
    }

    var state: SessionState = SessionState()
        private set

    private var nextId = 2L
    private var observer: ((SessionState) -> Unit)? = null

    fun observe(observer: (SessionState) -> Unit) {
        this.observer = observer
        observer(state)
    }

    fun clearObserver() {
        observer = null
    }

    fun newTab(isPrivate: Boolean = false): Long {
        val id = nextId++
        state = state.copy(
            tabs = state.tabs + TabState(id = id, isPrivate = isPrivate),
            activeTabId = id,
        )
        publish()
        return id
    }

    fun selectTab(id: Long) {
        if (state.tabs.any { it.id == id } && id != state.activeTabId) {
            state = state.copy(activeTabId = id)
            publish()
        }
    }

    fun closeTab(id: Long): Long? {
        if (state.tabs.size == 1 || state.tabs.none { it.id == id }) return null
        val wasActive = state.activeTabId == id
        val removedIndex = state.tabs.indexOfFirst { it.id == id }
        val remaining = state.tabs.filterNot { it.id == id }
        val nextActive = if (wasActive) {
            remaining.getOrNull((removedIndex - 1).coerceAtLeast(0))?.id
                ?: remaining.first().id
        } else {
            state.activeTabId
        }
        state = state.copy(tabs = remaining, activeTabId = nextActive)
        publish()
        return nextActive
    }

    fun updateTab(
        id: Long,
        url: String? = null,
        title: String? = null,
        loading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null,
    ) {
        val current = state.tabs.firstOrNull { it.id == id } ?: return
        val updated = current.copy(
            url = url ?: current.url,
            title = title ?: current.title,
            loading = loading ?: current.loading,
            progress = progress ?: current.progress,
            canGoBack = canGoBack ?: current.canGoBack,
            canGoForward = canGoForward ?: current.canGoForward,
        )
        if (updated == current) return
        state = state.copy(tabs = state.tabs.map { if (it.id == id) updated else it })
        publish()
    }

    fun saveMetadata(outState: Bundle) {
        outState.putLongArray(KEY_TAB_IDS, state.tabs.map { it.id }.toLongArray())
        outState.putStringArrayList(KEY_TAB_URLS, ArrayList(state.tabs.map { it.url }))
        outState.putStringArrayList(KEY_TAB_TITLES, ArrayList(state.tabs.map { it.title }))
        outState.putBooleanArray(KEY_TAB_PRIVATE, state.tabs.map { it.isPrivate }.toBooleanArray())
        outState.putLong(KEY_ACTIVE_ID, state.activeTabId)
        outState.putLong(KEY_NEXT_ID, nextId)
    }

    fun restoreMetadata(bundle: Bundle?) {
        val ids = bundle?.getLongArray(KEY_TAB_IDS) ?: return
        if (ids.isEmpty()) return
        val urls = bundle.getStringArrayList(KEY_TAB_URLS).orEmpty()
        val titles = bundle.getStringArrayList(KEY_TAB_TITLES).orEmpty()
        val privateFlags = bundle.getBooleanArray(KEY_TAB_PRIVATE)
        val restoredTabs = ids.mapIndexed { index, id ->
            TabState(
                id = id,
                url = urls.getOrNull(index) ?: "about:blank",
                title = titles.getOrNull(index) ?: "New Tab",
                isPrivate = privateFlags?.getOrNull(index) ?: false,
            )
        }
        val requestedActive = bundle.getLong(KEY_ACTIVE_ID, restoredTabs.first().id)
        state = SessionState(
            tabs = restoredTabs,
            activeTabId = restoredTabs.firstOrNull { it.id == requestedActive }?.id ?: restoredTabs.first().id,
        )
        nextId = maxOf(bundle.getLong(KEY_NEXT_ID, 1L), (ids.maxOrNull() ?: 0L) + 1L)
        publish()
    }

    private fun publish() {
        observer?.invoke(state)
    }
}
