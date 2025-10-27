package com.hereliesaz.barcodencrypt.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.hereliesaz.barcodencrypt.ui.Screen

@Composable
fun AppScaffold(
    navController: NavController,
    onManageContactKeys: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            NavigationRailItem(
                icon = { Icon(Icons.Default.VpnKey, contentDescription = "Manage Keys") },
                label = { Text("Keys") },
                selected = false, // This should be dynamic based on current route
                onClick = onManageContactKeys
            )
            NavigationRailItem(
                icon = { Icon(Icons.Default.Create, contentDescription = "Compose") },
                label = { Text("Compose") },
                selected = false,
                onClick = { navController.navigate(Screen.Compose.route) }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") },
                selected = false,
                onClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        content()
    }
}