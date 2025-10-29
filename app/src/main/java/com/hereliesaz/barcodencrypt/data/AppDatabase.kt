package com.hereliesaz.barcodencrypt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Contact::class, Barcode::class, OpenedMessage::class, EncryptedScriptLog::class], version = 10, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun barcodeDao(): BarcodeDao
    abstract fun openedMessageDao(): OpenedMessageDao
    abstract fun encryptedScriptLogDao(): EncryptedScriptLogDao

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

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `encrypted_script_log` (`barcodeIdentifier` TEXT NOT NULL, `masterKey` BLOB NOT NULL, `currentChainKey` BLOB NOT NULL, `messageNumber` INTEGER NOT NULL, PRIMARY KEY(`barcodeIdentifier`))")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // This migration is non-destructive for the contacts table.
                // Data loss for the barcodes table is unavoidable due to encryption changes.

                db.execSQL("CREATE TABLE `contacts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lookupKey` TEXT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("INSERT INTO `contacts_new` (`id`, `lookupKey`, `name`) SELECT `id`, `lookupKey`, `name` FROM `contacts`")
                db.execSQL("DROP TABLE `contacts`")
                db.execSQL("ALTER TABLE `contacts_new` RENAME TO `contacts`")
                db.execSQL("CREATE UNIQUE INDEX `index_contacts_lookupKey` ON `contacts` (`lookupKey`)")

                db.execSQL("DROP TABLE `barcodes`")
                db.execSQL("CREATE TABLE `barcodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contactLookupKey` TEXT NOT NULL, `name` TEXT NOT NULL, `encryptedValue` BLOB NOT NULL, `iv` BLOB NOT NULL, `counter` INTEGER NOT NULL, `keyType` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX `index_barcodes_contactLookupKey` ON `barcodes` (`contactLookupKey`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `revoked_messages` RENAME TO `opened_messages`")
                db.execSQL("ALTER TABLE `opened_messages` ADD COLUMN `openCount` INTEGER NOT NULL DEFAULT 1")
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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
