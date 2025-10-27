package com.hereliesaz.barcodencrypt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The Scribe's archive. The Room database.
 * It no longer knows of 'Contacts', only of the sigils ('Barcodes') themselves.
 * It is a singleton, a lonely and singular vault of secrets.
 */
@Database(entities = [Contact::class, Barcode::class, RevokedMessage::class], version = 7, exportSchema = false) // Incremented version for v3 schema
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun barcodeDao(): BarcodeDao
    abstract fun revokedMessageDao(): RevokedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE barcodes ADD COLUMN keyType TEXT NOT NULL DEFAULT 'SINGLE_BARCODE'")
                db.execSQL("ALTER TABLE barcodes ADD COLUMN passwordHash TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE barcodes ADD COLUMN barcodeSequence TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS app_contact_associations")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN rootKey BLOB")
                db.execSQL("ALTER TABLE contacts ADD COLUMN sendingChainKey BLOB")
                db.execSQL("ALTER TABLE contacts ADD COLUMN receivingChainKey BLOB")
                db.execSQL("ALTER TABLE contacts ADD COLUMN sendingMessageNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN receivingMessageNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN previousSendingChainMessageNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN dhKeyPair BLOB")
                db.execSQL("ALTER TABLE contacts ADD COLUMN dhRemotePublicKey BLOB")
                db.execSQL("ALTER TABLE contacts ADD COLUMN skippedMessageKeys TEXT")
            }
        }

        fun getDatabase(context: Context, passphrase: CharSequence): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = net.sqlcipher.database.SupportFactory(passphrase.toString().toByteArray())
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barcodencrypt_database"
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}