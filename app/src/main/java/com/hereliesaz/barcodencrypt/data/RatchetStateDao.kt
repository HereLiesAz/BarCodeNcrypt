package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RatchetStateDao {

    @Query("SELECT * FROM ratchet_state WHERE contactLookupKey = :key")
    suspend fun get(key: String): RatchetStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RatchetStateEntity)

    @Query("DELETE FROM ratchet_state WHERE contactLookupKey = :key")
    suspend fun delete(key: String)
}
