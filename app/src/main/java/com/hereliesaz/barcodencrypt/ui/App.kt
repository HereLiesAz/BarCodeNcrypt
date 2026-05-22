package com.hereliesaz.barcodencrypt.ui

import androidx.compose.runtime.Composable

/**
 * Top-level Composable mounted by [com.hereliesaz.barcodencrypt.MainActivity].
 *
 * The real screen graph lives in [AppNavigation]. Plan 4 will replace this thin
 * wrapper with an `AzHostActivityLayout`-based shell.
 */
@Composable
fun App() {
    AppNavigation()
}
