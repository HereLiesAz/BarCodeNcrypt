package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface OpenedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(openedMessage: OpenedMessage)

    @Update
    suspend fun update(openedMessage: OpenedMessage)

    @Query("SELECT * FROM opened_messages WHERE messageId = :messageId")
    suspend fun getById(messageId: String): OpenedMessage?
}
