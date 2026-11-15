package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: String,
    val category: String,
    val encryptedPayload: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val lastUsedAt: Long = 0L,
    val tagsJson: String
)
