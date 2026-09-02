package com.subbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.subbrowser.ui.browser.BrowserWorkspace
import com.subbrowser.ui.theme.SubBrowserTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SubBrowserRoot()
        }
    }
}

@androidx.compose.runtime.Composable
private fun SubBrowserRoot() {
    SubBrowserTheme {
        BrowserWorkspace()
    }
}
