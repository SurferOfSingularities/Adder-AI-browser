package com.adder.shared.ui

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Entry point for iOS — creates a UIViewController hosting the shared Compose UI.
 * Called from Swift's AppDelegate.
 */
fun MainViewController() = ComposeUIViewController { App() }
