package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_items ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY updatedAt DESC")
    suspend fun getAllList(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: VaultItemEntity)

    /** Single Room transaction: a verified import is written all-or-nothing. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<VaultItemEntity>)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM vault_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE vault_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE vault_items SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun updateFavoriteAll(ids: List<String>, isFavorite: Boolean)

    @Query("UPDATE vault_items SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long)

    @Query("DELETE FROM vault_items")
    suspend fun clearAll()

    /** First-run restore: replace the empty vault with the backup's records atomically. */
    @Transaction
    suspend fun replaceAllForRestore(entities: List<VaultItemEntity>) {
        clearAll()
        insertOrUpdateAll(entities)
    }
}
