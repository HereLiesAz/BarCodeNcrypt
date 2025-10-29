package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.Index // Import for Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["lookupKey"], unique = true)] // Added unique index
)
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lookupKey: String,
    val name: String,

    // V3 Double Ratchet State fields removed
)
