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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context, authManager: AuthManager): AppDatabase {
        return AppDatabase.getDatabase(context, authManager.getPassword())
    }

    @Provides
    fun provideContactDao(appDatabase: AppDatabase): ContactDao {
        return appDatabase.contactDao()
    }

    @Provides
    fun provideBarcodeDao(appDatabase: AppDatabase): BarcodeDao {
        return appDatabase.barcodeDao()
    }

    @Provides
    fun provideOpenedMessageDao(appDatabase: AppDatabase): OpenedMessageDao {
        return appDatabase.openedMessageDao()
    }

    @Provides
    fun provideEncryptedScriptLogDao(appDatabase: AppDatabase): EncryptedScriptLogDao {
        return appDatabase.encryptedScriptLogDao()
    }

    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext context: Context, sharedPreferences: SharedPreferences): AuthManager {
        return AuthManager(context, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("barcodencrypt_prefs", Context.MODE_PRIVATE)
    }
}
