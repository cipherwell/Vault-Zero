package com.example.core

import com.example.model.VaultItem
import com.example.model.VaultType

data class VaultSection(
    val type: VaultType,
    val items: List<VaultItem>,
) {
    val title: String get() = type.displayName
    val count: Int get() = items.size
}

/**
 * Groups the (already filtered + sorted) vault list into type sections in a
 * stable, meaningful order. Sections with no items are omitted.
 */
object VaultGrouping {

    private val ORDER = listOf(
        VaultType.LOGIN,
        VaultType.CARD,
        VaultType.API_SECRET,
        VaultType.CRYPTO_WALLET,
        VaultType.NOTE,
        VaultType.IDENTITY,
    )

    fun group(items: List<VaultItem>): List<VaultSection> =
        ORDER.mapNotNull { type ->
            items.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { VaultSection(type, it) }
        }
}
