package com.example.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.android.data.local.entity.TrustedSenderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedSenderDao {
    @Query("SELECT * FROM trusted_senders ORDER BY addedTimestamp DESC")
    fun observeAllTrustedSenders(): Flow<List<TrustedSenderEntity>>

    @Query("SELECT * FROM trusted_senders")
    suspend fun getAllTrustedSenders(): List<TrustedSenderEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM trusted_senders WHERE UPPER(sender) = UPPER(:sender))")
    suspend fun isTrusted(sender: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedSender(sender: TrustedSenderEntity)

    @Query("DELETE FROM trusted_senders WHERE UPPER(sender) = UPPER(:sender)")
    suspend fun removeTrustedSender(sender: String)

    @Query("DELETE FROM trusted_senders")
    suspend fun clearAll()
}
