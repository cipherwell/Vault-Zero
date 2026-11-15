package com.example.core

import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType

/**
 * Search syntax for the vault list. Plain words match title/category/tags and
 * the type-specific fields; tokens narrow the result set:
 *
 *   type:login   type:card   type:api   type:crypto   type:note   type:identity
 *   is:fav (favorites only)  is:all (clears the favorite constraint)
 *   tag:work
 *
 * Unknown `prefix:value` tokens are treated as plain text, so typing a URL or a
 * time does not silently change the query.
 */
data class SearchSpec(
    val terms: List<String> = emptyList(),
    val type: VaultType? = null,
    val favoritesOnly: Boolean? = null,
    val tags: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = terms.isEmpty() && type == null && favoritesOnly == null && tags.isEmpty()
}

object SearchQuery {

    private val TYPE_ALIASES: Map<String, VaultType> = mapOf(
        "login" to VaultType.LOGIN,
        "logins" to VaultType.LOGIN,
        "card" to VaultType.CARD,
        "cards" to VaultType.CARD,
        "api" to VaultType.API_SECRET,
        "apisecret" to VaultType.API_SECRET,
        "apisecrets" to VaultType.API_SECRET,
        "crypto" to VaultType.CRYPTO_WALLET,
        "wallet" to VaultType.CRYPTO_WALLET,
        "note" to VaultType.NOTE,
        "notes" to VaultType.NOTE,
        "identity" to VaultType.IDENTITY,
        "identities" to VaultType.IDENTITY,
    )

    fun parse(raw: String): SearchSpec {
        if (raw.isBlank()) return SearchSpec()
        val terms = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var type: VaultType? = null
        var favorites: Boolean? = null

        raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { token ->
            val prefix = token.substringBefore(':', missingDelimiterValue = "").lowercase()
            val value = token.substringAfter(':', missingDelimiterValue = "")
            when {
                prefix.isEmpty() || value.isEmpty() -> terms += token
                prefix == "type" || prefix == "t" ->
                    TYPE_ALIASES[value.lowercase()]?.let { type = it } ?: terms.add(token)
                prefix == "is" || prefix == "fav" || prefix == "favorite" -> when (value.lowercase()) {
                    "fav", "favorite", "favourite", "true", "yes" -> favorites = true
                    "all", "any", "false", "no" -> favorites = false
                    else -> terms += token
                }
                prefix == "tag" -> tags += value.lowercase()
                else -> terms += token
            }
        }
        return SearchSpec(terms, type, favorites, tags)
    }

    /** True when [item] satisfies every constraint in [spec]. */
    fun matches(item: VaultItem, spec: SearchSpec): Boolean {
        if (spec.type != null && item.type != spec.type) return false
        if (spec.favoritesOnly == true && !item.isFavorite) return false
        if (spec.tags.any { tag -> item.tags.none { it.equals(tag, ignoreCase = true) } }) return false
        if (spec.terms.isEmpty()) return true
        return spec.terms.all { term -> matchesTerm(item, term) }
    }

    private fun matchesTerm(item: VaultItem, term: String): Boolean {
        if (item.title.contains(term, ignoreCase = true)) return true
        if (item.category.contains(term, ignoreCase = true)) return true
        if (item.tags.any { it.contains(term, ignoreCase = true) }) return true
        return when (val payload = item.payload) {
            is LoginPayload ->
                payload.username.contains(term, ignoreCase = true) ||
                    payload.url.contains(term, ignoreCase = true) ||
                    payload.notes.contains(term, ignoreCase = true)
            is CardPayload ->
                payload.cardholderName.contains(term, ignoreCase = true) ||
                    payload.brand.contains(term, ignoreCase = true) ||
                    payload.cardNumber.takeLast(4).contains(term, ignoreCase = true) ||
                    payload.notes.contains(term, ignoreCase = true)
            is ApiSecretPayload ->
                payload.serviceName.contains(term, ignoreCase = true) ||
                    payload.environment.contains(term, ignoreCase = true) ||
                    payload.scopes.contains(term, ignoreCase = true) ||
                    payload.endpointUrl.contains(term, ignoreCase = true)
            is CryptoPayload ->
                payload.walletName.contains(term, ignoreCase = true) ||
                    payload.network.contains(term, ignoreCase = true) ||
                    payload.publicAddress.contains(term, ignoreCase = true)
            is NotePayload -> payload.content.contains(term, ignoreCase = true)
            is IdentityPayload ->
                payload.fullName.contains(term, ignoreCase = true) ||
                    payload.idType.contains(term, ignoreCase = true)
        }
    }
}
