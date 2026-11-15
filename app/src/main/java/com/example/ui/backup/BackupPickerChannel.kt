package com.example.ui.backup

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Minimal activity-result bridge for the SAF backup pickers.
 *
 * Why not `rememberLauncherForActivityResult`: this app's dependency graph pins
 * `androidx.fragment:fragment` 1.2.5 (via biometric 1.2.0-alpha05) while
 * `androidx.activity` is 1.10.1. The modern registry mints request codes above
 * 16 bits, and the old FragmentActivity rejects those with
 * "Can only use lower 16 bits for requestCode". Driving the two intents from
 * MainActivity with explicit small request codes avoids the registry entirely,
 * keeps the picker activity-scoped, and survives the fragment version mismatch.
 */
object BackupPickerChannel {

    enum class Kind { EXPORT, IMPORT }

    data class Request(val kind: Kind, val suggestedName: String? = null)

    data class Result(val kind: Kind, val uri: Uri?)

    val requests = MutableSharedFlow<Request>(extraBufferCapacity = 1)

    val results = MutableSharedFlow<Result>(extraBufferCapacity = 1)

    fun requestExport(suggestedName: String) {
        requests.tryEmit(Request(Kind.EXPORT, suggestedName))
    }

    fun requestImport() {
        requests.tryEmit(Request(Kind.IMPORT))
    }

    fun deliver(result: Result) {
        results.tryEmit(result)
    }
}
