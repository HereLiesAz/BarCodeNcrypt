package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A contact bridged from the device address book.
 *
 * v5 crypto state lives in its own table ([RatchetStateEntity]), not here. The v3
 * ratchet-state columns that used to hang off this entity were dropped in
 * `MIGRATION_10_11`.
 *
 * @property id The auto-generated local database ID.
 * @property lookupKey The stable identifier supplied by ContactsContract.
 * @property name The contact's display name (cached for the UI).
 */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["lookupKey"], unique = true)]
)
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val lookupKey: String,
    val name: String,
)
