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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFamily

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
        Card(
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
                                        text = item.type.displayName.uppercase(),
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
                            contentDescription = "Close dialog"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Payload Specific Content
                when (val p = item.payload) {
                    is LoginPayload -> {
                        if (p.username.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Username / Email",
                                value = p.username,
                                onCopy = { viewModel.copySecurely("Username", p.username, isSensitive = false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (p.password.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Password",
                                value = p.password,
                                onCopy = { viewModel.copySecurely("Password", p.password, isSensitive = true) },
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
                                TotpLiveBadge(
                                    totpResult = totp,
                                    onCopy = { viewModel.copySecurely("2FA Code", totp.code, isSensitive = true) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        if (p.url.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Website URL",
                                value = p.url,
                                onCopy = { viewModel.copySecurely("Website URL", p.url, isSensitive = false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (p.notes.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Notes",
                                value = p.notes,
                                onCopy = { viewModel.copySecurely("Notes", p.notes, isSensitive = false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is CardPayload -> {
                        SensitiveValueRow(
                            label = "Cardholder",
                            value = p.cardholderName,
                            onCopy = { viewModel.copySecurely("Cardholder", p.cardholderName, isSensitive = false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SensitiveValueRow(
                            label = "Card Number",
                            value = p.cardNumber,
                            onCopy = { viewModel.copySecurely("Card Number", p.cardNumber, isSensitive = true) },
                            isMonospace = true,
                            isMaskable = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                SensitiveValueRow(
                                    label = "Expires",
                                    value = "${p.expMonth}/${p.expYear}",
                                    onCopy = { viewModel.copySecurely("Expiry", "${p.expMonth}/${p.expYear}", false) },
                                    isMaskable = false
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                SensitiveValueRow(
                                    label = "CVV",
                                    value = p.cvv,
                                    onCopy = { viewModel.copySecurely("CVV", p.cvv, true) },
                                    isMaskable = true
                                )
                            }
                        }
                        if (p.pin.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "ATM PIN",
                                value = p.pin,
                                onCopy = { viewModel.copySecurely("PIN", p.pin, true) },
                                isMaskable = true
                            )
                        }
                        if (p.brand.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Card Brand",
                                value = p.brand,
                                onCopy = { viewModel.copySecurely("Brand", p.brand, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Notes",
                                value = p.notes,
                                onCopy = { viewModel.copySecurely("Notes", p.notes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is ApiSecretPayload -> {
                        SensitiveValueRow(
                            label = "Service / Provider",
                            value = p.serviceName,
                            onCopy = { viewModel.copySecurely("Service", p.serviceName, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (p.apiKey.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "API Key",
                                value = p.apiKey,
                                onCopy = { viewModel.copySecurely("API Key", p.apiKey, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.secretToken.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Secret Token",
                                value = p.secretToken,
                                onCopy = { viewModel.copySecurely("Secret Token", p.secretToken, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        SensitiveValueRow(
                            label = "Environment",
                            value = p.environment,
                            onCopy = { viewModel.copySecurely("Environment", p.environment, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        if (p.scopes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Scopes",
                                value = p.scopes,
                                onCopy = { viewModel.copySecurely("Scopes", p.scopes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.endpointUrl.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Endpoint URL",
                                value = p.endpointUrl,
                                onCopy = { viewModel.copySecurely("Endpoint URL", p.endpointUrl, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Notes",
                                value = p.notes,
                                onCopy = { viewModel.copySecurely("Notes", p.notes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is CryptoPayload -> {
                        if (p.walletName.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Wallet Name",
                                value = p.walletName,
                                onCopy = { viewModel.copySecurely("Wallet Name", p.walletName, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        SensitiveValueRow(
                            label = "Network",
                            value = p.network,
                            onCopy = { viewModel.copySecurely("Network", p.network, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (p.publicAddress.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Public Address",
                                value = p.publicAddress,
                                onCopy = { viewModel.copySecurely("Public Address", p.publicAddress, false) },
                                isMaskable = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.seedPhrase.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Seed / Recovery Phrase",
                                value = p.seedPhrase,
                                onCopy = { viewModel.copySecurely("Seed Phrase", p.seedPhrase, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.privateKey.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Private Key",
                                value = p.privateKey,
                                onCopy = { viewModel.copySecurely("Private Key", p.privateKey, true) },
                                isMaskable = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (p.derivationPath.isNotEmpty()) {
                            SensitiveValueRow(
                                label = "Derivation Path",
                                value = p.derivationPath,
                                onCopy = { viewModel.copySecurely("Derivation Path", p.derivationPath, false) },
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Notes",
                                value = p.notes,
                                onCopy = { viewModel.copySecurely("Notes", p.notes, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                    }

                    is NotePayload -> {
                        SensitiveValueRow(
                            label = "Secure Note Content",
                            value = p.content,
                            onCopy = { viewModel.copySecurely("Note", p.content, true) },
                            isMonospace = false,
                            isMaskable = false
                        )
                    }

                    is IdentityPayload -> {
                        SensitiveValueRow(
                            label = "Full Name",
                            value = p.fullName,
                            onCopy = { viewModel.copySecurely("Full Name", p.fullName, false) },
                            isMonospace = false,
                            isMaskable = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SensitiveValueRow(
                            label = "${p.idType} Number",
                            value = p.idNumber,
                            onCopy = { viewModel.copySecurely("ID Number", p.idNumber, true) },
                            isMaskable = true
                        )
                        if (p.birthDate.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Birth Date",
                                value = p.birthDate,
                                onCopy = { viewModel.copySecurely("Birth Date", p.birthDate, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.expiryDate.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Expiry Date",
                                value = p.expiryDate,
                                onCopy = { viewModel.copySecurely("Expiry Date", p.expiryDate, false) },
                                isMonospace = false,
                                isMaskable = false
                            )
                        }
                        if (p.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SensitiveValueRow(
                                label = "Notes",
                                value = p.notes,
                                onCopy = { viewModel.copySecurely("Notes", p.notes, false) },
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
                            status = status,
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
                        Text("Delete")
                    }

                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).testTag("edit_item_button"),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete '${item.title}'?") },
            text = { Text("This encrypted item will be permanently removed from your vault. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteItem(item)
                    },
                    modifier = Modifier.testTag("confirm_delete_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
