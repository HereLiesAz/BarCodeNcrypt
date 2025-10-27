package com.hereliesaz.barcodencrypt.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffold
import com.hereliesaz.barcodencrypt.util.ScannerManager
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Onboarding : Screen("onboarding")
    object Compose : Screen("compose")
    object Settings : Screen("settings")
    object Scanner : Screen("scanner")
    object ContactDetail : Screen("contact/{contactLookupKey}/{contactName}") {
        fun createRoute(contactLookupKey: String, contactName: String): String {
            val encodedName = Uri.encode(contactName)
            return "contact/$contactLookupKey/$encodedName"
        }
    }
}

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()
    val isLoggedIn by mainViewModel.isLoggedIn.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        ScannerManager.requests.onEach {
            navController.navigate(Screen.Scanner.route)
        }.launchIn(this)
    }

    DisposableEffect(navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.getLiveData<String>("scanned_barcode")
                    ?.observe(lifecycleOwner) { barcode ->
                        ScannerManager.onScanResult(barcode)
                        navController.currentBackStackEntry?.savedStateHandle?.remove<String>("scanned_barcode")
                    }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(navController = navController, startDestination = if (isLoggedIn == true) Screen.Main.route else Screen.Onboarding.route) {
        composable(Screen.Main.route) {
            val context = LocalContext.current
            val contactPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { contactUri ->
                        val projection = arrayOf(
                            ContactsContract.Contacts.LOOKUP_KEY,
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                        )
                        context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                                navController.navigate(Screen.ContactDetail.createRoute(lookupKey, displayName))
                            }
                        }
                    }
                }
            }

            val contactsPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    if (isGranted) {
                        contactPickerLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
                    }
                }
            )
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { /* handle result */ }
            )

            val onManageContactKeys = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    contactPickerLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
                } else {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }

            AppScaffold(navController = navController, onManageContactKeys = onManageContactKeys) {
                MainScreen(
                    viewModel = mainViewModel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onManageContactKeys = onManageContactKeys,
                    onNavigateToCompose = { navController.navigate(Screen.Compose.route) }
                )
            }
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(navController = navController)
        }
        composable(Screen.Compose.route) {
            ComposeScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(
                navArgument("contactLookupKey") { type = NavType.StringType },
                navArgument("contactName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactLookupKey = backStackEntry.arguments?.getString("contactLookupKey")
            val contactName = backStackEntry.arguments?.getString("contactName")?.let { Uri.decode(it) }
            ContactDetailScreen(
                navController = navController,
                contactLookupKey = contactLookupKey,
                contactName = contactName
            )
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(navController = navController) { barcode ->
                navController.previousBackStackEntry?.savedStateHandle?.set("scanned_barcode", barcode)
                navController.popBackStack()
            }
        }
    }
}