package com.example.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.BuildConfig
import com.example.R
import com.example.core.DiagnosticsReport
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VaultLockState
import com.example.ui.VaultViewModel
import com.example.ui.components.ZeroKnowledgeSecurityBadge
import com.example.ui.i18n.LocalAppLanguageTag
import com.example.ui.i18n.LocalLanguageController
import com.example.ui.i18n.SettingsLanguageRow
import com.example.ui.i18n.asString
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
    var showRotateKitConfirm by remember { mutableStateOf(false) }
    var rotatingKit by remember { mutableStateOf(false) }
    var newRecoveryKit by remember { mutableStateOf<String?>(null) }
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
    val languageTag = LocalAppLanguageTag.current
    val lockState by viewModel.lockState.collectAsState()
    val biometricsEnabled by viewModel.biometricEnabled.collectAsState()
    val authErrorState by viewModel.authError.collectAsState()
    val lastErrorText = authErrorState?.asString()
    val languageSelectionTag = LocalLanguageController.current?.language?.tag ?: "system"
    val biometricsAvailabilityToken = remember(context) {
        when (
            androidx.biometric.BiometricManager.from(context)
                .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        ) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> "available"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "no enrolment"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "no hardware"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "hardware unavailable"
            else -> "unsupported"
        }
    }

    // The backup collector outlives recompositions, so keep the locale-bound context current:
    // otherwise a language switch would keep emitting status messages in the old language.
    val latestContext by rememberUpdatedState(context)
    val fingerprintClipLabel = stringResource(R.string.settings_clipboard_label_fingerprint)
    val recoveryKitClipLabel = stringResource(R.string.settings_clipboard_label_recovery_kit)

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
                                val fileName = BackupFileIo.displayName(latestContext, uri)
                                if (pendingExportPhraseOnly) {
                                    latestContext.getString(R.string.settings_backup_saved_phrase_only, fileName)
                                } else {
                                    latestContext.getString(R.string.settings_backup_saved_standard, fileName)
                                }
                            } else {
                                latestContext.getString(R.string.settings_backup_write_failed)
                            }
                        }
                    }
                }
                BackupPickerChannel.Kind.IMPORT -> result.uri?.let { uri ->
                    scope.launch {
                        val text = BackupFileIo.readText(context, uri)
                        if (text == null) {
                            backupStatus = latestContext.getString(R.string.settings_backup_unreadable_document)
                        } else {
                            pendingImportJson = text
                            pendingImportName = BackupFileIo.displayName(latestContext, uri)
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
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
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
                            text = stringResource(R.string.settings_master_key_fingerprint),
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
                            viewModel.copySecurely(fingerprintClipLabel, viewModel.keyFingerprint, false)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.settings_a11y_copy_fingerprint),
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
            text = stringResource(R.string.settings_section_appearance).uppercase(),
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
                    label = { Text(themeLabel(mode), fontSize = 13.sp) },
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

        // Language selector: applies instantly and is stored outside the vault, so it also works
        // on the lock screen before the first unlock.
        Text(
            text = stringResource(R.string.language_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsLanguageRow(
                controller = LocalLanguageController.current,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Security Options Section
        Text(
            text = stringResource(R.string.settings_section_security).uppercase(),
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
                        Text(stringResource(R.string.settings_auto_lock_inactivity), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (BuildConfig.DEV_DISABLE_AUTO_LOCK) {
                    Text(
                        text = stringResource(R.string.settings_auto_lock_disabled_dev),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(
                            0 to R.string.settings_timeout_off,
                            30 to R.string.settings_timeout_30s,
                            60 to R.string.settings_timeout_1m,
                            300 to R.string.settings_timeout_5m,
                            900 to R.string.settings_timeout_15m,
                        ).forEach { (sec, labelRes) ->
                            SecurityOptionChip(
                                label = stringResource(labelRes),
                                selected = autoLockSeconds == sec,
                                modifier = Modifier.weight(1f),
                            ) { viewModel.updateAutoLock(sec) }
                        }
                    }
                    if (autoLockSeconds == 0) {
                        Text(
                            text = stringResource(R.string.settings_auto_lock_off_warning),
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
                    Text(stringResource(R.string.settings_lock_in_background), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(
                        0 to R.string.settings_timeout_off,
                        15 to R.string.settings_timeout_15s,
                        30 to R.string.settings_timeout_30s,
                        60 to R.string.settings_timeout_1m,
                    ).forEach { (sec, labelRes) ->
                        SecurityOptionChip(
                            label = stringResource(labelRes),
                            selected = backgroundLockSeconds == sec,
                            modifier = Modifier.weight(1f),
                        ) { viewModel.updateBackgroundLock(sec) }
                    }
                }
                Text(
                    text = if (backgroundLockSeconds == 0) {
                        stringResource(R.string.settings_background_lock_off_warning)
                    } else {
                        stringResource(R.string.settings_background_lock_locks_after, backgroundLockSeconds)
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
                        Text(stringResource(R.string.settings_auto_clear_clipboard), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(
                        0 to R.string.settings_timeout_off,
                        15 to R.string.settings_timeout_15s,
                        30 to R.string.settings_timeout_30s,
                        60 to R.string.settings_timeout_60s,
                    ).forEach { (sec, labelRes) ->
                        SecurityOptionChip(
                            label = stringResource(labelRes),
                            selected = clipboardSeconds == sec,
                            modifier = Modifier.weight(1f),
                        ) { viewModel.updateClipboardTimeout(sec) }
                    }
                }
                if (clipboardSeconds == 0) {
                    Text(
                        text = stringResource(R.string.settings_clipboard_off_warning),
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_biometric_title),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(R.string.settings_biometric_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
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
                        Text(stringResource(R.string.settings_change_master_password), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_change_master_password_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_action_change),
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
                        Text(stringResource(R.string.settings_lock_vault_now), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_lock_vault_now_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_action_lock),
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
                text = stringResource(R.string.settings_section_recovery).uppercase(),
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
                    text = stringResource(R.string.settings_working),
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
                        Text(stringResource(R.string.settings_view_recovery_kit), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_view_recovery_kit_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rotate recovery kit (upgrade legacy phrase / recover from exposure)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !rotatingKit) {
                            if (!rotatingKit) showRotateKitConfirm = true
                        }
                        .padding(vertical = 10.dp)
                        .testTag("rotate_recovery_kit_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (rotatingKit) {
                                stringResource(R.string.settings_rotate_kit_busy)
                            } else {
                                stringResource(R.string.settings_rotate_kit)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.settings_rotate_kit_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Text(stringResource(R.string.settings_export_backup), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_export_backup_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(stringResource(R.string.settings_import_backup), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_import_backup_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(stringResource(R.string.settings_specs_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_specs_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Danger Zone
        Text(
            text = stringResource(R.string.settings_section_danger).uppercase(),
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
            Text(stringResource(R.string.settings_reset_vault))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Support diagnostics: build/device/vault state only, never secrets. Testers can copy
        // this into a bug report without exposing any stored data.
        Text(
            text = stringResource(R.string.settings_section_about),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        val diagnosticsLabel = stringResource(R.string.settings_copy_diagnostics)
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(
                    onClick = {
                        val report = DiagnosticsReport.build(
                            DiagnosticsReport.Input(
                                appVersionName = BuildConfig.VERSION_NAME,
                                appVersionCode = BuildConfig.VERSION_CODE,
                                packageName = context.packageName,
                                androidRelease = android.os.Build.VERSION.RELEASE ?: "?",
                                sdkInt = android.os.Build.VERSION.SDK_INT,
                                manufacturer = android.os.Build.MANUFACTURER ?: "?",
                                model = android.os.Build.MODEL ?: "?",
                                languageTag = languageTag,
                                selectedLanguage = languageSelectionTag,
                                vaultInitialized = lockState != VaultLockState.NEEDS_SETUP,
                                vaultUnlocked = lockState == VaultLockState.UNLOCKED,
                                itemCount = vaultItems.size,
                                biometricsEnabled = biometricsEnabled,
                                biometricsAvailable = biometricsAvailabilityToken,
                                keyFingerprint = viewModel.keyFingerprint,
                                lastError = lastErrorText,
                            )
                        )
                        viewModel.copySecurely(
                            label = diagnosticsLabel,
                            value = report,
                            isSensitive = false,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_copy_diagnostics))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_copy_diagnostics_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(
                R.string.settings_version_footer,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showRotateKitConfirm) {
        AlertDialog(
            onDismissRequest = { if (!rotatingKit) showRotateKitConfirm = false },
            title = { Text(stringResource(R.string.settings_rotate_kit_dialog_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_rotate_kit_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRotateKitConfirm = false
                        rotatingKit = true
                        viewModel.regenerateRecoveryKit { phrase ->
                            rotatingKit = false
                            newRecoveryKit = phrase
                        }
                    },
                    modifier = Modifier.testTag("rotate_recovery_kit_confirm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningAmber,
                        contentColor = Color.Black
                    )
                ) {
                    Text(stringResource(R.string.settings_rotate_kit_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotateKitConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    newRecoveryKit?.let { phrase ->
        AlertDialog(
            onDismissRequest = { newRecoveryKit = null },
            modifier = Modifier.testTag("new_recovery_kit_dialog"),
            title = { Text(stringResource(R.string.settings_new_kit_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_new_kit_dialog_body),
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
                            text = phrase,
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
                        viewModel.copySecurely(recoveryKitClipLabel, phrase, true)
                        newRecoveryKit = null
                    },
                    modifier = Modifier.testTag("copy_new_recovery_kit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.settings_copy_and_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { newRecoveryKit = null }) { Text(stringResource(R.string.settings_dismiss)) }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        viewModel.clearVault()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(stringResource(R.string.settings_reset_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showRecoveryDialog) {
        val currentPhrase = remember { viewModel.recoveryPhrase }
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text(stringResource(R.string.settings_recovery_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_recovery_dialog_body),
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
                            text = currentPhrase.ifEmpty { stringResource(R.string.settings_recovery_unavailable) },
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
                            viewModel.copySecurely(recoveryKitClipLabel, currentPhrase, true)
                        }
                        showRecoveryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(stringResource(R.string.settings_copy_and_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) { Text(stringResource(R.string.settings_dismiss)) }
            }
        )
    }

    backupStatus?.let { status ->
        AlertDialog(
            onDismissRequest = { backupStatus = null },
            title = { Text(stringResource(R.string.settings_backup_dialog_title)) },
            text = {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { backupStatus = null }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }

    if (showImportDialog) {
        var backupPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.settings_import_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.settings_import_dialog_body,
                            pendingImportName ?: stringResource(R.string.settings_import_backup_file_default),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text(stringResource(R.string.settings_import_credential_label)) },
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
                    Text(stringResource(R.string.settings_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    importOutcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportOutcome() },
            title = { Text(stringResource(R.string.settings_import_complete_title)) },
            text = {
                Column {
                    Text(
                        text = if (outcome.imported + outcome.updated == 0) {
                            stringResource(R.string.settings_import_nothing_new)
                        } else {
                            stringResource(R.string.settings_import_verified)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val newEntriesLine = pluralStringResource(
                        R.plurals.settings_import_new_entries,
                        outcome.imported,
                        outcome.imported,
                    )
                    val updatedLine = pluralStringResource(
                        R.plurals.settings_import_updated,
                        outcome.updated,
                        outcome.updated,
                    )
                    val duplicatesLine = pluralStringResource(
                        R.plurals.settings_import_duplicates_skipped,
                        outcome.duplicatesSkipped,
                        outcome.duplicatesSkipped,
                    )
                    val unreadableLine = pluralStringResource(
                        R.plurals.settings_import_unreadable_skipped,
                        outcome.skippedInvalid,
                        outcome.skippedInvalid,
                    )
                    val rows = buildList {
                        if (outcome.imported > 0) add(newEntriesLine)
                        if (outcome.updated > 0) add(updatedLine)
                        if (outcome.duplicatesSkipped > 0) add(duplicatesLine)
                        if (outcome.skippedInvalid > 0) add(unreadableLine)
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
                        text = stringResource(R.string.settings_import_verified_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportOutcome() }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.settings_export_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_export_dialog_body),
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
                            text = pluralStringResource(
                                R.plurals.settings_export_preview,
                                vaultItems.size,
                                vaultItems.size,
                                summary,
                            ),
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
                            Text(stringResource(R.string.settings_export_option_standard), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_export_option_standard_desc),
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
                            Text(stringResource(R.string.settings_export_option_phrase_only), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_export_option_phrase_only_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_export_biometrics_note),
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
                            java.util.Locale.forLanguageTag(languageTag),
                        ).format(java.util.Date())
                        pendingExportPhraseOnly = exportPhraseOnly
                        showExportDialog = false
                        val suffix = if (exportPhraseOnly) "-phrase" else ""
                        BackupPickerChannel.requestExport("vaultzero-backup-$stamp$suffix.json")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(stringResource(R.string.settings_export_choose_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    importPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPartialImport() },
            title = { Text(stringResource(R.string.settings_import_prompt_title)) },
            text = {
                Column {
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_import_unreadable_prompt,
                            prompt.unreadableRecords,
                            prompt.unreadableRecords,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_import_unreadable_prompt,
                            prompt.unreadableRecords,
                            prompt.unreadableRecords,
                        ),
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
                    Text(stringResource(R.string.settings_import_readable))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPartialImport() }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showChangePasswordDialog) {
        var currentPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmPw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text(stringResource(R.string.settings_change_master_password)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_change_password_dialog_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = currentPw,
                        onValueChange = { currentPw = it },
                        label = { Text(stringResource(R.string.settings_current_master_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text(stringResource(R.string.settings_new_master_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text(stringResource(R.string.settings_confirm_new_password)) },
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
                    Text(stringResource(R.string.settings_change_password_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showSpecsDialog) {
        AlertDialog(
            onDismissRequest = { showSpecsDialog = false },
            title = { Text(stringResource(R.string.settings_specs_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_specs_dialog_body),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSpecsDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }
}

/** Localized display name for the persisted theme mode. */
@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
)

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
