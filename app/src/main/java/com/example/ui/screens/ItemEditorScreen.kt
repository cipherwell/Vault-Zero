package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.ui.EditFocus
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.crypto.VaultCrypto
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.ui.components.StrengthMeter
import com.example.ui.components.VaultTypeIcon
import com.example.ui.i18n.localizedSingularName
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.VaultCard

@Composable
fun ItemEditorDialog(
    item: VaultItem,
    onSave: (VaultItem) -> Unit,
    onDismiss: () -> Unit,
    initialFocus: EditFocus? = null
) {
    val titleFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val totpFocus = remember { FocusRequester() }

    LaunchedEffect(initialFocus) {
        if (initialFocus != null) {
            // Wait for the dialog to lay out before grabbing focus (otherwise the
            // request is dropped and the keyboard never opens).
            delay(300)
            when (initialFocus) {
                EditFocus.PASSWORD -> passwordFocus
                EditFocus.TOTP -> totpFocus
                else -> titleFocus
            }.requestFocus()
        }
    }
    var title by remember { mutableStateOf(item.title) }
    var category by remember { mutableStateOf(item.category) }

    // Login state
    val loginPayload = (item.payload as? LoginPayload) ?: LoginPayload()
    var username by remember { mutableStateOf(loginPayload.username) }
    var password by remember { mutableStateOf(loginPayload.password) }
    var url by remember { mutableStateOf(loginPayload.url) }
    var totpSecret by remember { mutableStateOf(loginPayload.totpSecret) }
    var notes by remember { mutableStateOf(loginPayload.notes) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Card state
    val cardPayload = (item.payload as? CardPayload) ?: CardPayload()
    var cardholderName by remember { mutableStateOf(cardPayload.cardholderName) }
    var cardNumber by remember { mutableStateOf(cardPayload.cardNumber) }
    var expMonth by remember { mutableStateOf(cardPayload.expMonth) }
    var expYear by remember { mutableStateOf(cardPayload.expYear) }
    var cvv by remember { mutableStateOf(cardPayload.cvv) }
    var pin by remember { mutableStateOf(cardPayload.pin) }
    var brand by remember { mutableStateOf(cardPayload.brand) }
    var cardNotes by remember { mutableStateOf(cardPayload.notes) }

    // API state
    val apiPayload = (item.payload as? ApiSecretPayload) ?: ApiSecretPayload()
    var serviceName by remember { mutableStateOf(apiPayload.serviceName) }
    var apiKey by remember { mutableStateOf(apiPayload.apiKey) }
    var secretToken by remember { mutableStateOf(apiPayload.secretToken) }
    var environment by remember { mutableStateOf(apiPayload.environment) }
    var scopes by remember { mutableStateOf(apiPayload.scopes) }
    var endpointUrl by remember { mutableStateOf(apiPayload.endpointUrl) }
    var apiNotes by remember { mutableStateOf(apiPayload.notes) }

    // Crypto state
    val cryptoPayload = (item.payload as? CryptoPayload) ?: CryptoPayload()
    var walletName by remember { mutableStateOf(cryptoPayload.walletName) }
    var network by remember { mutableStateOf(cryptoPayload.network) }
    var publicAddress by remember { mutableStateOf(cryptoPayload.publicAddress) }
    var seedPhrase by remember { mutableStateOf(cryptoPayload.seedPhrase) }
    var privateKey by remember { mutableStateOf(cryptoPayload.privateKey) }
    var derivationPath by remember { mutableStateOf(cryptoPayload.derivationPath) }
    var cryptoNotes by remember { mutableStateOf(cryptoPayload.notes) }

    // Note state
    val notePayload = (item.payload as? NotePayload) ?: NotePayload()
    var noteContent by remember { mutableStateOf(notePayload.content) }
    val isPinned by remember { mutableStateOf(notePayload.isPinned) }

    // Identity state
    val idPayload = (item.payload as? IdentityPayload) ?: IdentityPayload()
    var fullName by remember { mutableStateOf(idPayload.fullName) }
    var idType by remember { mutableStateOf(idPayload.idType) }
    var idNumber by remember { mutableStateOf(idPayload.idNumber) }
    var birthDate by remember { mutableStateOf(idPayload.birthDate) }
    var expiryDate by remember { mutableStateOf(idPayload.expiryDate) }
    var idNotes by remember { mutableStateOf(idPayload.notes) }

    val strength = remember(password) { VaultCrypto.calculateStrength(password) }

    // Unsaved-changes guard: closing the editor with typed secrets must be intentional.
    val currentSnapshot = remember(title, category, username, password, url, totpSecret, notes,
        cardholderName, cardNumber, expMonth, expYear, cvv, pin, brand, cardNotes,
        serviceName, apiKey, secretToken, environment, scopes, endpointUrl, apiNotes,
        walletName, network, publicAddress, seedPhrase, privateKey, derivationPath, cryptoNotes,
        noteContent, fullName, idType, idNumber, birthDate, expiryDate, idNotes) {
        listOf(title, category, username, password, url, totpSecret, notes,
            cardholderName, cardNumber, expMonth, expYear, cvv, pin, brand, cardNotes,
            serviceName, apiKey, secretToken, environment, scopes, endpointUrl, apiNotes,
            walletName, network, publicAddress, seedPhrase, privateKey, derivationPath, cryptoNotes,
            noteContent, fullName, idType, idNumber, birthDate, expiryDate, idNotes)
            .joinToString("\u0000")
    }
    val initialSnapshot = remember { currentSnapshot }
    val hasUnsavedChanges = currentSnapshot != initialSnapshot
    var showDiscardConfirm by remember { mutableStateOf(false) }
    fun requestClose() {
        if (hasUnsavedChanges) showDiscardConfirm = true else onDismiss()
    }

    // Resolved in composition so the save lambda below can use them without calling
    // composables from a non-composable callback.
    val defaultTitleLogin = stringResource(R.string.item_default_title_login)
    val defaultTitleCard = stringResource(R.string.item_default_title_card)
    val defaultTitleApi = stringResource(R.string.item_default_title_api)
    val defaultTitleCrypto = stringResource(R.string.item_default_title_crypto)
    val defaultTitleNote = stringResource(R.string.item_default_title_note)
    val defaultTitleIdentity = stringResource(R.string.item_default_title_identity)
    val defaultCategory = stringResource(R.string.item_default_category)

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        VaultCard(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp)
                .testTag("item_editor_card")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VaultTypeIcon(type = item.type)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (item.id.isEmpty()) {
                                    stringResource(R.string.item_editor_title_new, item.type.localizedSingularName())
                                } else {
                                    stringResource(R.string.item_editor_title_edit)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.item_editor_subtitle_encrypted),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { requestClose() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.item_a11y_close_editor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Common fields: Title & Category
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.item_field_title)) },
                    placeholder = { Text(stringResource(R.string.item_placeholder_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("editor_title_input").focusRequester(titleFocus),
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.item_field_category)) },
                    placeholder = { Text(stringResource(R.string.item_placeholder_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Type-Specific Fields
                when (item.type) {
                    VaultType.LOGIN -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.item_field_username_or_email)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("editor_username_input"),
                            shape = RoundedCornerShape(18.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.item_field_password)) },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        password = VaultCrypto.generatePassword(18)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = stringResource(R.string.item_a11y_generate_password),
                                            tint = CyberEmerald
                                        )
                                    }
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isPasswordVisible) {
                                                stringResource(R.string.a11y_hide_password)
                                            } else {
                                                stringResource(R.string.a11y_show_password)
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("editor_password_input").focusRequester(passwordFocus),
                            shape = RoundedCornerShape(18.dp)
                        )

                        if (password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            StrengthMeter(strength = strength)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = totpSecret,
                            onValueChange = { totpSecret = it },
                            label = { Text(stringResource(R.string.item_field_totp_secret)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_totp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("editor_totp_input").focusRequester(totpFocus),
                            shape = RoundedCornerShape(18.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(R.string.item_field_website_url_optional)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.item_field_private_notes_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            minLines = 2
                        )
                    }

                    VaultType.CARD -> {
                        OutlinedTextField(
                            value = cardholderName,
                            onValueChange = { cardholderName = it },
                            label = { Text(stringResource(R.string.item_field_cardholder_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it },
                            label = { Text(stringResource(R.string.item_field_card_number)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_card_number)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = expMonth,
                                onValueChange = { expMonth = it },
                                label = { Text(stringResource(R.string.item_field_expiry_month)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = expYear,
                                onValueChange = { expYear = it },
                                label = { Text(stringResource(R.string.item_field_expiry_year)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = cvv,
                                onValueChange = { cvv = it },
                                label = { Text(stringResource(R.string.item_field_cvv)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                label = { Text(stringResource(R.string.item_field_atm_pin_optional)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text(stringResource(R.string.item_field_card_network_brand)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = cardNotes,
                            onValueChange = { cardNotes = it },
                            label = { Text(stringResource(R.string.item_field_card_notes_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    VaultType.API_SECRET -> {
                        OutlinedTextField(
                            value = serviceName,
                            onValueChange = { serviceName = it },
                            label = { Text(stringResource(R.string.item_field_provider)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_provider)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.item_field_api_key_client_id)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = secretToken,
                            onValueChange = { secretToken = it },
                            label = { Text(stringResource(R.string.item_field_secret_token_private_key)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = environment,
                            onValueChange = { environment = it },
                            label = { Text(stringResource(R.string.item_field_environment)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_environment)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = scopes,
                            onValueChange = { scopes = it },
                            label = { Text(stringResource(R.string.item_field_scopes_permissions_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = endpointUrl,
                            onValueChange = { endpointUrl = it },
                            label = { Text(stringResource(R.string.item_field_endpoint_url_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = apiNotes,
                            onValueChange = { apiNotes = it },
                            label = { Text(stringResource(R.string.item_field_notes_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    VaultType.CRYPTO_WALLET -> {
                        OutlinedTextField(
                            value = walletName,
                            onValueChange = { walletName = it },
                            label = { Text(stringResource(R.string.item_field_wallet_name)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_wallet_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = network,
                            onValueChange = { network = it },
                            label = { Text(stringResource(R.string.item_field_blockchain_network)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_network)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = publicAddress,
                            onValueChange = { publicAddress = it },
                            label = { Text(stringResource(R.string.item_field_public_address)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = seedPhrase,
                            onValueChange = { seedPhrase = it },
                            label = { Text(stringResource(R.string.item_field_seed_phrase)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            minLines = 2
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            label = { Text(stringResource(R.string.item_field_private_key_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = derivationPath,
                            onValueChange = { derivationPath = it },
                            label = { Text(stringResource(R.string.item_field_derivation_path_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = cryptoNotes,
                            onValueChange = { cryptoNotes = it },
                            label = { Text(stringResource(R.string.item_field_notes_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    VaultType.NOTE -> {
                        OutlinedTextField(
                            value = noteContent,
                            onValueChange = { noteContent = it },
                            label = { Text(stringResource(R.string.item_field_secure_note)) },
                            modifier = Modifier.fillMaxWidth().testTag("editor_note_input"),
                            shape = RoundedCornerShape(18.dp),
                            minLines = 6
                        )
                    }

                    VaultType.IDENTITY -> {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text(stringResource(R.string.item_field_full_legal_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = idType,
                            onValueChange = { idType = it },
                            label = { Text(stringResource(R.string.item_field_document_type)) },
                            placeholder = { Text(stringResource(R.string.item_placeholder_document_type)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = idNumber,
                            onValueChange = { idNumber = it },
                            label = { Text(stringResource(R.string.item_field_document_number)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = birthDate,
                                onValueChange = { birthDate = it },
                                label = { Text(stringResource(R.string.item_field_birth_date_optional)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = expiryDate,
                                onValueChange = { expiryDate = it },
                                label = { Text(stringResource(R.string.item_field_expiry_date_optional)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = idNotes,
                            onValueChange = { idNotes = it },
                            label = { Text(stringResource(R.string.item_field_notes_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { requestClose() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                title = when (item.type) {
                                    VaultType.LOGIN -> username.ifEmpty { defaultTitleLogin }
                                    VaultType.CARD -> brand.ifEmpty { defaultTitleCard }
                                    VaultType.API_SECRET -> serviceName.ifEmpty { defaultTitleApi }
                                    VaultType.CRYPTO_WALLET -> walletName.ifEmpty { defaultTitleCrypto }
                                    VaultType.NOTE -> defaultTitleNote
                                    VaultType.IDENTITY -> fullName.ifEmpty { defaultTitleIdentity }
                                }
                            }

                            val newPayload = when (item.type) {
                                VaultType.LOGIN -> LoginPayload(username, password, url, totpSecret, notes)
                                VaultType.CARD -> CardPayload(cardholderName, cardNumber, expMonth, expYear, cvv, pin, brand, cardNotes)
                                VaultType.API_SECRET -> ApiSecretPayload(serviceName, apiKey, secretToken, environment, scopes, endpointUrl, apiNotes)
                                VaultType.CRYPTO_WALLET -> CryptoPayload(walletName, network, publicAddress, seedPhrase, privateKey, derivationPath, cryptoNotes)
                                VaultType.NOTE -> NotePayload(noteContent, isPinned)
                                VaultType.IDENTITY -> IdentityPayload(fullName, idType, idNumber, birthDate, expiryDate, idNotes)
                            }

                            val updatedItem = item.copy(
                                title = title,
                                category = category.ifEmpty { defaultCategory },
                                payload = newPayload,
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(updatedItem)
                        },
                        modifier = Modifier.weight(1f).testTag("save_item_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.item_save_encrypted), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.item_discard_title)) },
            text = {
                Text(
                    stringResource(R.string.item_discard_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDismiss()
                }) {
                    Text(stringResource(R.string.item_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.item_keep_editing))
                }
            }
        )
    }
}
