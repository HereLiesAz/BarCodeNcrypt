package com.hereliesaz.barcodencrypt.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.viewmodel.PasswordViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PasswordActivity : ComponentActivity() {

    private val viewModel: PasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                PasswordScreen(viewModel = viewModel) { password ->
                    // This is the point of initialization for the encrypted database
                    (application as com.hereliesaz.barcodencrypt.BarcodeApplication).database =
                        com.hereliesaz.barcodencrypt.data.AppDatabase.getDatabase(this, password)

                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}

@Composable
fun PasswordScreen(viewModel: PasswordViewModel, onPasswordSuccess: (String) -> Unit) {
    val passwordState by viewModel.passwordState.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(passwordState) {
        if (passwordState is PasswordViewModel.PasswordState.Success) {
            onPasswordSuccess(password)
        }
        showError = passwordState is PasswordViewModel.PasswordState.Invalid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (passwordState) {
            is PasswordViewModel.PasswordState.Loading -> CircularProgressIndicator()
            is PasswordViewModel.PasswordState.Unset -> {
                Text("Create a Master Password")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = showError
                )
                if (showError) {
                    Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (password == confirmPassword && password.isNotEmpty()) {
                            viewModel.createPassword(password)
                        } else {
                            showError = true
                        }
                    }
                ) {
                    Text("Create Password")
                }
            }
            is PasswordViewModel.PasswordState.Set, is PasswordViewModel.PasswordState.Invalid -> {
                Text("Enter Master Password")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = showError
                )
                if (showError) {
                    Text("Incorrect password", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.submitPassword(password) }) {
                    Text("Unlock")
                }
            }
            else -> {
                // Idle or Success, do nothing here as we are navigating away
            }
        }
    }
}