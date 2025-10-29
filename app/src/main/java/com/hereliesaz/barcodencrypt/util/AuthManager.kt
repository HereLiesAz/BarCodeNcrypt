package com.hereliesaz.barcodencrypt.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.hereliesaz.barcodencrypt.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    private val TAG = "AuthManager"

    private val credentialManager = CredentialManager.create(context)
    private val auth: FirebaseAuth = Firebase.auth
    private val webClientId by lazy {
        context.getString(R.string.web_client_id)
    }

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _user.value = firebaseAuth.currentUser
        if (LogConfig.AUTH_FLOW) {
            val uid = firebaseAuth.currentUser?.uid
            Log.d(TAG, "AuthStateListener fired. User is now: ${uid ?: "null"}")
        }
    }

    init {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "init: AuthManager instance initialized. Current Firebase user: ${auth.currentUser?.uid ?: "null"}")
        auth.addAuthStateListener(authStateListener)
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    // NEW METHOD to check for Google Sign-In
    fun isGoogleUserSignedIn(): Boolean {
        val isSignedIn = auth.currentUser != null
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "isGoogleUserSignedIn() check: $isSignedIn")
        return isSignedIn
    }


    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateSecretKey()
    }

    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun hasLocalPassword(): Boolean {
        val hasPass = sharedPreferences.contains(ENCRYPTED_PASSWORD_KEY)
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "hasLocalPassword() check: $hasPass")
        return hasPass
    }

    fun getGoogleSignInRequest(): GetCredentialRequest {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "getGoogleSignInRequest: Building Google Sign-In request.")
        val nonce = generateNonce()
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(nonce)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    suspend fun handleSignInResult(result: GetCredentialResponse): GoogleIdTokenCredential? {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "handleSignInResult: Received credential response from Google.")
        var googleIdTokenCredential: GoogleIdTokenCredential? = null

        when (val credential = result.credential) {
            is GoogleIdTokenCredential -> {
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Credential is of type GoogleIdTokenCredential.")
                googleIdTokenCredential = credential
            }
            is CustomCredential -> {
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Credential is of type CustomCredential. Type: ${credential.type}")
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        if (LogConfig.AUTH_FLOW) Log.d(TAG, "Successfully created GoogleIdTokenCredential from CustomCredential data.")
                    } catch (e: Exception) {
                        if (LogConfig.AUTH_FLOW) Log.e(TAG, "Failed to create GoogleIdTokenCredential from CustomCredential data.", e)
                    }
                }
            }
            else -> {
                if (LogConfig.AUTH_FLOW) Log.w(TAG, "handleSignInResult: Credential response was not a recognized type. Type was: ${credential::class.java.simpleName}")
            }
        }


        if (googleIdTokenCredential != null) {
            val googleIdToken = googleIdTokenCredential.idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            return try {
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Attempting to sign in with Firebase...")
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                if (authResult.user != null) {
                    if (LogConfig.AUTH_FLOW) Log.d(TAG, "Firebase sign-in SUCCESSFUL for user: ${authResult.user?.uid}")
                    googleIdTokenCredential
                } else {
                    if (LogConfig.AUTH_FLOW) Log.e(TAG, "Firebase sign-in failed: authResult.user is null")
                    null
                }
            } catch (e: Exception) {
                if (LogConfig.AUTH_FLOW) Log.e(TAG, "Firebase sign-in FAILED with exception", e)
                null
            }
        }

        if (LogConfig.AUTH_FLOW) Log.e(TAG, "Could not obtain a valid GoogleIdTokenCredential.")
        return null
    }

    private fun generateNonce(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.toHexString()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }


    fun setPassword(password: String) {
        val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedPassword = cipher.doFinal(password.toByteArray(Charset.defaultCharset()))
        sharedPreferences.edit()
            .putString(ENCRYPTED_PASSWORD_KEY, Base64.encodeToString(encryptedPassword, Base64.DEFAULT))
            .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }

    fun checkPassword(password: String): Boolean {
        val encryptedPasswordString = sharedPreferences.getString(ENCRYPTED_PASSWORD_KEY, null)
        val ivString = sharedPreferences.getString(IV_KEY, null)
        if (encryptedPasswordString != null && ivString != null) {
            try {
                val encryptedPassword = Base64.decode(encryptedPasswordString, Base64.DEFAULT)
                val iv = Base64.decode(ivString, Base64.DEFAULT)
                val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
                val decryptedPassword = cipher.doFinal(encryptedPassword)
                return password == String(decryptedPassword, Charset.defaultCharset())
            } catch (e: Exception) {
                // decryption failed
                return false
            }
        }
        return false
    }

    suspend fun logout() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Log error
        }
        auth.signOut()
        sharedPreferences.edit()
            .remove(ENCRYPTED_PASSWORD_KEY)
            .remove(IV_KEY)
            .apply()
    }

    fun getPassword(): CharSequence {
        val encryptedPasswordString = sharedPreferences.getString(ENCRYPTED_PASSWORD_KEY, null)
        val ivString = sharedPreferences.getString(IV_KEY, null)
        if (encryptedPasswordString != null && ivString != null) {
            try {
                val encryptedPassword = Base64.decode(encryptedPasswordString, Base64.DEFAULT)
                val iv = Base64.decode(ivString, Base64.DEFAULT)
                val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
                val decryptedPassword = cipher.doFinal(encryptedPassword)
                return String(decryptedPassword, Charset.defaultCharset())
            } catch (e: Exception) {
                // decryption failed
                return ""
            }
        }
        return ""
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "password_key"
        private const val ENCRYPTED_PASSWORD_KEY = "encrypted_password"
        private const val IV_KEY = "iv"
        const val IS_GOOGLE_SIGNED_IN_KEY = "is_google_signed_in"
    }
}