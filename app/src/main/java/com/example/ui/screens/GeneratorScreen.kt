package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.VaultType
import com.example.ui.GeneratorConfig
import com.example.ui.VaultViewModel
import com.example.ui.components.StrengthMeter
import com.example.ui.theme.CyberCyan
import com.example.core.GeneratedCredential
import com.example.core.Haptics
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultCard

@Composable
fun GeneratorScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.generatorConfig.collectAsState()
    val password by viewModel.generatedPassword.collectAsState()
    val strength by viewModel.passwordStrength.collectAsState()
    val history by viewModel.generatorHistory.collectAsState()
    val context = LocalContext.current
    var lastHapticLength by remember { mutableStateOf(config.length) }
    var lastHapticWords by remember { mutableStateOf(config.passphraseWords) }

    // Hoisted because copySecurely runs from non-composable click handlers.
    val passwordClipboardLabel = stringResource(R.string.gen_clipboard_password)
    val generatedClipboardLabel = stringResource(R.string.gen_clipboard_generated)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.gen_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.gen_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Result Display Card
        VaultCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                CyberEmerald.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.gen_result_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = {
                            Haptics.tick(context)
                            viewModel.regeneratePassword()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberEmerald.copy(alpha = 0.15f))
                            .testTag("regenerate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.gen_regenerate),
                            tint = CyberEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = password,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                StrengthMeter(strength = strength)

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Copy & Save to Vault
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.copySecurely(passwordClipboardLabel, password, isSensitive = true)
                        },
                        modifier = Modifier.weight(1f).testTag("copy_generated_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_copy))
                    }

                    Button(
                        onClick = {
                            viewModel.saveGeneratedPasswordToVault()
                        },
                        modifier = Modifier.weight(1.2f).testTag("save_generated_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.gen_save_to_vault), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mode Selector: Random vs Passphrase
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !config.isPassphraseMode,
                onClick = {
                    Haptics.tick(context)
                    viewModel.updateGeneratorConfig(config.copy(isPassphraseMode = false))
                },
                label = { Text(stringResource(R.string.gen_mode_password), fontSize = 13.sp) },
                shape = CircleShape,
                border = null,
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedLabelColor = CyberEmerald
                )
            )

            FilterChip(
                selected = config.isPassphraseMode,
                onClick = {
                    Haptics.tick(context)
                    viewModel.updateGeneratorConfig(config.copy(isPassphraseMode = true))
                },
                label = { Text(stringResource(R.string.gen_mode_passphrase), fontSize = 13.sp) },
                shape = CircleShape,
                border = null,
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedLabelColor = CyberEmerald
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options Card
        VaultCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                if (!config.isPassphraseMode) {
                    // Length Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.gen_password_length),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = pluralStringResource(R.plurals.gen_chars_count, config.length, config.length),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald
                        )
                    }

                    Slider(
                        value = config.length.toFloat(),
                        onValueChange = {
                            val next = it.toInt()
                            if (next != lastHapticLength) {
                                lastHapticLength = next
                                Haptics.tick(context)
                            }
                            viewModel.updateGeneratorConfig(config.copy(length = next))
                        },
                        valueRange = 8f..64f,
                        steps = 56,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberEmerald,
                            activeTrackColor = CyberEmerald
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggles
                    GeneratorToggleRow(
                        title = stringResource(R.string.gen_opt_uppercase),
                        checked = config.includeUpper,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeUpper = it)) }
                    )
                    GeneratorToggleRow(
                        title = stringResource(R.string.gen_opt_lowercase),
                        checked = config.includeLower,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeLower = it)) }
                    )
                    GeneratorToggleRow(
                        title = stringResource(R.string.gen_opt_numbers),
                        checked = config.includeDigits,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeDigits = it)) }
                    )
                    GeneratorToggleRow(
                        title = stringResource(R.string.gen_opt_symbols),
                        checked = config.includeSymbols,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeSymbols = it)) }
                    )
                    GeneratorToggleRow(
                        title = stringResource(R.string.gen_opt_avoid_ambiguous),
                        checked = config.avoidAmbiguous,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(avoidAmbiguous = it)) }
                    )
                } else {
                    // Passphrase options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.gen_word_count),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = pluralStringResource(R.plurals.gen_words_count, config.passphraseWords, config.passphraseWords),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald
                        )
                    }

                    Slider(
                        value = config.passphraseWords.toFloat(),
                        onValueChange = {
                            val next = it.toInt()
                            if (next != lastHapticWords) {
                                lastHapticWords = next
                                Haptics.tick(context)
                            }
                            viewModel.updateGeneratorConfig(config.copy(passphraseWords = next))
                        },
                        valueRange = 3f..8f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberEmerald,
                            activeTrackColor = CyberEmerald
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.gen_separator),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "-" to "-",
                            "." to ".",
                            "_" to "_",
                            " " to stringResource(R.string.gen_separator_space)
                        ).forEach { (sep, label) ->
                            FilterChip(
                                selected = config.passphraseSeparator == sep,
                                onClick = {
                                    viewModel.updateGeneratorConfig(config.copy(passphraseSeparator = sep))
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                shape = CircleShape,
                                border = null,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    GeneratorToggleRow(
                        title = stringResource(R.string.gen_opt_capitalise),
                        checked = config.passphraseCapitalise,
                        onCheckedChange = {
                            viewModel.updateGeneratorConfig(config.copy(passphraseCapitalise = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.gen_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            VaultCard(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.gen_history_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.gen_history_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.clearGeneratorHistory() }) {
                            Text(stringResource(R.string.common_clear), fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    history.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val relativeTimestamp = relativeTime(entry.createdAtMillis)
                                Text(
                                    text = if (entry.isPassphrase) {
                                        pluralStringResource(
                                            R.plurals.gen_history_passphrase,
                                            entry.length,
                                            entry.length
                                        )
                                    } else {
                                        pluralStringResource(
                                            R.plurals.gen_history_password,
                                            entry.length,
                                            entry.length
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "••••••••••  $relativeTimestamp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.copySecurely(generatedClipboardLabel, entry.value, isSensitive = true)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.gen_a11y_copy_generated),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun relativeTime(createdAtMillis: Long): String {
    val diffSeconds = ((System.currentTimeMillis() - createdAtMillis) / 1000).coerceAtLeast(0)
    return when {
        diffSeconds < 45 -> stringResource(R.string.gen_time_just_now)
        diffSeconds < 60 * 60 -> {
            val minutes = (diffSeconds / 60).toInt()
            pluralStringResource(R.plurals.gen_time_minutes_ago, minutes, minutes)
        }
        else -> {
            val hours = (diffSeconds / 3600).toInt()
            pluralStringResource(R.plurals.gen_time_hours_ago, hours, hours)
        }
    }
}

@Composable
private fun GeneratorToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = {
                Haptics.tick(context)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberEmerald,
                checkedTrackColor = CyberEmerald.copy(alpha = 0.3f)
            )
        )
    }
}
