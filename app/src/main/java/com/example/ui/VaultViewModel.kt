package com.example.ui

import com.example.R
import com.example.ui.i18n.UiText
import com.example.ui.i18n.messageRes
import android.app.Application
import androidx.annotation.StringRes
import com.example.BuildConfig
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.BiometricKeyManager
import com.example.core.GeneratedCredential
import com.example.core.Haptics
import com.example.core.ThemeMode
import com.example.core.GeneratorHistory
import com.example.core.AuditFilter
import com.example.core.AuditIndex
import com.example.core.SearchQuery
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import com.example.crypto.SecurityAuditEngine
import com.example.crypto.VaultCrypto
import com.example.data.BackupProtection
import com.example.data.BackupVerificationException
import com.example.data.VaultDatabase
import com.example.data.VaultError
import com.example.data.VaultPreferences
import com.example.data.VaultException
import com.example.data.VaultStateException
import com.example.data.VaultRepository
import com.example.model.ApiSecretPayload
import com.example.model.AuditReport
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.ItemPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VaultLockState {
    NEEDS_SETUP,
    LOCKED,
    UNLOCKED,
    RECOVERING
}

/** Launcher shortcut intents. */
enum class ShortcutAction { NEW_LOGIN, GENERATOR }

/** What a biometric prompt is being shown for. */
enum class BiometricPurpose { UNLOCK, ENROLL }

/** Which editor field an external action (e.g. the audit "Fix" button) should focus. */
enum class EditFocus { PASSWORD, TOTP, CARD_NUMBER, NOTE_CONTENT, SEED_PHRASE, API_KEY, ID_NUMBER }

/** Shown when a backup has unreadable records and the user may import the rest. */
data class ImportVerificationPrompt(
    val unreadableRecords: Int,
    val message: String,
)

enum class VaultSortOption(val label: String) {
    FAVORITES_FIRST("Favorites first"),
    TITLE("Title A–Z"),
    RECENT("Recently updated"),
    RECENTLY_USED("Recently used"),
}

/** Recently deleted records kept in memory for a short undo window. */
data class DeletedBatch(val items: List<VaultItem>, val label: String)

/** Summary of a verified import, shown in a result dialog. */
data class ImportOutcome(
    val imported: Int,
    val updated: Int,
    val duplicatesSkipped: Int,
    val skippedInvalid: Int,
    val summary: String,
)

enum class VaultNavTab {
    VAULT,
    GENERATOR,
    AUDIT,
    SETTINGS
}

data class GeneratorConfig(
    val length: Int = 18,
    val includeUpper: Boolean = true,
    val includeLower: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
    val isPassphraseMode: Boolean = false,
    val passphraseWords: Int = 4,
    val passphraseSeparator: String = "-",
    val passphraseCapitalise: Boolean = false
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VaultRepository
    private val clipboardManager =
        application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val _lockState = MutableStateFlow(VaultLockState.LOCKED)
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    private val _currentTab = MutableStateFlow(VaultNavTab.VAULT)
    val currentTab: StateFlow<VaultNavTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<VaultType?>(null)
    val selectedTypeFilter: StateFlow<VaultType?> = _selectedTypeFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(VaultSortOption.FAVORITES_FIRST)
    private val _groupByType = MutableStateFlow(false)
    val groupByType: StateFlow<Boolean> = _groupByType.asStateFlow()

    private val _selectionActive = MutableStateFlow(false)
    val selectionActive: StateFlow<Boolean> = _selectionActive.asStateFlow()
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _editFocus = MutableStateFlow<EditFocus?>(null)
    val editFocus: StateFlow<EditFocus?> = _editFocus.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _importVerificationPrompt = MutableStateFlow<ImportVerificationPrompt?>(null)
    val importVerificationPrompt: StateFlow<ImportVerificationPrompt?> =
        _importVerificationPrompt.asStateFlow()
    private var pendingPartialImportJson: String? = null
    private var pendingPartialImportPassword: String? = null

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val generatorHistoryStore = GeneratorHistory()
    private val _generatorHistory = MutableStateFlow<List<GeneratedCredential>>(emptyList())
    val generatorHistory: StateFlow<List<GeneratedCredential>> = _generatorHistory.asStateFlow()
    private val _favoritesOnly = MutableStateFlow(false)
    val sortOption: StateFlow<VaultSortOption> = _sortOption.asStateFlow()
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _authError = MutableStateFlow<UiText?>(null)
    val authError: StateFlow<UiText?> = _authError.asStateFlow()

    private val _viewingItem = MutableStateFlow<VaultItem?>(null)
    val viewingItem: StateFlow<VaultItem?> = _viewingItem.asStateFlow()

    private val _editingItem = MutableStateFlow<VaultItem?>(null)
    val editingItem: StateFlow<VaultItem?> = _editingItem.asStateFlow()

    private val _newRecoveryPhrase = MutableStateFlow<String?>(null)
    val newRecoveryPhrase: StateFlow<String?> = _newRecoveryPhrase.asStateFlow()

    /** Created vault exists, but the user has not confirmed saving the Recovery Kit yet. */
    private val _setupIncomplete = MutableStateFlow(false)
    val setupIncomplete: StateFlow<Boolean> = _setupIncomplete.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _clipboardStatus = MutableStateFlow<UiText?>(null)
    val clipboardStatus: StateFlow<UiText?> = _clipboardStatus.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _biometricPromptEvent = MutableSharedFlow<BiometricPurpose>(extraBufferCapacity = 1)
    val biometricPromptEvent: SharedFlow<BiometricPurpose> = _biometricPromptEvent

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _autoLockSeconds = MutableStateFlow(60)
    val autoLockSeconds: StateFlow<Int> = _autoLockSeconds.asStateFlow()

    private val _clipboardTimeoutSeconds = MutableStateFlow(30)
    val clipboardTimeoutSeconds: StateFlow<Int> = _clipboardTimeoutSeconds.asStateFlow()

    private val _generatorConfig = MutableStateFlow(GeneratorConfig())
    val generatorConfig: StateFlow<GeneratorConfig> = _generatorConfig.asStateFlow()

    private val _generatedPassword = MutableStateFlow("")
    val generatedPassword: StateFlow<String> = _generatedPassword.asStateFlow()

    private val _passwordStrength = MutableStateFlow(
        VaultCrypto.calculateStrength("")
    )
    val passwordStrength: StateFlow<VaultCrypto.PasswordStrength> = _passwordStrength.asStateFlow()

    // Real-time second clock for live TOTP animations
    private val _recentlyDeleted = MutableStateFlow<DeletedBatch?>(null)
    val recentlyDeleted: StateFlow<DeletedBatch?> = _recentlyDeleted.asStateFlow()
    private var deletedExpiryJob: Job? = null

    private val _importOutcome = MutableStateFlow<ImportOutcome?>(null)
    val importOutcome: StateFlow<ImportOutcome?> = _importOutcome.asStateFlow()

    private val _backgroundLockSeconds = MutableStateFlow(30)
    val backgroundLockSeconds: StateFlow<Int> = _backgroundLockSeconds.asStateFlow()

    private val _clockTicker = MutableStateFlow(System.currentTimeMillis())
    val clockTicker: StateFlow<Long> = _clockTicker.asStateFlow()

    private var pendingBiometricEnrollment = false
    private var clipboardJob: Job? = null
    private var autoLockJob: Job? = null
    private var statusDismissJob: Job? = null

    @Volatile
    private var lastInteractionRealtime: Long = SystemClock.elapsedRealtime()

    @Volatile
    private var lastCopiedSensitiveValue: String? = null


    /**
     * Localized text for a failed operation: a typed [VaultException] carries a code the UI can
     * translate, everything else falls back to the (English) platform/exception message under a
     * localized prefix.
     */
    private fun failureText(@StringRes prefix: Int, error: Throwable?): UiText {
        when (error) {
            is VaultException -> return UiText.Res(error.error.messageRes(), error.args)
            is VaultStateException -> return UiText.Res(error.error.messageRes(), error.args)
            else -> Unit
        }
        val detail = error?.message
        return if (detail.isNullOrBlank()) UiText.Res(prefix, "") else UiText.Res(prefix, detail)
    }

    private fun postStatusMessage(message: UiText, durationMs: Long = 2200L) {
        _clipboardStatus.value = message
        statusDismissJob?.cancel()
        statusDismissJob = viewModelScope.launch {
            delay(durationMs)
            if (_clipboardStatus.value == message) {
                _clipboardStatus.value = null
            }
        }
    }

    init {
        val database = VaultDatabase.getInstance(application)
        val prefs = VaultPreferences(application)
        repository = VaultRepository(database.vaultDao(), prefs)

        if (!repository.isInitialized) {
            _lockState.value = VaultLockState.NEEDS_SETUP
        } else {
            _lockState.value = VaultLockState.LOCKED
        }
        _setupIncomplete.value = repository.isSetupKitPending
        _hapticsEnabled.value = repository.hapticsEnabled
        Haptics.enabled = repository.hapticsEnabled

        _themeMode.value = ThemeMode.fromStorage(repository.themeMode())
        // A legacy (non-auth-gated) wrap or a key invalidated by re-enrolment
        // cannot be used: require the user to enable biometric unlock again.
        if (repository.isBiometricEnabled() && !BiometricKeyManager.hasCurrentEnrollment(application)) {
            repository.setBiometricEnabled(false)
        }
        _biometricEnabled.value = repository.isBiometricEnabled()
        _autoLockSeconds.value = repository.autoLockSeconds
        _clipboardTimeoutSeconds.value = repository.clipboardTimeoutSeconds
        _backgroundLockSeconds.value = repository.backgroundLockSeconds
        _sortOption.value = VaultSortOption.entries
            .firstOrNull { it.name == repository.sortOption } ?: VaultSortOption.FAVORITES_FIRST
        _groupByType.value = repository.groupByType
        regeneratePassword()
        startClockTicker()
    }

    val keyFingerprint: String
        get() = repository.keyFingerprint

    val recoveryPhrase: String
        get() = repository.getDecryptedRecoveryPhrase()

    val isDarkTheme: Boolean
        get() = repository.isDarkTheme()

    val isBiometricEnabled: Boolean
        get() = repository.isBiometricEnabled()

    val allItems: StateFlow<List<VaultItem>> = repository.getVaultItemsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Parse the query once per debounce window instead of per keystroke. */
    @OptIn(FlowPreview::class)
    private val searchSpecFlow = _searchQuery
        .debounce(140)
        .map { SearchQuery.parse(it) }

    @OptIn(FlowPreview::class)
    val filteredItems: StateFlow<List<VaultItem>> = combine(
        allItems,
        searchSpecFlow,
        _selectedTypeFilter,
        _favoritesOnly,
        _sortOption,
    ) { items, spec, typeFilter, favsOnly, sortOption ->
        // Audit-backed filters need a report; compute it only when used so the
        // normal list path stays free of per-tick audit work.
        val auditIndex = if (spec.audit.isEmpty()) {
            null
        } else {
            AuditIndex.from(SecurityAuditEngine.analyzeVault(items))
        }
        items.filter { item ->
            SearchQuery.matches(item, spec) &&
                (auditIndex == null || auditIndex.matches(item.id, spec.audit)) &&
                (typeFilter == null || item.type == typeFilter) &&
                (!favsOnly || item.isFavorite)
        }.let { filtered ->
            when (sortOption) {
                VaultSortOption.FAVORITES_FIRST -> filtered.sortedWith(
                    compareByDescending<VaultItem> { it.isFavorite }.thenBy { it.title.lowercase() },
                )
                VaultSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
                VaultSortOption.RECENT -> filtered.sortedByDescending { it.updatedAt }
                VaultSortOption.RECENTLY_USED -> filtered.sortedWith(
                    compareByDescending<VaultItem> { it.lastUsedAt }.thenByDescending { it.updatedAt },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditReport: StateFlow<AuditReport> = allItems.combine(_clockTicker) { items, now ->
        SecurityAuditEngine.analyzeVault(items, now)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AuditReport(100, "A+", 0, 0, 0, 0, 0, emptyList())
    )

    private fun startClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _clockTicker.value = System.currentTimeMillis()
                checkAutoLockTimeout()
            }
        }
    }

    /**
     * Dev builds can opt out of auto-lock (`-PdevDisableAutoLock=true`); the
     * stored user preference still wins for every normal build, and 0 keeps
     * meaning "never lock".
     */
    private fun effectiveAutoLockSeconds(): Int =
        if (BuildConfig.DEV_DISABLE_AUTO_LOCK) 0 else repository.autoLockSeconds

    private fun resetAutoLockTimer() {
        lastInteractionRealtime = SystemClock.elapsedRealtime()
        autoLockJob?.cancel()
        val seconds = effectiveAutoLockSeconds()
        if (seconds > 0 && _lockState.value == VaultLockState.UNLOCKED) {
            autoLockJob = viewModelScope.launch {
                delay(seconds * 1000L)
                lockVault(autoLocked = true)
            }
        }
    }

    /**
     * Checks monotonic elapsed time since last user interaction and locks vault if timeout elapsed.
     * Safe against wall-clock tampering and device sleep/background pauses.
     */
    fun checkAutoLockTimeout(nowRealtime: Long = SystemClock.elapsedRealtime()) {
        if (_lockState.value == VaultLockState.UNLOCKED) {
            val seconds = effectiveAutoLockSeconds()
            if (seconds > 0 && (nowRealtime - lastInteractionRealtime) >= seconds * 1000L) {
                lockVault(autoLocked = true)
            }
        }
    }

    fun onUserInteraction() {
        if (_lockState.value == VaultLockState.UNLOCKED) {
            resetAutoLockTimer()
        }
    }

    // -------------------------------------------------------------------------
    // Auth & Lock Operations
    // -------------------------------------------------------------------------

    fun clearAuthError() {
        _authError.value = null
    }

    fun setHapticsEnabled(enabled: Boolean) {
        if (enabled) {
            Haptics.enabled = true
            Haptics.tick(getApplication())
        } else {
            // Give the tap that turns it off one last subtle confirmation.
            Haptics.tick(getApplication())
            Haptics.enabled = false
        }
        repository.hapticsEnabled = enabled
        _hapticsEnabled.value = enabled
    }

    fun setupMasterPassword(
        password: String,
        confirm: String,
        enableBiometricsAfterSetup: Boolean = false,
    ) {
        val cleanPassword = password.trim()
        val cleanConfirm = confirm.trim()
        if (cleanPassword.length < 8) {
            _authError.value = UiText.Res(R.string.err_password_too_short)
            return
        }
        if (cleanPassword != cleanConfirm) {
            _authError.value = UiText.Res(R.string.msg_passwords_do_not_match)
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val (phrase, vmkHex) = repository.setupMasterPassword(cleanPassword)

                pendingBiometricEnrollment = enableBiometricsAfterSetup
                _setupIncomplete.value = true
                _newRecoveryPhrase.value = phrase
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = failureText(R.string.msg_setup_failed, e)
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun completeSetupAndEnterVault() {
        Haptics.success(getApplication())
        repository.markSetupKitConfirmed()
        _setupIncomplete.value = false
        _newRecoveryPhrase.value = null
        _lockState.value = VaultLockState.UNLOCKED
        _authError.value = null
        resetAutoLockTimer()
        if (pendingBiometricEnrollment) {
            pendingBiometricEnrollment = false
            requestBiometricEnrollment()
        }
    }

    /**
     * Enter the vault, unless setup was interrupted before the Recovery Kit was confirmed.
     * In that case the kit is decrypted with the now-unlocked VMK and shown again, so the
     * user cannot end up with a vault whose only kit was lost with the process.
     */
    private fun enterAfterUnlock() {
        Haptics.success(getApplication())
        if (repository.isSetupKitPending) {
            val phrase = repository.getDecryptedRecoveryPhrase()
            if (phrase.isNotBlank()) {
                _newRecoveryPhrase.value = phrase
                _setupIncomplete.value = true
                _lockState.value = VaultLockState.NEEDS_SETUP
                _authError.value = null
                return
            }
        }
        _lockState.value = VaultLockState.UNLOCKED
        _authError.value = null
        resetAutoLockTimer()
    }

    /**
     * First-run migration: restore a complete backup using the existing 12-word recovery
     * phrase. The recovery kit is preserved (no new phrase is generated). For standard
     * backups the original master password keeps working unless [newPassword] is supplied;
     * phrase-only backups require a new password because they carry no password envelope.
     */
    fun restoreBackupAtSetup(
        json: String,
        recoveryPhrase: String,
        newPassword: String?,
        confirmPassword: String,
    ) {
        if (recoveryPhrase.trim().isEmpty()) {
            _authError.value = UiText.Res(R.string.msg_enter_backup_phrase)
            return
        }
        val cleanNewPassword = newPassword?.trim().orEmpty()
        if (cleanNewPassword.isNotEmpty()) {
            if (cleanNewPassword.length < 8) {
                _authError.value = UiText.Res(R.string.err_new_password_too_short)
                return
            }
            if (cleanNewPassword != confirmPassword.trim()) {
                _authError.value = UiText.Res(R.string.msg_new_master_passwords_mismatch)
                return
            }
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val report = repository.restoreBackupAtSetup(
                    jsonString = json,
                    recoveryPhrase = recoveryPhrase,
                    newMasterPassword = cleanNewPassword.ifBlank { null },
                )
                _newRecoveryPhrase.value = null
                _lockState.value = VaultLockState.UNLOCKED
                _authError.value = null
                resetAutoLockTimer()
                postStatusMessage(
                    UiText.Plural(R.plurals.msg_vault_restored, report.itemCount),
                    durationMs = 6000L,
                )
            } catch (e: IllegalArgumentException) {
                _authError.value = failureText(R.string.msg_restore_failed, e)
            } catch (e: Exception) {
                _authError.value = failureText(R.string.msg_restore_failed_invalid, e)
            } finally {
                _isAuthenticating.value = false
            }
        }
    }


    fun unlockVault(password: String) {
        if (password.isBlank()) {
            _authError.value = UiText.Res(R.string.msg_enter_master_password)
            return
        }
        val lockoutSec = repository.getRemainingLockoutSeconds()
        if (lockoutSec > 0) {
            _authError.value = UiText.Res(R.string.msg_too_many_attempts, lockoutSec)
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val success = repository.unlock(password)
                if (success) {
                    enterAfterUnlock()
                } else {
                    val newLockoutSec = repository.getRemainingLockoutSeconds()
                    if (newLockoutSec > 0) {
                        _authError.value = UiText.Res(R.string.msg_locked_out, newLockoutSec)
                    } else {
                        _authError.value = UiText.Res(R.string.msg_incorrect_password)
                    }
                }
            } catch (e: Exception) {
                _authError.value = failureText(R.string.msg_auth_error, e)
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun requestBiometricUnlock() {
        if (!repository.isBiometricEnabled() || !_biometricEnabled.value) {
            _authError.value = UiText.Res(R.string.msg_biometric_disabled)
            return
        }
        if (!BiometricKeyManager.hasCurrentEnrollment(getApplication())) {
            _authError.value = UiText.Res(R.string.msg_biometric_enrolment_missing)
            return
        }
        _authError.value = null
        _biometricPromptEvent.tryEmit(BiometricPurpose.UNLOCK)
    }

    fun onBiometricVerified(authenticatedCipher: javax.crypto.Cipher? = null) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                if (authenticatedCipher == null) {
                    _authError.value = UiText.Res(R.string.msg_biometric_not_authorised)
                    return@launch
                }
                val vmkHex = BiometricKeyManager.decryptVmkWithCipher(getApplication(), authenticatedCipher)
                if (vmkHex != null) {
                    val success = repository.unlockWithVmkHex(vmkHex)
                    if (success) {
                        enterAfterUnlock()
                        return@launch
                    }
                }
                _authError.value = UiText.Res(R.string.msg_biometric_failed)
            } catch (e: Exception) {
                _authError.value = failureText(R.string.msg_biometric_error, e)
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun onBiometricError(message: UiText) {
        _authError.value = message
    }

    /** True when the lock screen should offer an automatic fingerprint prompt. */
    fun isAutoBiometricUnlockAvailable(): Boolean =
        _lockState.value == VaultLockState.LOCKED &&
            repository.isBiometricEnabled() &&
            BiometricKeyManager.hasCurrentEnrollment(getApplication())

    fun recoverVault(phrase: String, newPass: String, confirmPass: String) {
        val cleanPhrase = VaultCrypto.normalizeRecoveryPhrase(phrase)
        val cleanNewPass = newPass.trim()
        val cleanConfirm = confirmPass.trim()
        if (!VaultCrypto.validateRecoveryPhrase(cleanPhrase, enforceChecksum = false)) {
            _authError.value = UiText.Res(R.string.msg_recovery_phrase_invalid)
            return
        }
        if (!VaultCrypto.verifyRecoveryPhraseChecksum(cleanPhrase)) {
            _authError.value = UiText.Res(R.string.msg_recovery_checksum)
            return
        }
        if (cleanNewPass.length < 8) {
            _authError.value = UiText.Res(R.string.err_new_password_too_short)
            return
        }
        if (cleanNewPass != cleanConfirm) {
            _authError.value = UiText.Res(R.string.msg_passwords_do_not_match)
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val success = repository.recoverWithPhrase(cleanPhrase, cleanNewPass)
                if (success) {
                    repository.markSetupKitConfirmed()
                    _setupIncomplete.value = false
                    _lockState.value = VaultLockState.UNLOCKED
                    _authError.value = null
                    resetAutoLockTimer()
                } else {
                    _authError.value = UiText.Res(R.string.err_recovery_phrase_mismatch)
                }
            } catch (e: Exception) {
                _authError.value = failureText(R.string.msg_recovery_error, e)
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun setLockState(state: VaultLockState) {
        _lockState.value = state
        _authError.value = null
    }

    fun lockVault(autoLocked: Boolean = false) {
        // A stale auto-lock job must never lock the setup wizard or the recovery-kit screen.
        if (autoLocked && _lockState.value != VaultLockState.UNLOCKED) return
        repository.lock()
        clearSelection()
        generatorHistoryStore.clear()
        _generatorHistory.value = emptyList()
        _editFocus.value = null
        _lockState.value = VaultLockState.LOCKED
        _viewingItem.value = null
        _editingItem.value = null
        _newRecoveryPhrase.value = null
        autoLockJob?.cancel()
        // Both clipboard modes clear on lock: timed mode also cancels its pending
        // timeout job, Off mode relies on this ownership-checked wipe.
        clipboardJob?.cancel()
        clearSensitiveClipboardIfOwned()
        _recentlyDeleted.value = null
        deletedExpiryJob?.cancel()
        if (autoLocked) {
            postStatusMessage(UiText.Res(R.string.msg_auto_locked))
        } else {
            dismissClipboardStatus()
        }
    }

    fun dismissRecoveryPhraseModal() {
        _newRecoveryPhrase.value = null
    }

    // -------------------------------------------------------------------------
    // Navigation & Filtering
    // -------------------------------------------------------------------------

    /** Applied immediately; if the vault is locked the action lands after unlock. */
    fun handleShortcut(action: ShortcutAction) {
        when (action) {
            ShortcutAction.GENERATOR -> setTab(VaultNavTab.GENERATOR)
            ShortcutAction.NEW_LOGIN -> {
                setTab(VaultNavTab.VAULT)
                startCreateItem(VaultType.LOGIN)
            }
        }
    }

    fun setTab(tab: VaultNavTab) {
        _currentTab.value = tab
        onUserInteraction()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        clearSelection()
        onUserInteraction()
    }

    fun setTypeFilter(type: VaultType?) {
        _selectedTypeFilter.value = type
        // Category and favorites are mutually exclusive views. Leaving both on
        // ANDs them and shows an empty screen, which reads as a broken filter.
        if (type != null) _favoritesOnly.value = false
        clearSelection()
        onUserInteraction()
    }

    fun toggleFavoritesFilter() {
        val next = !_favoritesOnly.value
        _favoritesOnly.value = next
        if (next) _selectedTypeFilter.value = null
        clearSelection()
        onUserInteraction()
    }

    fun setSortOption(option: VaultSortOption) {
        if (_sortOption.value == option) return
        _sortOption.value = option
        repository.setSortOption(option.name)
        onUserInteraction()
    }

    fun setGroupByType(enabled: Boolean) {
        if (_groupByType.value == enabled) return
        _groupByType.value = enabled
        repository.setGroupByType(enabled)
        onUserInteraction()
    }

    // -------------------------------------------------------------------------
    // Multi-select
    // -------------------------------------------------------------------------

    fun enterSelectionMode(initialId: String? = null) {
        _selectionActive.value = true
        if (initialId != null) _selectedIds.value = setOf(initialId)
        onUserInteraction()
    }

    fun toggleSelection(id: String) {
        _selectedIds.value = if (id in _selectedIds.value) _selectedIds.value - id else _selectedIds.value + id
        if (_selectedIds.value.isEmpty() && _selectionActive.value) {
            // Keep the mode open so the user can keep picking; close on demand.
        }
        onUserInteraction()
    }

    fun clearSelection() {
        _selectionActive.value = false
        _selectedIds.value = emptySet()
    }

    fun deleteSelectedItems() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        val items = allItems.value.filter { it.id in ids }
        rememberDeleted(items, "Deleted ${items.size} ${if (items.size == 1) "item" else "items"}")
        viewModelScope.launch {
            try {
                repository.deleteItems(ids)
                clearSelection()
            } catch (e: Exception) {
                _recentlyDeleted.value = null
                postStatusMessage(failureText(R.string.msg_delete_failed, e))
            }
        }
    }

    private fun rememberDeleted(items: List<VaultItem>, label: String) {
        if (items.isEmpty()) return
        _recentlyDeleted.value = DeletedBatch(items, label)
        deletedExpiryJob?.cancel()
        deletedExpiryJob = viewModelScope.launch {
            delay(10_000)
            _recentlyDeleted.value = null
        }
    }

    fun undoDelete() {
        val batch = _recentlyDeleted.value ?: return
        _recentlyDeleted.value = null
        deletedExpiryJob?.cancel()
        viewModelScope.launch {
            try {
                batch.items.forEach { repository.saveItem(it) }
                postStatusMessage(UiText.Plural(R.plurals.msg_items_restored, batch.items.size))
            } catch (e: Exception) {
                postStatusMessage(failureText(R.string.msg_could_not_restore, e))
            }
        }
    }

    fun dismissDeletedBatch() {
        _recentlyDeleted.value = null
        deletedExpiryJob?.cancel()
    }

    /** Favourite (or un-favourite) every selected record. */
    fun setSelectedFavorite(favorite: Boolean) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.setFavorite(ids, favorite)
                postStatusMessage(
                    if (favorite) UiText.Res(R.string.msg_marked_favorite, ids.size)
                    else UiText.Res(R.string.msg_unmarked_favorite, ids.size)
                )
            } catch (e: Exception) {
                postStatusMessage(failureText(R.string.msg_could_not_favorite, e))
            }
        }
    }

    fun updateBackgroundLock(seconds: Int) {
        if (_backgroundLockSeconds.value == seconds) return
        _backgroundLockSeconds.value = seconds
        repository.setBackgroundLockSeconds(seconds)
    }

    fun clearFilters() {
        _selectedTypeFilter.value = null
        _favoritesOnly.value = false
        onUserInteraction()
    }

    // -------------------------------------------------------------------------
    // Item CRUD
    // -------------------------------------------------------------------------

    fun viewItem(item: VaultItem) {
        if (_lockState.value != VaultLockState.UNLOCKED) return
        _viewingItem.value = item
        onUserInteraction()
    }

    fun dismissViewItem() {
        _viewingItem.value = null
        onUserInteraction()
    }

    fun startCreateItem(type: VaultType = VaultType.LOGIN, prefilledPassword: String = "") {
        if (_lockState.value != VaultLockState.UNLOCKED) return
        val blankPayload: ItemPayload = when (type) {
            VaultType.LOGIN -> LoginPayload(password = prefilledPassword)
            VaultType.CARD -> CardPayload()
            VaultType.API_SECRET -> ApiSecretPayload(secretToken = prefilledPassword)
            VaultType.CRYPTO_WALLET -> CryptoPayload()
            VaultType.NOTE -> NotePayload()
            VaultType.IDENTITY -> IdentityPayload()
        }
        _editingItem.value = VaultItem(
            id = "",
            title = "",
            type = type,
            payload = blankPayload
        )
        onUserInteraction()
    }

    fun saveGeneratedPasswordToVault() {
        val currentGenerated = _generatedPassword.value
        startCreateItem(type = VaultType.LOGIN, prefilledPassword = currentGenerated)
    }

    fun startEditItem(item: VaultItem, focus: EditFocus? = null) {
        _editFocus.value = focus
        if (_lockState.value != VaultLockState.UNLOCKED) return
        _viewingItem.value = null
        _editingItem.value = item
        onUserInteraction()
    }

    fun dismissEditItem() {
        _editFocus.value = null
        _editingItem.value = null
        onUserInteraction()
    }

    fun saveItem(item: VaultItem) {
        viewModelScope.launch {
            try {
                repository.saveItem(item)
                _editingItem.value = null
                _viewingItem.value = null
                postStatusMessage(UiText.Res(R.string.msg_saved_item, item.title))
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage(failureText(R.string.msg_save_failed, e))
            }
        }
    }

    fun toggleFavorite(item: VaultItem) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(item.id, !item.isFavorite)
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage(UiText.Res(R.string.msg_cannot_favorite_locked))
            }
        }
    }

    fun deleteItem(item: VaultItem) {
        rememberDeleted(listOf(item), "Deleted '${item.title}'")
        viewModelScope.launch {
            try {
                repository.deleteItem(item.id)
                _viewingItem.value = null
                _editingItem.value = null
                postStatusMessage(UiText.Res(R.string.msg_deleted_item, item.title))
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage(failureText(R.string.msg_delete_failed, e))
            }
        }
    }

    fun clearVault() {
        autoLockJob?.cancel()
        pendingBiometricEnrollment = false
        _setupIncomplete.value = false
        _newRecoveryPhrase.value = null
        _hapticsEnabled.value = true
        Haptics.enabled = true
        viewModelScope.launch {
            BiometricKeyManager.clearBiometricKey(getApplication())
            clearSensitiveClipboardIfOwned()
            repository.clearVault()
            _lockState.value = VaultLockState.NEEDS_SETUP
            _viewingItem.value = null
            _editingItem.value = null
            _newRecoveryPhrase.value = null
            postStatusMessage(UiText.Res(R.string.msg_vault_reset))
        }
    }

    // -------------------------------------------------------------------------
    // Encrypted Backup Export & Import
    // -------------------------------------------------------------------------

    fun exportBackupJson(
        protection: BackupProtection = BackupProtection.STANDARD,
        onResult: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val json = repository.exportEncryptedBackup(protection)
                onResult(json)
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage(failureText(R.string.msg_export_failed, e))
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun importBackupJson(
        json: String,
        backupPassword: String? = null,
        allowPartial: Boolean = false,
    ) {
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val report = repository.importEncryptedBackup(
                    jsonString = json,
                    backupPassword = backupPassword,
                    skipInvalidRecords = allowPartial,
                )
                val parts = mutableListOf<String>()
                if (report.imported > 0) {
                    parts.add(
                        "Restored ${report.imported} ${if (report.imported == 1) "entry" else "entries"}"
                    )
                }
                if (report.updated > 0) {
                    parts.add(
                        "updated ${report.updated} ${if (report.updated == 1) "entry" else "entries"}"
                    )
                }
                if (report.duplicatesSkipped > 0) {
                    parts.add(
                        "${report.duplicatesSkipped} duplicate" +
                            "${if (report.duplicatesSkipped == 1) "" else "s"} skipped"
                    )
                }
                if (report.skippedInvalid > 0) {
                    parts.add(
                        "${report.skippedInvalid} unreadable " +
                            (if (report.skippedInvalid == 1) "record skipped" else "records skipped")
                    )
                }
                val msg = when {
                    parts.isEmpty() -> "Backup verified · nothing new to import"
                    report.totalNew == 0 -> "Backup verified · " + parts.joinToString(" · ")
                    else -> parts.joinToString(" · ")
                }
                pendingPartialImportJson = null
                pendingPartialImportPassword = null
                _importVerificationPrompt.value = null
                _importOutcome.value = ImportOutcome(
                    imported = report.imported,
                    updated = report.updated,
                    duplicatesSkipped = report.duplicatesSkipped,
                    skippedInvalid = report.skippedInvalid,
                    summary = msg,
                )
                postStatusMessage(
                    if (report.totalNew == 0) UiText.Res(R.string.msg_import_none)
                    else UiText.Res(R.string.msg_import_done, report.imported, report.updated)
                )
                onUserInteraction()
            } catch (prompt: BackupVerificationException) {
                if (allowPartial) {
                    // Should not happen (partial mode no longer throws), but never
                    // leave the UI without feedback.
                    postStatusMessage(UiText.Res(R.string.msg_import_failed, prompt.message.orEmpty()))
                } else {
                    pendingPartialImportJson = json
                    pendingPartialImportPassword = backupPassword
                    _importVerificationPrompt.value = ImportVerificationPrompt(
                        unreadableRecords = prompt.unreadableRecords,
                        message = prompt.message ?: "Some records could not be verified.",
                    )
                }
            } catch (e: IllegalArgumentException) {
                postStatusMessage(failureText(R.string.msg_import_rejected, e))
            } catch (e: Exception) {
                postStatusMessage(failureText(R.string.msg_import_failed_invalid, e))
            } finally {
                _backupBusy.value = false
            }
        }
    }

    /** User accepted importing only the readable records of a partially bad backup. */
    fun confirmPartialImport() {
        val json = pendingPartialImportJson ?: return
        val password = pendingPartialImportPassword
        _importVerificationPrompt.value = null
        importBackupJson(json, password, allowPartial = true)
    }

    fun cancelPartialImport() {
        pendingPartialImportJson = null
        pendingPartialImportPassword = null
        _importVerificationPrompt.value = null
    }

    fun dismissImportOutcome() {
        _importOutcome.value = null
    }

    /**
     * Rotate the master password: verifies the current one, re-wraps the VMK with
     * a fresh salt/KEK at 600k iterations, and leaves records and the recovery kit
     * untouched.
     */
    fun changeMasterPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (Boolean) -> Unit,
    ) {
        if (newPassword.length < 8) {
            postStatusMessage(UiText.Res(R.string.err_new_password_too_short))
            onResult(false)
            return
        }
        if (newPassword != confirmPassword) {
            postStatusMessage(UiText.Res(R.string.msg_new_passwords_mismatch))
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                repository.changeMasterPassword(currentPassword, newPassword)
                postStatusMessage(UiText.Res(R.string.msg_password_changed))
                onResult(true)
            } catch (e: Exception) {
                postStatusMessage(failureText(R.string.msg_password_change_failed, e))
                onResult(false)
            }
        }
    }

    /**
     * Replaces the recovery kit with a new BIP-39 128-bit phrase while keeping the vault key,
     * records and master password. The new phrase is returned once for the user to save.
     */
    fun regenerateRecoveryKit(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val phrase = repository.regenerateRecoveryKit()
                onResult(phrase)
                postStatusMessage(UiText.Res(R.string.msg_kit_generated), durationMs = 6000L)
                onUserInteraction()
            } catch (e: Exception) {
                onResult(null)
                postStatusMessage(failureText(R.string.msg_kit_failed, e))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Secure Clipboard with Ownership-Aware Auto-Clear & Sensitive Flag
    // -------------------------------------------------------------------------

    fun copySecurely(
        label: String,
        value: String,
        isSensitive: Boolean = true,
        itemId: String? = null,
    ) {
        if (value.isEmpty()) return
        if (itemId != null) {
            viewModelScope.launch { repository.markUsed(itemId) }
        }
        val clip = ClipData.newPlainText(label, value)
        if (isSensitive) {
            try {
                val extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                clip.description.extras = extras
            } catch (e: Exception) {
                // Ignore on older unit test mocks where PersistableBundle is stubbed
            }
        }
        clipboardManager.setPrimaryClip(clip)

        val timeout = repository.clipboardTimeoutSeconds
        val statusMsg: UiText = when {
            !isSensitive -> UiText.Res(R.string.msg_clipboard_copied, label)
            timeout > 0 -> UiText.Res(R.string.msg_clipboard_auto_clear, label, timeout)
            else -> UiText.Res(R.string.msg_clipboard_clears_on_lock, label)
        }
        postStatusMessage(statusMsg)
        onUserInteraction()

        clipboardJob?.cancel()
        if (isSensitive) {
            lastCopiedSensitiveValue = value
            if (timeout > 0) {
                clipboardJob = viewModelScope.launch {
                    delay(timeout * 1000L)
                    clearSensitiveClipboardIfOwned()
                }
            }
        }
    }

    /**
     * Clears the clipboard ONLY if it still contains the sensitive value placed by VaultZero,
     * preserving unrelated user clipboard contents from other applications.
     */
    fun clearSensitiveClipboardIfOwned(): Boolean {
        val tracked = lastCopiedSensitiveValue ?: return false
        return try {
            val currentClip = clipboardManager.primaryClip
            val currentText = if (currentClip != null && currentClip.itemCount > 0) {
                currentClip.getItemAt(0).text?.toString()
            } else {
                null
            }
            if (currentText == tracked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                lastCopiedSensitiveValue = null
                true
            } else {
                lastCopiedSensitiveValue = null
                false
            }
        } catch (e: Exception) {
            lastCopiedSensitiveValue = null
            false
        }
    }

    fun dismissClipboardStatus() {
        statusDismissJob?.cancel()
        _clipboardStatus.value = null
    }

    // -------------------------------------------------------------------------
    // Password Generator
    // -------------------------------------------------------------------------

    fun updateGeneratorConfig(config: GeneratorConfig) {
        if (!config.isPassphraseMode &&
            !config.includeUpper &&
            !config.includeLower &&
            !config.includeDigits &&
            !config.includeSymbols
        ) {
            postStatusMessage(UiText.Res(R.string.msg_charset_required))
            return
        }
        val clampedConfig = config.copy(
            length = config.length.coerceIn(8, 64),
            passphraseWords = config.passphraseWords.coerceIn(3, 8)
        )
        _generatorConfig.value = clampedConfig
        regeneratePassword()
        onUserInteraction()
    }

    fun regeneratePassword() {
        val config = _generatorConfig.value
        val password = if (config.isPassphraseMode) {
            VaultCrypto.generatePassphrase(
                wordCount = config.passphraseWords,
                separator = config.passphraseSeparator,
                capitalise = config.passphraseCapitalise,
            )
        } else {
            VaultCrypto.generatePassword(
                length = config.length,
                includeUpper = config.includeUpper,
                includeLower = config.includeLower,
                includeDigits = config.includeDigits,
                includeSymbols = config.includeSymbols,
                avoidAmbiguous = config.avoidAmbiguous
            )
        }
        _generatedPassword.value = password
        _passwordStrength.value = VaultCrypto.calculateStrength(password)
        generatorHistoryStore.add(
            GeneratedCredential(
                value = password,
                isPassphrase = config.isPassphraseMode,
                createdAtMillis = System.currentTimeMillis(),
            )
        )
        _generatorHistory.value = generatorHistoryStore.snapshot()
    }

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        repository.setThemeMode(mode.storage)
        onUserInteraction()
    }

    fun clearGeneratorHistory() {
        generatorHistoryStore.clear()
        _generatorHistory.value = emptyList()
        onUserInteraction()
    }

    /** 0 disables automatic locking (manual lock still available). */
    fun updateAutoLock(seconds: Int) {
        if (_autoLockSeconds.value == seconds) return
        _autoLockSeconds.value = seconds
        repository.setAutoLockSeconds(seconds)
        resetAutoLockTimer()
    }

    /** 0 keeps copied secrets on the clipboard until it is overwritten. */
    fun updateClipboardTimeout(seconds: Int) {
        if (_clipboardTimeoutSeconds.value == seconds) return
        _clipboardTimeoutSeconds.value = seconds
        repository.setClipboardTimeoutSeconds(seconds)
    }

    /**
     * Settings switch. Enabling always requires an authenticated biometric prompt:
     * the VMK is wrapped by a Keystore key that only works for a cipher a prompt has
     * authorised, so there is no silent path to create the wrap.
     */
    fun toggleBiometric(enabled: Boolean) {
        if (enabled) {
            requestBiometricEnrollment()
        } else {
            BiometricKeyManager.clearBiometricKey(getApplication())
            repository.setBiometricEnabled(false)
            _biometricEnabled.value = false
            postStatusMessage(UiText.Res(R.string.msg_biometric_disabled_status))
        }
    }

    fun requestBiometricEnrollment() {
        if (repository.getActiveVmkHex() == null) {
            postStatusMessage(UiText.Res(R.string.msg_unlock_before_biometrics))
            return
        }
        if (!BiometricKeyManager.hasCurrentEnrollment(getApplication())) {
            // Fresh enrolment: make sure no stale key material survives.
            BiometricKeyManager.clearBiometricKey(getApplication())
        }
        _biometricPromptEvent.tryEmit(BiometricPurpose.ENROLL)
    }

    /** Called with the authenticated ENCRYPT_MODE cipher after a successful prompt. */
    fun onBiometricEnrollmentVerified(authenticatedCipher: javax.crypto.Cipher?) {
        val vmkHex = repository.getActiveVmkHex()
        if (vmkHex == null || authenticatedCipher == null) {
            _biometricEnabled.value = repository.isBiometricEnabled()
            postStatusMessage(UiText.Res(R.string.msg_biometric_setup_incomplete))
            return
        }
        val stored = BiometricKeyManager.storeVmkWithCipher(getApplication(), vmkHex, authenticatedCipher)
        repository.setBiometricEnabled(stored)
        _biometricEnabled.value = stored
        postStatusMessage(
            if (stored) UiText.Res(R.string.msg_biometric_enabled_status)
            else UiText.Res(R.string.msg_biometric_store_failed)
        )
    }

    fun toggleTheme(isDark: Boolean) {
        repository.setDarkTheme(isDark)
    }
}
