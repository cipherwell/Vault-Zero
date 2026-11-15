package com.example.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.BuildConfig
import com.example.core.ThemeMode
import com.example.ui.backup.BackupPickerChannel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VaultViewModel
import com.example.ui.components.ZeroKnowledgeSecurityBadge
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.WarningAmber

@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showSpecsDialog by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPhraseOnly by remember { mutableStateOf(false) }
    var pendingExportPhraseOnly by remember { mutableStateOf(false) }
    val importPrompt by viewModel.importVerificationPrompt.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val autoLockSeconds by viewModel.autoLockSeconds.collectAsState()
    val clipboardSeconds by viewModel.clipboardTimeoutSeconds.collectAsState()
    val backupBusy by viewModel.backupBusy.collectAsState()
    val backgroundLockSeconds by viewModel.backgroundLockSeconds.collectAsState()
    val importOutcome by viewModel.importOutcome.collectAsState()
    val vaultItems by viewModel.allItems.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Export/import go through MainActivity's SAF intents (see BackupPickerChannel):
    // the user picks the document, we read/write exactly that URI once, no
    // persistable grant, no clipboard, no share sheet.
    LaunchedEffect(Unit) {
        BackupPickerChannel.results.collect { result ->
            when (result.kind) {
                BackupPickerChannel.Kind.EXPORT -> result.uri?.let { uri ->
                    val protection = if (pendingExportPhraseOnly) {
                        com.example.data.BackupProtection.RECOVERY_PHRASE_ONLY
                    } else {
                        com.example.data.BackupProtection.STANDARD
                    }
                    viewModel.exportBackupJson(protection) { json ->
                        scope.launch {
                            val ok = BackupFileIo.writeText(context, uri, json)
                            backupStatus = if (ok) {
                                "Encrypted backup saved to ${BackupFileIo.displayName(context, uri)}. " +
                                    if (pendingExportPhraseOnly) {
                                        "It can only be opened with this backup's 12-word recovery phrase."
                                    } else {
                                        "It can be opened with this vault's master password or its 12-word recovery phrase. No plaintext secrets inside."
                                    }
                            } else {
                                "Could not write the backup to that location."
                            }
                        }
                    }
                }
                BackupPickerChannel.Kind.IMPORT -> result.uri?.let { uri ->
                    scope.launch {
                        val text = BackupFileIo.readText(context, uri)
                        if (text == null) {
                            backupStatus = "That document is not a readable VaultZero backup envelope."
                        } else {
                            pendingImportJson = text
                            pendingImportName = BackupFileIo.displayName(context, uri)
                            showImportDialog = true
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Vault Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Cryptographic preferences, timeouts, and emergency kits",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Vault identity: one flat panel; the fingerprint is the only mono accent.
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Master key fingerprint",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = viewModel.keyFingerprint,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.copySecurely("Fingerprint", viewModel.keyFingerprint, false)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy fingerprint",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ZeroKnowledgeSecurityBadge()
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // One UI appearance selector
        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(mode.label, fontSize = 13.sp) },
                    shape = CircleShape,
                    border = null,
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Security Options Section
        Text(
            text = "SECURITY CONTROLS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Auto-Lock Timeout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Auto-Lock Inactivity", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (BuildConfig.DEV_DISABLE_AUTO_LOCK) {
                    Text(
                        text = "Auto-lock is disabled in this dev build (-PdevDisableAutoLock=true).",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(0 to "Off", 30 to "30s", 60 to "1m", 300 to "5m", 900 to "15m")
                            .forEach { (sec, label) ->
                                SecurityOptionChip(
                                    label = label,
                                    selected = autoLockSeconds == sec,
                                    modifier = Modifier.weight(1f),
                                ) { viewModel.updateAutoLock(sec) }
                            }
                    }
                    if (autoLockSeconds == 0) {
                        Text(
                            text = "Off: the vault stays unlocked until you tap the lock button.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Lock in Background", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(0 to "Off", 15 to "15s", 30 to "30s", 60 to "1m").forEach { (sec, label) ->
                        SecurityOptionChip(
                            label = label,
                            selected = backgroundLockSeconds == sec,
                            modifier = Modifier.weight(1f),
                        ) { viewModel.updateBackgroundLock(sec) }
                    }
                }
                Text(
                    text = if (backgroundLockSeconds == 0) {
                        "Off: the vault stays unlocked when you leave the app or turn the screen off."
                    } else {
                        "Locks after $backgroundLockSeconds s away or immediately when the screen turns off. Not applied while the file picker is open."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (backgroundLockSeconds == 0) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Clipboard Auto-Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Auto-Clear Clipboard", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(0 to "Off", 15 to "15s", 30 to "30s", 60 to "60s")
                        .forEach { (sec, label) ->
                            SecurityOptionChip(
                                label = label,
                                selected = clipboardSeconds == sec,
                                modifier = Modifier.weight(1f),
                            ) { viewModel.updateClipboardTimeout(sec) }
                        }
                }
                if (clipboardSeconds == 0) {
                    Text(
                        text = "Off: copied secrets stay on the clipboard until you copy something else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fast Device Touch Unlock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Fast Biometric Unlock", style = MaterialTheme.typography.bodyMedium)
                            Text("Quickly unlock with device biometrics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { viewModel.toggleBiometric(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rotate the password-derived envelope without re-keying records.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showChangePasswordDialog = true }
                        .padding(vertical = 10.dp)
                        .testTag("change_master_password_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Change Master Password", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Records stay encrypted · recovery kit keeps working",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.lockVault() }
                        .padding(vertical = 10.dp)
                        .testTag("lock_now_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock Vault Now", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Lock immediately and clear copied secrets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Lock",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tools & Recovery
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "RECOVERY & BACKUP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            if (backupBusy) {
                Spacer(modifier = Modifier.width(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Working…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // View Recovery Kit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showRecoveryDialog = true }
                        .padding(vertical = 10.dp)
                        .testTag("view_recovery_kit_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("View 12-Word Recovery Kit", fontWeight = FontWeight.SemiBold)
                        Text("Decrypt and view your emergency recovery phrase", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Export Encrypted Backup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !backupBusy) { showExportDialog = true }
                        .padding(vertical = 10.dp)
                        .testTag("export_backup_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Export Encrypted Vault Backup", fontWeight = FontWeight.SemiBold)
                        Text("Generate AES-256-GCM encrypted JSON backup envelope", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Import Encrypted Backup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !backupBusy) {
                            BackupPickerChannel.requestImport()
                        }
                        .padding(vertical = 10.dp)
                        .testTag("import_backup_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Restore / Import Encrypted Backup", fontWeight = FontWeight.SemiBold)
                        Text("Import records from a VaultZero encrypted JSON backup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cryptographic Architecture Specs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showSpecsDialog = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cryptographic Specifications", fontWeight = FontWeight.SemiBold)
                        Text("AES-256-GCM, PBKDF2-HMAC-SHA256, Zero Telemetry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Danger Zone
        Text(
            text = "DANGER ZONE",
            style = MaterialTheme.typography.labelMedium,
            color = DangerRed,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth().testTag("reset_vault_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
        ) {
            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset & Erase Vault Completely")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "VaultZero v1.0.0 (Build 1) • 100% FOSS Air-Gapped Build",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Erase Vault Completely?") },
            text = { Text("All encrypted records, keys, and master passwords will be irrecoverably wiped from this device. Are you sure you wish to continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        viewModel.clearVault()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRecoveryDialog) {
        val currentPhrase = remember { viewModel.recoveryPhrase }
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Emergency Recovery Kit") },
            text = {
                Column {
                    Text(
                        text = "12-word seed phrase for master key recovery (decrypted in memory):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentPhrase.ifEmpty { "Unavailable (Vault locked)" },
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentPhrase.isNotEmpty()) {
                            viewModel.copySecurely("Recovery Kit", currentPhrase, true)
                        }
                        showRecoveryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Copy & Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) { Text("Dismiss") }
            }
        )
    }

    backupStatus?.let { status ->
        AlertDialog(
            onDismissRequest = { backupStatus = null },
            title = { Text("Backup") },
            text = {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { backupStatus = null }) { Text("OK") }
            }
        )
    }

    if (showImportDialog) {
        var backupPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Encrypted Backup") },
            text = {
                Column {
                    Text(
                        text = "${pendingImportName ?: "Backup file"} is ready to import. Enter the source vault's Master Password or its 12-word Recovery Phrase when the file was created by a different vault; leave it blank for a backup of this vault (unlock with biometrics first to avoid typing). Phrase-only backups always require their recovery phrase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Master Password or 12-Word Recovery Phrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImportJson?.let { json ->
                            viewModel.importBackupJson(json, backupPassword.ifBlank { null })
                        }
                        showImportDialog = false
                        pendingImportJson = null
                        pendingImportName = null
                    },
                    enabled = pendingImportJson != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Decrypt & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    importOutcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportOutcome() },
            title = { Text("Import complete") },
            text = {
                Column {
                    Text(
                        text = outcome.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val rows = buildList {
                        if (outcome.imported > 0) add("New entries: ${outcome.imported}")
                        if (outcome.updated > 0) add("Updated: ${outcome.updated}")
                        if (outcome.duplicatesSkipped > 0) add("Duplicates skipped: ${outcome.duplicatesSkipped}")
                        if (outcome.skippedInvalid > 0) add("Unreadable records skipped: ${outcome.skippedInvalid}")
                    }
                    rows.forEach { line ->
                        Text(
                            text = "· $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The backup was verified before any record was written.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportOutcome() }) { Text("OK") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Encrypted Backup") },
            text = {
                Column {
                    Text(
                        text = "How should this backup be unlockable?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (vaultItems.isNotEmpty()) {
                        val summary = vaultItems.groupingBy { it.type.displayName }
                            .eachCount().entries
                            .sortedByDescending { it.value }
                            .joinToString(" · ") { "${it.value} ${it.key.lowercase()}" }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Preview: ${vaultItems.size} encrypted ${if (vaultItems.size == 1) "entry" else "entries"} ($summary)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { exportPhraseOnly = false }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !exportPhraseOnly, onClick = { exportPhraseOnly = false })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Master password or recovery phrase", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Restore with either credential · cross-device friendly",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { exportPhraseOnly = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = exportPhraseOnly, onClick = { exportPhraseOnly = true })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("12-word recovery phrase only", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Password envelope is stripped; only that phrase opens it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Biometrics unlock this vault locally; they cannot decrypt a foreign backup. The recovery phrase is the portable credential.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val stamp = java.text.SimpleDateFormat(
                            "yyyyMMdd-HHmm",
                            java.util.Locale.US,
                        ).format(java.util.Date())
                        pendingExportPhraseOnly = exportPhraseOnly
                        showExportDialog = false
                        val suffix = if (exportPhraseOnly) "-phrase" else ""
                        BackupPickerChannel.requestExport("vaultzero-backup-$stamp$suffix.json")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Choose file")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    importPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPartialImport() },
            title = { Text("Backup needs attention") },
            text = {
                Column {
                    Text(
                        text = prompt.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${prompt.unreadableRecords} unreadable " +
                            (if (prompt.unreadableRecords == 1) "record will be skipped. " else "records will be skipped. ") +
                            "The readable records can still be imported; the file itself is not modified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPartialImport() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Import readable records")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPartialImport() }) { Text("Cancel") }
            }
        )
    }

    if (showChangePasswordDialog) {
        var currentPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmPw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Master Password") },
            text = {
                Column {
                    Text(
                        text = "Your records are encrypted with the vault key, not the password, so they are not re-encrypted. The vault key is re-wrapped with the new password; your 12-word recovery kit remains valid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = currentPw,
                        onValueChange = { currentPw = it },
                        label = { Text("Current Master Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("New Master Password (min 8)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changeMasterPassword(currentPw, newPw, confirmPw) { ok ->
                            if (ok) showChangePasswordDialog = false
                        }
                    },
                    enabled = currentPw.isNotBlank() && newPw.isNotBlank() && confirmPw.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Change Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSpecsDialog) {
        AlertDialog(
            onDismissRequest = { showSpecsDialog = false },
            title = { Text("VaultZero Architecture") },
            text = {
                Column {
                    Text(
                        text = "• Cipher: AES-256-GCM authenticated encryption (128-bit authentication tag, unique 12-byte IV per record, per-record GCM AAD binding).\n\n" +
                                "• Zero Metadata Leakage: Record titles, categories, tags, and payloads are encrypted together inside AES-256-GCM; SQLite columns store redacted placeholders.\n\n" +
                                "• Key Derivation & Recovery: PBKDF2WithHmacSHA256 (600,000 rounds), 128-bit CSPRNG salt, HMAC-SHA256 verifiers, and SHA-256 checksummed 12-word recovery mnemonic.\n\n" +
                                "• Client-Side Zero-Knowledge: Zero network permissions, zero remote servers, StrongBox/TEE Keystore, FLAG_SECURE + recents-screenshot suppression, and backups that leave only through a Storage Access Framework document you pick.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSpecsDialog = false }) { Text("Close") }
            }
        )
    }
}

/** One UI pill chip used for the security timeout options. */
@Composable
private fun SecurityOptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
