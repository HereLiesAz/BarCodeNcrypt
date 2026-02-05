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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
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

/**
 * Manages Authentication and Secure Storage of sensitive credentials.
 *
 * This class handles two distinct but related security tasks:
 * 1. **User Authentication:** Handles Google Sign-In via Credential Manager and Firebase Auth.
 * 2. **Local Secret Storage:** Manages the encryption and storage of the local database password
 *    using the Android Keystore system.
 *
 * **Security Architecture:**
 * - The app uses SQLCipher to encrypt the database.
 * - The password for SQLCipher is generated randomly or set by the user.
 * - This password is *never* stored in plaintext.
 * - It is encrypted using a [SecretKey] backed by the Android Keystore ([ANDROID_KEYSTORE]).
 * - The encrypted password and its IV are stored in SharedPreferences.
 *
 * @param context Application context.
 * @param sharedPreferences Preferences to store encrypted blobs.
 */
class AuthManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    private val TAG = "AuthManager"

    // Initialize Credential Manager for Google Sign-In
    private val credentialManager = CredentialManager.create(context)
    // Initialize Firebase Auth
    private val auth: FirebaseAuth = Firebase.auth
    // Lazy load Web Client ID from resources
    private val webClientId by lazy {
        context.getString(R.string.web_client_id)
    }

    // Mutable state flow to track the current Firebase user
    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    /**
     * Flow of the current authenticated user. Emits null if logged out.
     */
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    // Listener for Firebase Auth state changes
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        // Update the flow when auth state changes
        _user.value = firebaseAuth.currentUser
        if (LogConfig.AUTH_FLOW) {
            val uid = firebaseAuth.currentUser?.uid
            Log.d(TAG, "AuthStateListener fired. User is now: ${uid ?: "null"}")
        }
    }

    init {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "init: AuthManager instance initialized. Current Firebase user: ${auth.currentUser?.uid ?: "null"}")
        // Register the auth state listener
        auth.addAuthStateListener(authStateListener)
    }

    // Load the Android Keystore instance
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Checks if a Google User is currently signed in via Firebase.
     */
    fun isGoogleUserSignedIn(): Boolean {
        // Check if currentUser is not null
        val isSignedIn = auth.currentUser != null
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "isGoogleUserSignedIn() check: $isSignedIn")
        return isSignedIn
    }

    /**
     * Retrieves the SecretKey from the Keystore, generating it if necessary.
     */
    private fun getSecretKey(): SecretKey {
        // Try to get the existing key entry from the KeyStore
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            // If not found, generate a new one
            ?: generateSecretKey()
    }

    private fun generateSecretKey(): SecretKey {
        // Get KeyGenerator for AES algorithm in AndroidKeyStore provider
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        // Configure the key specifications
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM) // Use GCM mode
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE) // No padding needed for GCM
            .setKeySize(256) // Use 256-bit key
            .build()
        // Initialize the generator with the specs
        keyGenerator.init(keyGenParameterSpec)
        // Generate and return the key
        return keyGenerator.generateKey()
    }

    /**
     * Checks if a local password has been set.
     */
    fun hasLocalPassword(): Boolean {
        // Check if the encrypted password exists in SharedPreferences
        val hasPass = sharedPreferences.contains(ENCRYPTED_PASSWORD_KEY)
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "hasLocalPassword() check: $hasPass")
        return hasPass
    }

    /**
     * Builds the Google Sign-In request.
     */
    fun getGoogleSignInRequest(): GetCredentialRequest {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "getGoogleSignInRequest: Building Google Sign-In request.")
        // Generate a random nonce for security
        val nonce = generateNonce()
        // Build the Google ID option with required parameters
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Allow any account
            .setServerClientId(webClientId) // Server Client ID for backend verification
            .setNonce(nonce) // Nonce to prevent replay attacks
            .build()
        // Build and return the full credential request
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    /**
     * Handles the sign-in result credential.
     */
    suspend fun handleSignInResult(result: GetCredentialResponse): GoogleIdTokenCredential? {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "handleSignInResult: Received credential response from Google.")
        var googleIdTokenCredential: GoogleIdTokenCredential? = null

        // Determine the type of credential returned
        when (val credential = result.credential) {
            is GoogleIdTokenCredential -> {
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Credential is of type GoogleIdTokenCredential.")
                googleIdTokenCredential = credential
            }
            is CustomCredential -> {
                // Fallback for some devices/versions where it returns CustomCredential
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Credential is of type CustomCredential. Type: ${credential.type}")
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // Attempt to parse GoogleIdTokenCredential from custom data
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
            // Get the ID token from the credential
            val googleIdToken = googleIdTokenCredential.idToken
            // Create a Firebase credential using the ID token
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            return try {
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Attempting to sign in with Firebase...")
                // Sign in to Firebase with the credential
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                if (authResult.user != null) {
                    if (LogConfig.AUTH_FLOW) Log.d(TAG, "Firebase sign-in SUCCESSFUL for user: ${authResult.user?.uid}")
                    // Return the credential on success
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
        // Create secure random generator
        val random = SecureRandom()
        // Generate 16 random bytes
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        // Convert to hex string
        return bytes.toHexString()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }


    /**
     * Sets and encrypts the local password.
     */
    fun setPassword(password: String) {
        // Get Cipher instance for AES/GCM/NoPadding
        val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
        // Initialize Cipher in ENCRYPT_MODE using the SecretKey from Keystore
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        // Get the initialization vector (IV) generated by the Cipher
        val iv = cipher.iv
        // Encrypt the password bytes
        val encryptedPassword = cipher.doFinal(password.toByteArray(Charset.defaultCharset()))
        // Store the encrypted password and IV in SharedPreferences (Base64 encoded)
        sharedPreferences.edit()
            .putString(ENCRYPTED_PASSWORD_KEY, Base64.encodeToString(encryptedPassword, Base64.DEFAULT))
            .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }

    /**
     * Checks the password against the stored encrypted one.
     */
    fun checkPassword(password: String): Boolean {
        // Retrieve the encrypted password from SharedPreferences
        val encryptedPasswordString = sharedPreferences.getString(ENCRYPTED_PASSWORD_KEY, null)
        // Retrieve the IV from SharedPreferences
        val ivString = sharedPreferences.getString(IV_KEY, null)

        if (encryptedPasswordString != null && ivString != null) {
            try {
                // Decode the Base64 strings to bytes
                val encryptedPassword = Base64.decode(encryptedPasswordString, Base64.DEFAULT)
                val iv = Base64.decode(ivString, Base64.DEFAULT)
                // Get Cipher instance
                val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
                // Create GCMParameterSpec with the IV
                val spec = GCMParameterSpec(128, iv) // 128 bit tag length
                // Initialize Cipher in DECRYPT_MODE using the SecretKey and IV spec
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
                // Decrypt the password
                val decryptedPassword = cipher.doFinal(encryptedPassword)
                // Compare decrypted password with input
                return password == String(decryptedPassword, Charset.defaultCharset())
            } catch (e: Exception) {
                // decryption failed (wrong key, corrupted data, or tamper detected)
                return false
            }
        }
        return false
    }

    /**
     * Logs out the user.
     */
    suspend fun logout() {
        try {
            // Clear credential state in Credential Manager
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Log error if clearing fails
        }
        // Sign out from Firebase
        auth.signOut()
        // Remove the local encrypted password and IV from SharedPreferences
        sharedPreferences.edit()
            .remove(ENCRYPTED_PASSWORD_KEY)
            .remove(IV_KEY)
            .apply()
    }

    /**
     * Decrypts and returns the password.
     *
     * **Warning:** Use with caution. Handles plaintext password.
     */
    fun getPassword(): CharSequence {
        // Retrieve the encrypted password from SharedPreferences
        val encryptedPasswordString = sharedPreferences.getString(ENCRYPTED_PASSWORD_KEY, null)
        // Retrieve the IV from SharedPreferences
        val ivString = sharedPreferences.getString(IV_KEY, null)

        if (encryptedPasswordString != null && ivString != null) {
            try {
                // Decode Base64
                val encryptedPassword = Base64.decode(encryptedPasswordString, Base64.DEFAULT)
                val iv = Base64.decode(ivString, Base64.DEFAULT)
                // Initialize Cipher
                val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
                // Decrypt
                val decryptedPassword = cipher.doFinal(encryptedPassword)
                // Return decrypted string
                return String(decryptedPassword, Charset.defaultCharset())
            } catch (e: Exception) {
                // Decryption failed
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
