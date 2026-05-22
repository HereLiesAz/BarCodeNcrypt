package com.hereliesaz.barcodencrypt.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hereliesaz.barcodencrypt.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    val credentialManager = remember { CredentialManager.create(context) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val signInError by onboardingViewModel.signInError.collectAsState(initial = false)
    val noCredentialsFound by onboardingViewModel.noCredentialsFound.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        onboardingViewModel.signInRequest.collect { request ->
            coroutineScope.launch {
                try {
                    val result = credentialManager.getCredential(context, request)
                    onboardingViewModel.handleSignInResult(result)
                } catch (e: NoCredentialException) {
                    onboardingViewModel.onNoCredentialsFound()
                    Toast.makeText(context, "No Google accounts found. Please set a password.", Toast.LENGTH_LONG).show()
                } catch (e: GetCredentialException) {
                    onboardingViewModel.onSignInError()
                    Toast.makeText(context, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        onboardingViewModel.signInResult.collect { signedIn ->
            if (signedIn) {
                navController.navigate(Screen.Main.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                onboardingViewModel.onSetPasswordClicked(password)
                showPasswordDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Barcodencrypt", color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onboardingViewModel.onSignInWithGoogleClicked() },
            enabled = !noCredentialsFound
        ) {
            Text(if (signInError) "Retry Sign-in" else "Sign in with Google")
        }

        if (noCredentialsFound) {
            Text(
                "No Google accounts found. Please set a password to continue.",
                modifier = Modifier.padding(top = 8.dp),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showPasswordDialog = true }) {
            Text("Enter Password")
        }
    }
}