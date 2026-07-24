package com.adder.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Root composable for the Adder app.
 * Called from both Android (via setContent) and iOS (via ComposeUIViewController).
 */
@Composable
fun App() {
    MaterialTheme {
        BrowserScreen()
    }
}
