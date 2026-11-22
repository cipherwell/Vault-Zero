package com.example.ui.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A piece of user-visible text that may be produced outside a Compose scope (ViewModels,
 * background work) where no `Context` is available. Resolve it in the UI with [asString], which
 * respects the in-app language selected in the composition.
 */
sealed interface UiText {
    /** A string resource with optional format arguments. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    /** A plural resource with its quantity. */
    data class Plural(@PluralsRes val id: Int, val count: Int) : UiText

    /** Fallback for text that is not localizable (e.g. a platform error message). */
    data class Raw(val text: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

/** Resolve a [UiText] in the current composition language. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, count)
    is UiText.Raw -> text
}
