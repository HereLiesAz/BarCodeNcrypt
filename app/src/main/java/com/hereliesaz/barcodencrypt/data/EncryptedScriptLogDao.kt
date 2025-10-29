package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface EncryptedScriptLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(encryptedScriptLog: EncryptedScriptLog)

    @Update
    suspend fun update(encryptedScriptLog: EncryptedScriptLog)

    @Query("SELECT * FROM encrypted_script_log WHERE barcodeIdentifier = :barcodeIdentifier")
    suspend fun getByIdentifier(barcodeIdentifier: String): EncryptedScriptLog?
}
