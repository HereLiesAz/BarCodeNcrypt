package com.hereliesaz.barcodencrypt.di

import android.content.Context
import android.content.SharedPreferences
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.BarcodeDao
import com.hereliesaz.barcodencrypt.data.ContactDao
import com.hereliesaz.barcodencrypt.data.OpenedMessageDao
import com.hereliesaz.barcodencrypt.data.RatchetStateDao
import com.hereliesaz.barcodencrypt.util.AuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the encrypted [AppDatabase], its DAOs, and the
 * cross-cutting [AuthManager] used to derive the SQLCipher passphrase.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        authManager: AuthManager,
    ): AppDatabase = AppDatabase.getDatabase(context, authManager.getPassword())

    @Provides
    fun provideContactDao(appDatabase: AppDatabase): ContactDao = appDatabase.contactDao()

    @Provides
    fun provideBarcodeDao(appDatabase: AppDatabase): BarcodeDao = appDatabase.barcodeDao()

    @Provides
    fun provideOpenedMessageDao(appDatabase: AppDatabase): OpenedMessageDao =
        appDatabase.openedMessageDao()

    @Provides
    fun provideRatchetStateDao(appDatabase: AppDatabase): RatchetStateDao =
        appDatabase.ratchetStateDao()

    @Provides
    @Singleton
    fun provideAuthManager(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences,
    ): AuthManager = AuthManager(context, sharedPreferences)

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences =
        context.getSharedPreferences("barcodencrypt_prefs", Context.MODE_PRIVATE)
}
