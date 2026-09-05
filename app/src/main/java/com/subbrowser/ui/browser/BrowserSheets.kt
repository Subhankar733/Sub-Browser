package com.subbrowser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subbrowser.browser.data.BookmarkItem
import com.subbrowser.browser.data.BrowserDatabase
import com.subbrowser.browser.data.HistoryItem
import com.subbrowser.browser.model.BrowserState

private val BackgroundColor = Color(0xFF0F172A)
private val SurfaceColor = Color(0xFF1E293B)
private val BorderColor = Color(0xFF334155)
private val AccentColor = Color(0xFF38BDF8)
private val SubTextPrimary = Color(0xFFF8FAFC)
private val SubTextSecondary = Color(0xFF94A3B8)

@Composable
fun TabSwitcherOverlay(
    state: BrowserState,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tabs (${state.session.tabs.size})", color = SubTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row {
                    Text("+ New", color = AccentColor, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNewTab).padding(8.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Done", color = SubTextPrimary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp))
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.session.tabs) { tab ->
                    val isSelected = tab.id == state.session.activeTabId
                    Box(
                        modifier = Modifier
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AccentColor.copy(alpha = 0.15f) else SurfaceColor)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) AccentColor else BorderColor, RoundedCornerShape(12.dp))
                            .clickable { onSelectTab(tab.id) }
                            .padding(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tab.title.ifBlank { "New Tab" }, color = SubTextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (state.session.tabs.size > 1) {
                                    Text("✕", color = SubTextSecondary, fontSize = 12.sp, modifier = Modifier.clickable { onCloseTab(tab.id) }.padding(2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(tab.url, color = SubTextSecondary, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistorySheet(
    database: BrowserDatabase,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    var historyItems by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    LaunchedEffect(Unit) { historyItems = database.getHistory() }

    Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.75f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(SurfaceColor)
                .clickable(enabled = false, onClick = {})
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Browsing History", color = SubTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (historyItems.isNotEmpty()) {
                    Text("Clear All", color = Color(0xFFF87171), fontSize = 12.sp, modifier = Modifier.clickable {
                        database.clearHistory()
                        historyItems = emptyList()
                    }.padding(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (historyItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No browsing history", color = SubTextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(historyItems) { item ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { onSelect(item.url) }.padding(vertical = 8.dp)) {
                            Text(item.title, color = SubTextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.url, color = SubTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarksSheet(
    database: BrowserDatabase,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    var bookmarks by remember { mutableStateOf<List<BookmarkItem>>(emptyList()) }
    LaunchedEffect(Unit) { bookmarks = database.getBookmarks() }

    Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.75f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(SurfaceColor)
                .clickable(enabled = false, onClick = {})
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Saved Bookmarks", color = SubTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("✕", color = SubTextSecondary, modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No bookmarks saved yet", color = SubTextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(bookmarks) { item ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(item.url) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = SubTextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.url, color = SubTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("✕", color = SubTextSecondary, fontSize = 12.sp, modifier = Modifier.clickable {
                                database.deleteBookmark(item.id)
                                bookmarks = database.getBookmarks()
                            }.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSheet(
    currentEngine: String,
    onSelectEngine: (String) -> Unit,
    onClose: () -> Unit,
) {
    val engines = listOf("Google", "DuckDuckGo", "Bing")
    Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(SurfaceColor)
                .clickable(enabled = false, onClick = {})
                .padding(16.dp)
        ) {
            Text("Browser Settings", color = SubTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Default Search Engine", color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            engines.forEach { engine ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelectEngine(engine) }.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(engine, color = SubTextPrimary, fontSize = 13.sp)
                    if (currentEngine == engine) Text("✓", color = AccentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
