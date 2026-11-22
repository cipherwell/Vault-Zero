package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.ui.VaultViewModel
import com.example.ui.components.SensitiveValueRow
import com.example.ui.components.StrengthMeter
import com.example.ui.components.TotpLiveBadge
import com.example.ui.components.VaultStatusPill
import com.example.ui.components.VaultTypeIcon
import com.example.ui.i18n.UiText
import com.example.ui.i18n.asString
import com.example.ui.i18n.localizedPluralName
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultCard

@Composable
fun ItemDetailDialog(
    item: VaultItem,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val clockTicker by viewModel.clockTicker.collectAsState()
    val clipboardStatus by viewModel.clipboardStatus.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim sits behind the sheet; taps outside dismiss, taps on the sheet
            // never reach it because the sheet is drawn on top.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() }
            )
        VaultCard(
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("item_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // One UI sheet grab handle
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        VaultTypeIcon(type = item.type)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CyberEmerald.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.type.localizedPluralName().uppercase(),
                                        fontSize = 9.sp,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = CyberEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.a11y_close_dialog)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Payload Specific Content
                when (val p = item.payload) {
                    is LoginPayload -> {
                        if (p.username.isNotEmpty()) {
                            val usernameLabel = stringResource(R.string.item_field_username_email)
                            val usernameCopyLabel = stringResource(R.string.item_copy_username)
                            SensitiveValueRow(
                                label = usernameLabel,
                                value = p.username,
                                onCopy = { viewModel.copySecurely(usernameCopyLabel, p.username, isSensitive = false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (p.password.isNotEmpty()) {
                            val passwordLabel = stringResource(R.string.item_field_password)
                            SensitiveValueRow(
                                label = passwordLabel,
                                value = p.password,
                                onCopy = { viewModel.copySecurely(passwordLabel, p.password, isSensitive = true) },
                                isMonospace = true,
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val strength = VaultCrypto.calculateStrength(p.password)
                            StrengthMeter(strength = strength)
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (p.totpSecret.isNotEmpty()) {
                            val totp = VaultCrypto.generateTotp(p.totpSecret, clockTicker)
                            if (totp != null) {
                                val totpCopyLabel = stringResource(R.string.item_copy_2fa_code)
                                TotpLiveBadge(
                                    totpResult = totp,
                                    onCopy = { viewModel.copySecurely(totpCopyLabel, totp.code, isSensitive = true) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        if (p.url.isNotEmpty()) {
                            val websiteUrlLabel = stringResource(R.string.item_field_website_url)
                            SensitiveValueRow(
                                label = websiteUrlLabel,
                                value = p.url,
                                onCopy = { viewModel.copySecurely(websiteUrlLabel, p.url, isSensitive = false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (p.notes.isNotEmpty()) {
                            val notesLabel = stringResource(R.string.item_field_notes)
                            SensitiveValueRow(
                                label = notesLabel,
                                value = p.notes,
                                onCopy = { viewModel.copySecurely(notesLabel, p.notes, isSensitive = false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is CardPayload -> {
                        val cardholderLabel = stringResource(R.string.item_field_cardholder)
                        SensitiveValueRow(
                            label = cardholderLabel,
                            value = p.cardholderName,
                            onCopy = { viewModel.copySecurely(cardholderLabel, p.cardholderName, isSensitive = false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val cardNumberLabel = stringResource(R.string.item_field_card_number)
                        SensitiveValueRow(
                            label = cardNumberLabel,
                            value = p.cardNumber,
                            onCopy = { viewModel.copySecurely(cardNumberLabel, p.cardNumber, isSensitive = true) },
                            isMonospace = true,
                            isMaskable = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                val expiresLabel = stringResource(R.string.item_field_expires)
                                val expiryCopyLabel = stringResource(R.string.item_copy_expiry)
                                SensitiveValueRow(
                                    label = expiresLabel,
                                    value = "${p.expMonth}/${p.expYear}",
                                    onCopy = { viewModel.copySecurely(expiryCopyLabel, "${p.expMonth}/${p.expYear}", false) },
                                    isMaskable = false
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val cvvLabel = stringResource(R.string.item_field_cvv)
                                SensitiveValueRow(
                                    label = cvvLabel,
                                    value = p.cvv,
                                    onCopy = { viewModel.copySecurely(cvvLabel, p.cvv, true) },
                                    isMaskable = true
                                )
                            }
                        }
                        if (p.pin.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val atmPinLabel = stringResource(R.string.item_field_atm_pin)
                            val pinCopyLabel = stringResource(R.string.item_copy_pin)
                            SensitiveValueRow(
                                label = atmPinLabel,
                                value = p.pin,
                                onCopy = { viewModel.copySecurely(pinCopyLabel, p.pin, true) },
                                isMaskable = true
                            )
                        }
                        if (p.brand.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val cardBrandLabel = stringResource(R.string.item_field_card_brand)
                            val brandCopyLabel = stringResource(R.string.item_copy_brand)
                            SensitiveValueRow(
                                label = cardBrandLabel,
                                value = p.brand,
                                onCopy = { viewModel.copySecurely(brandCopyLabel, p.brand, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val cardNotesLabel = stringResource(R.string.item_field_notes)
                            SensitiveValueRow(
                                label = cardNotesLabel,
                                value = p.notes,
                                onCopy = { viewModel.copySecurely(cardNotesLabel, p.notes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is ApiSecretPayload -> {
                        val serviceLabel = stringResource(R.string.item_field_service_provider)
                        val serviceCopyLabel = stringResource(R.string.item_copy_service)
                        SensitiveValueRow(
                            label = serviceLabel,
                            value = p.serviceName,
                            onCopy = { viewModel.copySecurely(serviceCopyLabel, p.serviceName, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (p.apiKey.isNotEmpty()) {
                            val apiKeyLabel = stringResource(R.string.item_field_api_key)
                            SensitiveValueRow(
                                label = apiKeyLabel,
                                value = p.apiKey,
                                onCopy = { viewModel.copySecurely(apiKeyLabel, p.apiKey, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.secretToken.isNotEmpty()) {
                            val secretTokenLabel = stringResource(R.string.item_field_secret_token)
                            SensitiveValueRow(
                                label = secretTokenLabel,
                                value = p.secretToken,
                                onCopy = { viewModel.copySecurely(secretTokenLabel, p.secretToken, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        val environmentLabel = stringResource(R.string.item_field_environment)
                        SensitiveValueRow(
                            label = environmentLabel,
                            value = p.environment,
                            onCopy = { viewModel.copySecurely(environmentLabel, p.environment, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        if (p.scopes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val scopesLabel = stringResource(R.string.item_field_scopes)
                            SensitiveValueRow(
                                label = scopesLabel,
                                value = p.scopes,
                                onCopy = { viewModel.copySecurely(scopesLabel, p.scopes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.endpointUrl.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val endpointUrlLabel = stringResource(R.string.item_field_endpoint_url)
                            SensitiveValueRow(
                                label = endpointUrlLabel,
                                value = p.endpointUrl,
                                onCopy = { viewModel.copySecurely(endpointUrlLabel, p.endpointUrl, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val apiNotesLabel = stringResource(R.string.item_field_notes)
                            SensitiveValueRow(
                                label = apiNotesLabel,
                                value = p.notes,
                                onCopy = { viewModel.copySecurely(apiNotesLabel, p.notes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is CryptoPayload -> {
                        if (p.walletName.isNotEmpty()) {
                            val walletNameLabel = stringResource(R.string.item_field_wallet_name)
                            SensitiveValueRow(
                                label = walletNameLabel,
                                value = p.walletName,
                                onCopy = { viewModel.copySecurely(walletNameLabel, p.walletName, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        val networkLabel = stringResource(R.string.item_field_network)
                        SensitiveValueRow(
                            label = networkLabel,
                            value = p.network,
                            onCopy = { viewModel.copySecurely(networkLabel, p.network, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (p.publicAddress.isNotEmpty()) {
                            val publicAddressLabel = stringResource(R.string.item_field_public_address)
                            SensitiveValueRow(
                                label = publicAddressLabel,
                                value = p.publicAddress,
                                onCopy = { viewModel.copySecurely(publicAddressLabel, p.publicAddress, false) },
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.seedPhrase.isNotEmpty()) {
                            val seedRecoveryLabel = stringResource(R.string.item_field_seed_recovery_phrase)
                            val seedPhraseCopyLabel = stringResource(R.string.item_copy_seed_phrase)
                            SensitiveValueRow(
                                label = seedRecoveryLabel,
                                value = p.seedPhrase,
                                onCopy = { viewModel.copySecurely(seedPhraseCopyLabel, p.seedPhrase, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.privateKey.isNotEmpty()) {
                            val privateKeyLabel = stringResource(R.string.item_field_private_key)
                            SensitiveValueRow(
                                label = privateKeyLabel,
                                value = p.privateKey,
                                onCopy = { viewModel.copySecurely(privateKeyLabel, p.privateKey, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.derivationPath.isNotEmpty()) {
                            val derivationPathLabel = stringResource(R.string.item_field_derivation_path)
                            SensitiveValueRow(
                                label = derivationPathLabel,
                                value = p.derivationPath,
                                onCopy = { viewModel.copySecurely(derivationPathLabel, p.derivationPath, false) },
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val cryptoNotesLabel = stringResource(R.string.item_field_notes)
                            SensitiveValueRow(
                                label = cryptoNotesLabel,
                                value = p.notes,
                                onCopy = { viewModel.copySecurely(cryptoNotesLabel, p.notes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is NotePayload -> {
                        val secureNoteContentLabel = stringResource(R.string.item_field_secure_note_content)
                        val noteCopyLabel = stringResource(R.string.item_copy_note)
                        SensitiveValueRow(
                            label = secureNoteContentLabel,
                            value = p.content,
                            onCopy = { viewModel.copySecurely(noteCopyLabel, p.content, true) },
                            isMonospace = false,
                            isMaskable = false
                        )
                    }

                    is IdentityPayload -> {
                        val fullNameLabel = stringResource(R.string.item_field_full_name)
                        SensitiveValueRow(
                            label = fullNameLabel,
                            value = p.fullName,
                            onCopy = { viewModel.copySecurely(fullNameLabel, p.fullName, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val idTypeNumberLabel = stringResource(R.string.item_field_id_type_number, p.idType)
                        val idNumberCopyLabel = stringResource(R.string.item_copy_id_number)
                        SensitiveValueRow(
                            label = idTypeNumberLabel,
                            value = p.idNumber,
                            onCopy = { viewModel.copySecurely(idNumberCopyLabel, p.idNumber, true) },
                            isMaskable = true
                        )
                        if (p.birthDate.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val birthDateLabel = stringResource(R.string.item_field_birth_date)
                            SensitiveValueRow(
                                label = birthDateLabel,
                                value = p.birthDate,
                                onCopy = { viewModel.copySecurely(birthDateLabel, p.birthDate, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.expiryDate.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val expiryDateLabel = stringResource(R.string.item_field_expiry_date)
                            SensitiveValueRow(
                                label = expiryDateLabel,
                                value = p.expiryDate,
                                onCopy = { viewModel.copySecurely(expiryDateLabel, p.expiryDate, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val identityNotesLabel = stringResource(R.string.item_field_notes)
                            SensitiveValueRow(
                                label = identityNotesLabel,
                                value = p.notes,
                                onCopy = { viewModel.copySecurely(identityNotesLabel, p.notes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = clipboardStatus != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 14.dp)
                ) {
                    clipboardStatus?.let { status ->
                        VaultStatusPill(
                            status = status.asString(),
                            onDismiss = { viewModel.dismissClipboardStatus() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f).testTag("delete_item_button"),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_delete))
                    }

                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).testTag("edit_item_button"),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_edit), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.item_delete_title, item.title)) },
            text = { Text(stringResource(R.string.item_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteItem(item)
                    },
                    modifier = Modifier.testTag("confirm_delete_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(stringResource(R.string.item_delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
