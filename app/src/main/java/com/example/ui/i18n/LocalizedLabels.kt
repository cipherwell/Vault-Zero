package com.example.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ThemeMode
import com.example.model.VaultType

/**
 * Localized display names for model enums. The enums keep their English `displayName`/`label`
 * because they are used for logging and storage; the UI renders these instead.
 */

@Composable
fun VaultType.localizedPluralName(): String = stringResource(
    when (this) {
        VaultType.LOGIN -> R.string.type_login
        VaultType.CARD -> R.string.type_card
        VaultType.API_SECRET -> R.string.type_api_secret
        VaultType.CRYPTO_WALLET -> R.string.type_crypto_wallet
        VaultType.NOTE -> R.string.type_note
        VaultType.IDENTITY -> R.string.type_identity
    }
)

@Composable
fun VaultType.localizedSingularName(): String = stringResource(
    when (this) {
        VaultType.LOGIN -> R.string.type_login_singular
        VaultType.CARD -> R.string.type_card_singular
        VaultType.API_SECRET -> R.string.type_api_secret_singular
        VaultType.CRYPTO_WALLET -> R.string.type_crypto_wallet_singular
        VaultType.NOTE -> R.string.type_note_singular
        VaultType.IDENTITY -> R.string.type_identity_singular
    }
)

@Composable
fun ThemeMode.localizedName(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

