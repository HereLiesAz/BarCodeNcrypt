package com.hereliesaz.barcodencrypt.di

import android.content.Context
import android.content.SharedPreferences
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.BarcodeDao
import com.hereliesaz.barcodencrypt.data.ContactDao
import com.hereliesaz.barcodencrypt.data.EncryptedScriptLogDao
import com.hereliesaz.barcodencrypt.data.OpenedMessageDao
import com.hereliesaz.barcodencrypt.util.AuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing Database-related dependencies.
 *
 * This module manages the lifecycle of the [AppDatabase] and its Data Access Objects (DAOs).
 * It also provides the [AuthManager], which is essential for unlocking the encrypted database.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the singleton instance of the encrypted [AppDatabase].
     *
     * **Crucial Dependency:**
     * Accessing the database requires the user's password, which is managed by [AuthManager].
     * The [AuthManager.getPassword] method is called here to retrieve the key for SQLCipher.
     *
     * @param context Application context.
     * @param authManager The manager responsible for retrieving the database encryption key.
     * @return The configured Room database instance.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context, authManager: AuthManager): AppDatabase {
        return AppDatabase.getDatabase(context, authManager.getPassword())
    }

    /**
     * Provides the [ContactDao] for accessing Contact data.
     */
    @Provides
    fun provideContactDao(appDatabase: AppDatabase): ContactDao {
        return appDatabase.contactDao()
    }

    /**
     * Provides the [BarcodeDao] for accessing Barcode keys.
     */
    @Provides
    fun provideBarcodeDao(appDatabase: AppDatabase): BarcodeDao {
        return appDatabase.barcodeDao()
    }

    /**
     * Provides the [OpenedMessageDao] for tracking message usage (open counts).
     */
    @Provides
    fun provideOpenedMessageDao(appDatabase: AppDatabase): OpenedMessageDao {
        return appDatabase.openedMessageDao()
    }

    /**
     * Provides the [EncryptedScriptLogDao] for managing cryptographic state logs.
     */
    @Provides
    fun provideEncryptedScriptLogDao(appDatabase: AppDatabase): EncryptedScriptLogDao {
        return appDatabase.encryptedScriptLogDao()
    }

    /**
     * Provides the [AuthManager] singleton.
     *
     * This manager handles authentication and key retrieval. It requires shared preferences
     * to store the encrypted key blobs.
     *
     * @param context Application context.
     * @param sharedPreferences The specific shared preferences file for auth storage.
     */
    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext context: Context, sharedPreferences: SharedPreferences): AuthManager {
        return AuthManager(context, sharedPreferences)
    }

    /**
     * Provides the SharedPreferences instance used by AuthManager and other components.
     *
     * This separates the preference file configuration from the consumers.
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("barcodencrypt_prefs", Context.MODE_PRIVATE)
    }
}
