package com.example.ui.screens

import com.example.R
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.i18n.UiText
import com.example.ui.i18n.asString
import com.example.ui.i18n.LocalLanguageController
import com.example.ui.i18n.LockScreenLanguageButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.example.crypto.VaultCrypto
import com.example.ui.VaultLockState
import com.example.ui.backup.BackupPickerChannel
import com.example.ui.VaultViewModel
import com.example.ui.components.StrengthMeter
import com.example.ui.components.ZeroKnowledgeSecurityBadge
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldMuted
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.min
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.VaultCard
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val lockState by viewModel.lockState.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val newRecoveryPhrase by viewModel.newRecoveryPhrase.collectAsState()
    val isAuthenticating by viewModel.isAuthenticating.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Language switch must be reachable before any vault exists so a first-time user can
        // read the setup flow in their language.
        LockScreenLanguageButton(
            controller = LocalLanguageController.current,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        when (lockState) {
            VaultLockState.NEEDS_SETUP -> {
                if (newRecoveryPhrase != null) {
                    // Resolved here: the onCopy lambda is not a composable scope.
                    val recoveryClipboardLabel =
                        stringResource(R.string.lock_recovery_phrase_clipboard)
                    RecoveryKitDisplayModal(
                        recoveryPhrase = newRecoveryPhrase!!,
                        onDismiss = { viewModel.completeSetupAndEnterVault() },
                        onCopy = {
                            viewModel.copySecurely(
                                recoveryClipboardLabel,
                                newRecoveryPhrase!!,
                                isSensitive = true,
                            )
                        }
                    )
                } else {
                    SetupMasterKeyScreen(
                        authError = authError,
                        isAuthenticating = isAuthenticating,
                        onSetup = { p, c, biometrics -> viewModel.setupMasterPassword(p, c, biometrics) },
                        onRestoreBackup = { json, phrase, newPassword, confirm ->
                            viewModel.restoreBackupAtSetup(json, phrase, newPassword, confirm)
                        },
                        onClearError = { viewModel.clearAuthError() }
                    )
                }
            }
            VaultLockState.LOCKED -> {
                UnlockVaultScreen(
                    fingerprint = viewModel.keyFingerprint,
                    authError = authError,
                    isAuthenticating = isAuthenticating,
                    isBiometricEnabled = viewModel.isBiometricEnabled,
                    onUnlock = { viewModel.unlockVault(it) },
                    onBiometric = { viewModel.requestBiometricUnlock() },
                    onClearError = { viewModel.clearAuthError() },
                    onStartRecovery = { viewModel.setLockState(VaultLockState.RECOVERING) }
                )
            }
            VaultLockState.RECOVERING -> {
                RecoverVaultScreen(
                    authError = authError,
                    isAuthenticating = isAuthenticating,
                    onRecover = { phrase, newP, confP ->
                        viewModel.recoverVault(phrase, newP, confP)
                    },
                    onClearError = { viewModel.clearAuthError() },
                    onCancel = { viewModel.setLockState(VaultLockState.LOCKED) }
                )
            }
            VaultLockState.UNLOCKED -> {
                // Handled in MainActivity
            }
        }
    }
}

@Composable
private fun SetupMasterKeyScreen(
    authError: UiText?,
    isAuthenticating: Boolean,
    onSetup: (String, String, Boolean) -> Unit,
    onRestoreBackup: (String, String, String?, String) -> Unit,
    onClearError: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var generatedPassword by remember { mutableStateOf<String?>(null) }
    var generatedCopied by remember { mutableStateOf(false) }
    var generatedSaved by remember { mutableStateOf(false) }
    var usedGeneratedPassword by remember { mutableStateOf(false) }
    var enableBiometrics by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val biometricsAvailable = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val strength = remember(password) { VaultCrypto.calculateStrength(password) }

    // Existing-phone migration: pick the encrypted backup with the system picker, then
    // collect the 12-word kit in a dialog. The kit is kept; no new phrase is generated.
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJson by remember { mutableStateOf<String?>(null) }
    var restoreFileName by remember { mutableStateOf("") }
    var restoreFileError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Resolved here because the collector below runs outside a composition.
    val readErrorText = stringResource(R.string.lock_read_error)
    LaunchedEffect(Unit) {
        BackupPickerChannel.results.collect { result ->
            if (result.kind == BackupPickerChannel.Kind.IMPORT) {
                result.uri?.let { uri ->
                    scope.launch {
                        val text = BackupFileIo.readText(context, uri)
                        if (text == null) {
                            restoreFileError = readErrorText
                        } else {
                            restoreJson = text
                            restoreFileName = BackupFileIo.displayName(context, uri)
                            restoreFileError = null
                            onClearError()
                            showRestoreDialog = true
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Vault Logo & Shield
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(CyberEmerald.copy(alpha = 0.15f))
                .border(2.dp, CyberEmerald.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = stringResource(R.string.lock_shield_desc),
                tint = CyberEmerald,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        ZeroKnowledgeSecurityBadge()

        Spacer(modifier = Modifier.height(28.dp))

        VaultCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.lock_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // The card's content colour is derived from its translucent
                    // surfaceVariant container, which dimmed the headline.
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.lock_setup_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.lock_master_password)) },
                    singleLine = true,
                    enabled = !isAuthenticating,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val generated = VaultCrypto.generatePassword(
                                        length = 20,
                                        includeUpper = true,
                                        includeLower = true,
                                        includeDigits = true,
                                        includeSymbols = true,
                                        avoidAmbiguous = true,
                                    )
                                    password = generated
                                    confirmPassword = generated
                                    isPasswordVisible = true
                                    generatedCopied = false
                                    generatedSaved = false
                                    usedGeneratedPassword = true
                                    generatedPassword = generated
                                    onClearError()
                                },
                                modifier = Modifier.testTag("generate_master_password_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.setup_generate_password),
                                )
                            }
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(
                                        if (isPasswordVisible) R.string.a11y_hide_password
                                        else R.string.a11y_show_password
                                    )
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("setup_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                generatedPassword?.let { generated ->
                    AlertDialog(
                        onDismissRequest = { generatedPassword = null },
                        title = { Text(stringResource(R.string.setup_generated_title)) },
                        text = {
                            Column {
                                Text(
                                    text = generated,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.setup_generated_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = generatedSaved,
                                        onCheckedChange = { generatedSaved = it },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.setup_generated_confirm),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val clip = ClipData.newPlainText(
                                        context.getString(R.string.lock_master_password),
                                        generated,
                                    )
                                    runCatching {
                                        clip.description.extras = PersistableBundle().apply {
                                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                                        }
                                    }
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(clip)
                                    generatedCopied = true
                                }
                            ) {
                                Icon(
                                    imageVector = if (generatedCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (generatedCopied) R.string.setup_generate_copied
                                        else R.string.setup_generate_copy
                                    )
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = generatedSaved,
                                onClick = {
                                    generatedPassword = null
                                    // Once the user confirms they saved it, stop showing it in the fields.
                                    isPasswordVisible = false
                                }
                            ) {
                                Text(stringResource(R.string.setup_generate_saved))
                            }
                        },
                    )
                }

                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    StrengthMeter(strength = strength)
                }

                if (usedGeneratedPassword) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.setup_generated_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.lock_confirm_password)) },
                    singleLine = true,
                    enabled = !isAuthenticating,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isAuthenticating && password.isNotEmpty() && confirmPassword.isNotEmpty()) {
                                onSetup(password, confirmPassword, enableBiometrics && biometricsAvailable)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("setup_confirm_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (authError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = authError.asString(),
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setup_biometric_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.setup_biometric_toggle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enableBiometrics && biometricsAvailable,
                        onCheckedChange = { enableBiometrics = it },
                        enabled = biometricsAvailable && !isAuthenticating,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                if (!biometricsAvailable) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.setup_biometric_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onSetup(password, confirmPassword, enableBiometrics && biometricsAvailable) },
                    enabled = !isAuthenticating && password.isNotEmpty() && confirmPassword.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("create_vault_button"),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberEmerald,
                        contentColor = Color.Black,
                        disabledContainerColor = CyberEmerald.copy(alpha = 0.35f),
                        disabledContentColor = CyberEmeraldMuted
                    )
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.lock_deriving),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.lock_create_vault),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        restoreFileError = null
                        onClearError()
                        BackupPickerChannel.requestImport()
                    },
                    enabled = !isAuthenticating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("restore_from_backup_button"),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        CyberCyan.copy(alpha = 0.55f),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.lock_restore_from_backup),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.lock_restore_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (restoreFileError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = restoreFileError!!,
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    val pendingJson = restoreJson
    if (showRestoreDialog && pendingJson != null) {
        RestoreBackupDialog(
            fileName = restoreFileName,
            authError = authError,
            isAuthenticating = isAuthenticating,
            onRestore = { phrase, newPassword, confirm ->
                onRestoreBackup(pendingJson, phrase, newPassword, confirm)
            },
            onCancel = {
                showRestoreDialog = false
                onClearError()
            },
            onClearError = onClearError,
        )
    }
}

@Composable
private fun RestoreBackupDialog(
    fileName: String,
    authError: UiText?,
    isAuthenticating: Boolean,
    onRestore: (String, String?, String) -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isAuthenticating) onCancel() },
        modifier = Modifier.testTag("restore_backup_dialog"),
        title = { Text(stringResource(R.string.lock_restore_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.lock_backup_file, fileName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.lock_restore_phrase_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phrase,
                    onValueChange = {
                        phrase = it
                        onClearError()
                    },
                    enabled = !isAuthenticating,
                    label = { Text(stringResource(R.string.lock_recovery_phrase_label)) },
                    placeholder = { Text(stringResource(R.string.lock_recovery_phrase_placeholder)) },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_restore_phrase_input"),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.lock_new_password_optional),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.lock_new_password_optional_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        onClearError()
                    },
                    enabled = !isAuthenticating,
                    label = { Text(stringResource(R.string.lock_new_master_password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (showPassword) R.string.a11y_hide_password
                                    else R.string.a11y_show_password
                                )
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_new_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (newPassword.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            onClearError()
                        },
                        enabled = !isAuthenticating,
                        label = { Text(stringResource(R.string.lock_confirm_new_password)) },
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_confirm_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (authError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = authError.asString(),
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRestore(
                        phrase.trim(),
                        newPassword.trim().ifBlank { null },
                        confirmPassword.trim(),
                    )
                },
                enabled = !isAuthenticating && phrase.isNotBlank(),
                modifier = Modifier.testTag("restore_vault_confirm_button"),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberEmerald,
                    contentColor = Color.Black,
                )
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.lock_restore_vault), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isAuthenticating) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun RecoveryKitDisplayModal(
    recoveryPhrase: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    val words = remember(recoveryPhrase) { recoveryPhrase.split(" ") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(WarningAmber.copy(alpha = 0.15f))
                .border(2.dp, WarningAmber.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.lock_recovery_kit_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.lock_recovery_kit_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 12-Word Grid Card
        VaultCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                CyberEmerald.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // Three columns read best on phones. Instead of wrapping/breaking a
                    // word, size the word text to the cell (accounting for the system
                    // font scale); only very small screens fall back to two columns.
                    val columns = if (maxWidth < 300.dp) 2 else 3
                    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    val spacing = 6.dp
                    val cellWidth = (maxWidth - spacing * (columns - 1)) / columns
                    val wordWidth = (cellWidth - 32.dp).coerceAtLeast(34.dp)
                    // BIP-39 English words are at most 8 characters; monospace advance ~0.62 em.
                    val wordFont = min(12f, wordWidth.value / (8f * 0.62f * fontScale))
                        .coerceAtLeast(8.5f).sp
                    val indexFont = min(10f, wordFont.value - 1f).coerceAtLeast(8f).sp
                    val rows = (words.size + columns - 1) / columns
                    Column {
                        repeat(rows) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                repeat(columns) { col ->
                                    val idx = row * columns + col
                                    if (idx < words.size) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                                .padding(horizontal = 6.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${idx + 1}.",
                                                fontSize = indexFont,
                                                fontFamily = JetBrainsMonoFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = words[idx],
                                                fontSize = wordFont,
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = CyberEmerald,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.lock_copy_phrase))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("confirm_recovery_backup_button"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberEmerald,
                contentColor = Color.Black
            )
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.lock_saved_kit),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun UnlockVaultScreen(
    fingerprint: String,
    authError: UiText?,
    isAuthenticating: Boolean,
    isBiometricEnabled: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    onClearError: () -> Unit,
    onStartRecovery: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Vault Emblem
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyberEmerald.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                .border(2.dp, CyberEmerald.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(R.string.lock_vault_locked_desc),
                tint = CyberEmerald,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Fingerprint chip
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                CyberCyan.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = fingerprint,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = CyberCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        VaultCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.lock_unlock_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.lock_master_password)) },
                    singleLine = true,
                    enabled = !isAuthenticating,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (isPasswordVisible) R.string.a11y_hide_password
                                    else R.string.a11y_show_password
                                )
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isAuthenticating && password.isNotEmpty()) {
                                onUnlock(password)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("unlock_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (authError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = authError.asString(),
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { onUnlock(password) },
                    enabled = !isAuthenticating && password.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("unlock_vault_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberEmerald,
                        contentColor = Color.Black,
                        disabledContainerColor = CyberEmerald.copy(alpha = 0.35f),
                        disabledContentColor = CyberEmeraldMuted
                    )
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.lock_decrypting), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.lock_unlock_button), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real System Biometric / Device Credential Unlock Button
                OutlinedButton(
                    onClick = onBiometric,
                    enabled = !isAuthenticating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("biometric_unlock_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isBiometricEnabled) CyberCyan.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = if (isBiometricEnabled) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isBiometricEnabled) R.string.lock_biometrics
                            else R.string.lock_biometrics_disabled
                        ),
                        color = if (isBiometricEnabled) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(
            onClick = onStartRecovery,
            modifier = Modifier.testTag("forgot_password_button")
        ) {
            Text(
                text = stringResource(R.string.lock_forgot_password),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun RecoverVaultScreen(
    authError: UiText?,
    isAuthenticating: Boolean,
    onRecover: (String, String, String) -> Unit,
    onClearError: () -> Unit,
    onCancel: () -> Unit
) {
    var phrase by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.lock_recovery_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.lock_recovery_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = phrase,
            onValueChange = {
                phrase = it
                onClearError()
            },
            enabled = !isAuthenticating,
            label = { Text("12-Word Recovery Phrase") },
            placeholder = { Text("alpha beacon cipher matrix...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                onClearError()
            },
            enabled = !isAuthenticating,
            label = { Text(stringResource(R.string.lock_new_master_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                onClearError()
            },
            enabled = !isAuthenticating,
            label = { Text(stringResource(R.string.lock_confirm_new_password_recovery)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (!isAuthenticating && phrase.isNotEmpty() && newPassword.isNotEmpty()) {
                        onRecover(phrase, newPassword, confirmPassword)
                    }
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (authError != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = authError.asString(), color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onRecover(phrase, newPassword, confirmPassword) },
            enabled = !isAuthenticating && phrase.isNotEmpty() && newPassword.isNotEmpty() && confirmPassword.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color.Black)
        ) {
            if (isAuthenticating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.lock_recovering), fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(R.string.lock_reset_unlock), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onCancel, enabled = !isAuthenticating) {
            Text(stringResource(R.string.lock_back_to_unlock), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
