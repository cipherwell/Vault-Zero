package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.core.ThemeMode
import com.example.ui.BiometricPromptRequest
import com.example.ui.BiometricPurpose
import com.example.crypto.BiometricBackupKeyManager
import com.example.crypto.BiometricKeyManager
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.backup.BackupPickerChannel
import com.example.ui.VaultLockState
import com.example.ui.VaultNavTab
import com.example.ui.VaultViewModel
import com.example.ui.components.VaultStatusPill
import com.example.ui.screens.AuditScreen
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.ItemDetailDialog
import com.example.ui.screens.ItemEditorDialog
import com.example.ui.screens.LockScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultListScreen
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.VaultZeroTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Protect all sensitive vault screens from screenshots, screen recording, and
        // Recent Apps previews. BuildConfig.ALLOW_SCREENSHOTS is false in normal builds;
        // it only becomes true for an explicit `-PallowScreenshots=true` debug build used
        // by UI automation on the emulator.
        if (!BuildConfig.ALLOW_SCREENSHOTS) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        // SAF pickers for encrypted backup export/import. Driven with explicit
        // small request codes because the pinned fragment version rejects the
        // ActivityResultRegistry's wider codes (see BackupPickerChannel).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BackupPickerChannel.requests.collect { request ->
                    val intent = when (request.kind) {
                        BackupPickerChannel.Kind.EXPORT -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(
                                Intent.EXTRA_TITLE,
                                request.suggestedName ?: "vaultzero-backup.json",
                            )
                        }
                        BackupPickerChannel.Kind.IMPORT -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf("application/json", "text/plain", "application/octet-stream"),
                            )
                        }
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        intent,
                        if (request.kind == BackupPickerChannel.Kind.EXPORT) REQUEST_EXPORT else REQUEST_IMPORT,
                    )
                }
            }
        }

        // Tapjacking / overlay protection: touches that pass through another
        // app's unobscured view on top of ours are ignored.
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Belt and braces on top of FLAG_SECURE: keep the app out of the
            // Recent Apps thumbnail pipeline entirely.
            setRecentsScreenshotEnabled(false)
        }
        enableEdgeToEdge()

        // Listen for Biometric Prompt trigger events from ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.biometricPromptEvent.collect { request ->
                    launchNativeBiometricPrompt(request)
                }
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // One UI keeps the system bar icons in sync with the app's appearance.
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(
                    this@MainActivity.window,
                    this@MainActivity.window.decorView,
                ).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            VaultZeroTheme(darkTheme = dark) {
                VaultZeroApp(viewModel = viewModel)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_EXPORT -> BackupPickerChannel.deliver(
                BackupPickerChannel.Result(BackupPickerChannel.Kind.EXPORT, data?.data),
            )
            REQUEST_IMPORT -> BackupPickerChannel.deliver(
                BackupPickerChannel.Result(BackupPickerChannel.Kind.IMPORT, data?.data),
            )
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private companion object {
        const val REQUEST_EXPORT = 5101
        const val REQUEST_IMPORT = 5102
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAutoLockTimeout()
    }

    /**
     * Show the biometric prompt with a Keystore CryptoObject for the requested
     * purpose. The key is per-use auth-gated, so the prompt is not cosmetic: the
     * cipher handed to [viewModel] only becomes usable after a successful
     * authentication of that exact cipher.
     */
    /**
     * Show the biometric prompt with the right Keystore CryptoObject:
     * - vault key (unlock/enrol)
     * - saved backup key (RESTORE_BACKUP / ENROLL_BACKUP), the "fingerprint decryption"
     *   path where the backup's key is unwrapped without typing its phrase.
     */
    private fun launchNativeBiometricPrompt(request: BiometricPromptRequest) {
        val purpose = request.purpose
        val fingerprint = request.backupFingerprint
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            viewModel.onBiometricError(
                "No fingerprint or face enrolled. Add one in system Settings, then try again."
            )
            return
        }
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.onBiometricError("Biometrics unavailable right now. Use your Master Password.")
            return
        }

        val cipher = when (purpose) {
            BiometricPurpose.UNLOCK -> BiometricKeyManager.getDecryptCipher(this)
            BiometricPurpose.ENROLL -> BiometricKeyManager.getEncryptCipher(this)
            BiometricPurpose.RESTORE_BACKUP -> {
                if (fingerprint == null) {
                    viewModel.onBiometricError("No saved backup was selected.")
                    return
                }
                BiometricBackupKeyManager.getDecryptCipher(this, fingerprint)
            }
            BiometricPurpose.ENROLL_BACKUP -> BiometricBackupKeyManager.getEncryptCipher()
        }
        if (cipher == null) {
            viewModel.onBiometricError(
                when (purpose) {
                    BiometricPurpose.UNLOCK ->
                        "Biometric key is no longer valid (re-enrolled or removed). " +
                            "Unlock with your Master Password and enable it again."
                    BiometricPurpose.ENROLL ->
                        "Could not prepare the secure biometric key. Check device security settings."
                    BiometricPurpose.RESTORE_BACKUP ->
                        "The saved backup key is no longer available. Import with your recovery phrase once, then save it again."
                    BiometricPurpose.ENROLL_BACKUP ->
                        "Could not prepare the biometric backup key. Check device security settings."
                }
            )
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(
                when (purpose) {
                    BiometricPurpose.UNLOCK -> "Unlock VaultZero"
                    BiometricPurpose.ENROLL -> "Enable biometric unlock"
                    BiometricPurpose.RESTORE_BACKUP -> "Restore saved backup"
                    BiometricPurpose.ENROLL_BACKUP -> "Save backup for fingerprint restore"
                }
            )
            .setSubtitle(
                when (purpose) {
                    BiometricPurpose.UNLOCK -> "Authenticate to unwrap your vault key"
                    BiometricPurpose.ENROLL -> "Authenticate to wrap your vault key with this device"
                    BiometricPurpose.RESTORE_BACKUP -> "Authenticate to decrypt this backup without its phrase"
                    BiometricPurpose.ENROLL_BACKUP -> "Authenticate to wrap this backup's key with this device"
                }
            )
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Cancel")
            .build()

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val authenticated = result.cryptoObject?.cipher
                    when (purpose) {
                        BiometricPurpose.UNLOCK -> viewModel.onBiometricVerified(authenticated)
                        BiometricPurpose.ENROLL -> viewModel.onBiometricEnrollmentVerified(authenticated)
                        BiometricPurpose.RESTORE_BACKUP -> {
                            if (fingerprint != null) {
                                viewModel.onBackupRestoreVerified(authenticated, fingerprint)
                            }
                        }
                        BiometricPurpose.ENROLL_BACKUP -> viewModel.onBackupEnrollVerified(authenticated)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        viewModel.onBiometricError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Not recognised; the system prompt stays open for a retry.
                }
            }
        )
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onUserInteraction()
    }
}

@Composable
fun VaultZeroApp(viewModel: VaultViewModel) {
    val lockState by viewModel.lockState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val viewingItem by viewModel.viewingItem.collectAsState()
    val editingItem by viewModel.editingItem.collectAsState()
    val editFocus by viewModel.editFocus.collectAsState()
    val clipboardStatus by viewModel.clipboardStatus.collectAsState()

    // Handle back button
    BackHandler(enabled = viewingItem != null || editingItem != null || currentTab != VaultNavTab.VAULT) {
        if (editingItem != null) {
            viewModel.dismissEditItem()
        } else if (viewingItem != null) {
            viewModel.dismissViewItem()
        } else if (currentTab != VaultNavTab.VAULT) {
            viewModel.setTab(VaultNavTab.VAULT)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = lockState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "lockStateTransition"
        ) { state ->
            if (state != VaultLockState.UNLOCKED) {
                // Surface sets LocalContentColor to onBackground; without it the
                // lock screen inherited the root default (black) text colour.
                Surface(color = MaterialTheme.colorScheme.background) {
                    LockScreen(
                        viewModel = viewModel,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    )
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        VaultBottomNavigationBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.setTab(it) }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "tabTransition"
                        ) { tab ->
                            when (tab) {
                                VaultNavTab.VAULT -> VaultListScreen(viewModel = viewModel)
                                VaultNavTab.GENERATOR -> GeneratorScreen(viewModel = viewModel)
                                VaultNavTab.AUDIT -> AuditScreen(viewModel = viewModel)
                                VaultNavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for viewing decrypted item details
        viewingItem?.let { item ->
            ItemDetailDialog(
                item = item,
                viewModel = viewModel,
                onDismiss = { viewModel.dismissViewItem() },
                onEdit = { viewModel.startEditItem(item) }
            )
        }

        // Dialog for creating or editing items
        editingItem?.let { item ->
            ItemEditorDialog(
                item = item,
                onSave = { updatedItem -> viewModel.saveItem(updatedItem) },
                onDismiss = { viewModel.dismissEditItem() },
                initialFocus = editFocus
            )
        }

        // Non-intrusive Bottom Floating Pill for Clipboard & Status Feedback
        AnimatedVisibility(
            visible = clipboardStatus != null && viewingItem == null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    bottom = if (lockState == VaultLockState.UNLOCKED) 92.dp else 28.dp,
                    start = 20.dp,
                    end = 20.dp
                )
        ) {
            clipboardStatus?.let { status ->
                VaultStatusPill(
                    status = status,
                    onDismiss = { viewModel.dismissClipboardStatus() }
                )
            }
        }
    }
}

@Composable
fun VaultBottomNavigationBar(
    currentTab: VaultNavTab,
    onTabSelected: (VaultNavTab) -> Unit
) {
    // One UI 7/8: the bottom bar floats as a rounded container instead of sitting
    // edge-to-edge on the screen bottom.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OneUiNavItem("Vault", Icons.Default.VpnKey, currentTab == VaultNavTab.VAULT, Modifier.weight(1f)) {
                onTabSelected(VaultNavTab.VAULT)
            }
            OneUiNavItem("Generator", Icons.Default.AutoAwesome, currentTab == VaultNavTab.GENERATOR, Modifier.weight(1f)) {
                onTabSelected(VaultNavTab.GENERATOR)
            }
            OneUiNavItem("Audit", Icons.Default.Shield, currentTab == VaultNavTab.AUDIT, Modifier.weight(1f)) {
                onTabSelected(VaultNavTab.AUDIT)
            }
            OneUiNavItem("Settings", Icons.Default.Settings, currentTab == VaultNavTab.SETTINGS, Modifier.weight(1f)) {
                onTabSelected(VaultNavTab.SETTINGS)
            }
        }
    }
}

@Composable
private fun OneUiNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
