package com.example.ui

import android.app.Application
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
import com.example.data.VaultPreferences
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

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _viewingItem = MutableStateFlow<VaultItem?>(null)
    val viewingItem: StateFlow<VaultItem?> = _viewingItem.asStateFlow()

    private val _editingItem = MutableStateFlow<VaultItem?>(null)
    val editingItem: StateFlow<VaultItem?> = _editingItem.asStateFlow()

    private val _newRecoveryPhrase = MutableStateFlow<String?>(null)
    val newRecoveryPhrase: StateFlow<String?> = _newRecoveryPhrase.asStateFlow()

    private val _clipboardStatus = MutableStateFlow<String?>(null)
    val clipboardStatus: StateFlow<String?> = _clipboardStatus.asStateFlow()

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

    private var clipboardJob: Job? = null
    private var autoLockJob: Job? = null
    private var statusDismissJob: Job? = null

    @Volatile
    private var lastInteractionRealtime: Long = SystemClock.elapsedRealtime()

    @Volatile
    private var lastCopiedSensitiveValue: String? = null

    private fun postStatusMessage(message: String, durationMs: Long = 2200L) {
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

    fun setupMasterPassword(password: String, confirm: String) {
        val cleanPassword = password.trim()
        val cleanConfirm = confirm.trim()
        if (cleanPassword.length < 8) {
            _authError.value = "Master Password must be at least 8 characters"
            return
        }
        if (cleanPassword != cleanConfirm) {
            _authError.value = "Passwords do not match"
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val (phrase, vmkHex) = repository.setupMasterPassword(cleanPassword)

                _newRecoveryPhrase.value = phrase
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = "Setup failed: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun completeSetupAndEnterVault() {
        _newRecoveryPhrase.value = null
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
            _authError.value = "Enter the 12-word recovery phrase for this backup"
            return
        }
        val cleanNewPassword = newPassword?.trim().orEmpty()
        if (cleanNewPassword.isNotEmpty()) {
            if (cleanNewPassword.length < 8) {
                _authError.value = "New Master Password must be at least 8 characters"
                return
            }
            if (cleanNewPassword != confirmPassword.trim()) {
                _authError.value = "New Master Passwords do not match"
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
                val passwordNote = when {
                    report.phraseOnlyBackup -> "new master password set"
                    report.keptOriginalPassword -> "your original master password still works"
                    else -> "new master password set"
                }
                postStatusMessage(
                    "Vault restored · ${report.itemCount} " +
                        (if (report.itemCount == 1) "entry" else "entries") +
                        " · recovery kit unchanged · $passwordNote",
                    durationMs = 6000L,
                )
            } catch (e: IllegalArgumentException) {
                _authError.value = "Restore failed: ${e.message}"
            } catch (e: Exception) {
                _authError.value = "Restore failed: ${e.message ?: "invalid backup"}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }


    fun unlockVault(password: String) {
        if (password.isBlank()) {
            _authError.value = "Please enter your Master Password"
            return
        }
        val lockoutSec = repository.getRemainingLockoutSeconds()
        if (lockoutSec > 0) {
            _authError.value = "Too many failed attempts. Try again in ${lockoutSec}s."
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val success = repository.unlock(password)
                if (success) {

                    _lockState.value = VaultLockState.UNLOCKED
                    _authError.value = null
                    resetAutoLockTimer()
                } else {
                    val newLockoutSec = repository.getRemainingLockoutSeconds()
                    if (newLockoutSec > 0) {
                        _authError.value = "Too many failed attempts. Locked out for ${newLockoutSec}s."
                    } else {
                        _authError.value = "Incorrect Master Password. Please check and try again."
                    }
                }
            } catch (e: Exception) {
                _authError.value = "Authentication error: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun requestBiometricUnlock() {
        if (!repository.isBiometricEnabled() || !_biometricEnabled.value) {
            _authError.value = "Biometric unlock is disabled in Settings. Unlock with Master Password first."
            return
        }
        if (!BiometricKeyManager.hasCurrentEnrollment(getApplication())) {
            _authError.value = "Biometric enrolment is missing or was invalidated. Unlock with Master Password and re-enable it in Settings."
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
                    _authError.value = "Biometric authentication did not authorise the vault key. Please try again."
                    return@launch
                }
                val vmkHex = BiometricKeyManager.decryptVmkWithCipher(getApplication(), authenticatedCipher)
                if (vmkHex != null) {
                    val success = repository.unlockWithVmkHex(vmkHex)
                    if (success) {
                        _lockState.value = VaultLockState.UNLOCKED
                        _authError.value = null
                        resetAutoLockTimer()
                        return@launch
                    }
                }
                _authError.value = "Biometric authentication failed. Please enter Master Password."
            } catch (e: Exception) {
                _authError.value = "Biometric unlock error: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun onBiometricError(message: String) {
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
            _authError.value = "Recovery phrase must be 12 valid words from your Emergency Recovery Kit"
            return
        }
        if (!VaultCrypto.verifyRecoveryPhraseChecksum(cleanPhrase)) {
            _authError.value = "Recovery phrase checksum mismatch — check for a mistyped word or wrong word order"
            return
        }
        if (cleanNewPass.length < 8) {
            _authError.value = "New password must be at least 8 characters"
            return
        }
        if (cleanNewPass != cleanConfirm) {
            _authError.value = "Passwords do not match"
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val success = repository.recoverWithPhrase(cleanPhrase, cleanNewPass)
                if (success) {

                    _lockState.value = VaultLockState.UNLOCKED
                    _authError.value = null
                    resetAutoLockTimer()
                } else {
                    _authError.value = "Recovery phrase does not match this vault"
                }
            } catch (e: Exception) {
                _authError.value = "Recovery error: ${e.message}"
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
            postStatusMessage("Vault auto-locked due to inactivity")
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
                postStatusMessage("Delete failed: ${e.message}")
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
                postStatusMessage(
                    "Restored ${batch.items.size} ${if (batch.items.size == 1) "item" else "items"}"
                )
            } catch (e: Exception) {
                postStatusMessage("Could not restore: ${e.message}")
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
                    if (favorite) "Marked ${ids.size} as favourite" else "Removed favourite from ${ids.size}"
                )
            } catch (e: Exception) {
                postStatusMessage("Could not update favourites: ${e.message}")
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
                postStatusMessage("Saved '${item.title}' securely")
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Failed to save: ${e.message}")
            }
        }
    }

    fun toggleFavorite(item: VaultItem) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(item.id, !item.isFavorite)
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Cannot update favorite while locked")
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
                postStatusMessage("Deleted '${item.title}'")
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Failed to delete: ${e.message}")
            }
        }
    }

    fun clearVault() {
        viewModelScope.launch {
            BiometricKeyManager.clearBiometricKey(getApplication())
            clearSensitiveClipboardIfOwned()
            repository.clearVault()
            _lockState.value = VaultLockState.NEEDS_SETUP
            _viewingItem.value = null
            _editingItem.value = null
            _newRecoveryPhrase.value = null
            postStatusMessage("Vault completely reset")
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
                postStatusMessage("Export failed: ${e.message}")
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
                postStatusMessage(msg)
                onUserInteraction()
            } catch (prompt: BackupVerificationException) {
                if (allowPartial) {
                    // Should not happen (partial mode no longer throws), but never
                    // leave the UI without feedback.
                    postStatusMessage("Import failed — nothing was written: ${prompt.message}")
                } else {
                    pendingPartialImportJson = json
                    pendingPartialImportPassword = backupPassword
                    _importVerificationPrompt.value = ImportVerificationPrompt(
                        unreadableRecords = prompt.unreadableRecords,
                        message = prompt.message ?: "Some records could not be verified.",
                    )
                }
            } catch (e: IllegalArgumentException) {
                postStatusMessage("Import rejected — nothing was written: ${e.message}")
            } catch (e: Exception) {
                postStatusMessage("Import failed — nothing was written: ${e.message ?: "invalid backup"}")
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
            postStatusMessage("New password must be at least 8 characters")
            onResult(false)
            return
        }
        if (newPassword != confirmPassword) {
            postStatusMessage("New passwords do not match")
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                repository.changeMasterPassword(currentPassword, newPassword)
                postStatusMessage("Master password changed")
                onResult(true)
            } catch (e: Exception) {
                postStatusMessage("Could not change password: ${e.message}")
                onResult(false)
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
        val statusMsg = when {
            !isSensitive -> "$label copied to clipboard"
            timeout > 0 -> "$label copied • Auto-clears in ${timeout}s"
            else -> "$label copied • Clears when the vault locks"
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
            postStatusMessage("At least one character set must remain enabled")
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
            postStatusMessage("Biometric unlock disabled")
        }
    }

    fun requestBiometricEnrollment() {
        if (repository.getActiveVmkHex() == null) {
            postStatusMessage("Unlock the vault with your master password before enabling biometrics")
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
            postStatusMessage("Biometric setup was not completed")
            return
        }
        val stored = BiometricKeyManager.storeVmkWithCipher(getApplication(), vmkHex, authenticatedCipher)
        repository.setBiometricEnabled(stored)
        _biometricEnabled.value = stored
        postStatusMessage(
            if (stored) "Biometric unlock enabled" else "Could not store the biometric key"
        )
    }

    fun toggleTheme(isDark: Boolean) {
        repository.setDarkTheme(isDark)
    }
}
