package com.subbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.subbrowser.browser.BrowserController
import com.subbrowser.ui.browser.BrowserWorkspace
import com.subbrowser.ui.theme.SubBrowserTheme

class MainActivity : ComponentActivity() {
    private val browserController = BrowserController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        browserController.restoreInstanceState(savedInstanceState)

        setContent {
            SubBrowserTheme {
                BrowserWorkspace(controller = browserController)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browserController.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}
