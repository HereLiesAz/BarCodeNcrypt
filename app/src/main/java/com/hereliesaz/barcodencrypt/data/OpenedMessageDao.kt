package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface OpenedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(openedMessage: OpenedMessage)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(openedMessage: OpenedMessage): Long

    @Update
    suspend fun update(openedMessage: OpenedMessage)

    @Query("SELECT * FROM opened_messages WHERE messageId = :messageId")
    suspend fun getById(messageId: String): OpenedMessage?

    @Query("UPDATE opened_messages SET openCount = openCount + 1 WHERE messageId = :messageId")
    suspend fun incrementIfPresent(messageId: String): Int

    /**
     * Atomically bumps the open count, creating the row at 1 if absent. Runs in a single
     * transaction (SQLite serializes writers) so concurrent opens can't lose an increment
     * and slip past a max-open-count limit.
     */
    @Transaction
    suspend fun incrementOpenCount(messageId: String) {
        if (incrementIfPresent(messageId) == 0) {
            if (insertIfAbsent(OpenedMessage(messageId, 1)) == -1L) {
                // A concurrent insert won the race; count its open too.
                incrementIfPresent(messageId)
            }
        }
    }
}
