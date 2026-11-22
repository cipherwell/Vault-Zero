package com.example.ui.i18n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Compact language affordance for the lock / setup / recovery screens: shows the language a
 * first-time user can switch to, in that language, so the app is understandable before a vault
 * exists. Opens a dialog with System / English / Español.
 */
@Composable
fun LockScreenLanguageButton(
    controller: LanguageController?,
    modifier: Modifier = Modifier,
) {
    if (controller == null) return
    var showDialog by remember { mutableStateOf(false) }
    // Offer the other language, labelled in that language.
    val label = when (controller.language) {
        AppLanguage.SPANISH_US -> stringResource(R.string.language_english_us)
        else -> stringResource(R.string.language_spanish_us)
    }
    TextButton(
        onClick = { showDialog = true },
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (showDialog) {
        LanguageDialog(
            current = controller.language,
            onSelect = {
                controller.updateLanguage(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** Settings entry point: one row of choices, same dialog. */
@Composable
fun SettingsLanguageRow(
    controller: LanguageController?,
    modifier: Modifier = Modifier,
) {
    if (controller == null) return
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = false,
                onClick = { showDialog = true },
                role = Role.Button,
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.language_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = languageLabel(controller.language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (showDialog) {
        LanguageDialog(
            current = controller.language,
            onSelect = {
                controller.updateLanguage(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ENGLISH_US -> stringResource(R.string.language_english_us)
    AppLanguage.SPANISH_US -> stringResource(R.string.language_spanish_us)
}

@Composable
fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = current == language,
                                onClick = { onSelect(language) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current == language,
                            onClick = null,
                        )
                        Text(
                            text = languageLabel(language),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}
