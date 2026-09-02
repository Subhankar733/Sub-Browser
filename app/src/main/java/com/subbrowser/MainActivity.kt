package com.subbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subbrowser.ui.theme.SubBlack
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

@Composable
private fun SubBrowserRoot() {
    SubBrowserTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SubBlack
        ) {
        }
    }
}
