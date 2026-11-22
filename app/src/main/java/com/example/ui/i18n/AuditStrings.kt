package com.example.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.crypto.VaultCrypto.StrengthGrade
import com.example.model.Vulnerability
import com.example.model.VulnerabilityType

/**
 * Localized rendering of the audit findings. The engine keeps building English `description` and
 * `recommendation` strings (logs, tests); these composables render the same findings from the
 * finding type plus [Vulnerability.descriptionArgs].
 */
@Composable
fun strengthGradeName(grade: StrengthGrade): String = stringResource(
    when (grade) {
        StrengthGrade.VERY_WEAK -> R.string.strength_very_weak
        StrengthGrade.WEAK -> R.string.strength_weak
        StrengthGrade.FAIR -> R.string.strength_fair
        StrengthGrade.STRONG -> R.string.strength_strong
        StrengthGrade.FORTIFIED -> R.string.strength_excellent
    }
)

@Composable
fun Vulnerability.localizedDescription(): String = when (type) {
    VulnerabilityType.REUSED_PASSWORD ->
        stringResource(R.string.auditmsg_reused_desc, descriptionArgs.getOrNull(0)?.toString().orEmpty())
    VulnerabilityType.WEAK_PASSWORD -> {
        val grade = descriptionArgs.getOrNull(0)
            ?.let { runCatching { StrengthGrade.valueOf(it.toString()) }.getOrNull() }
        val bits = (descriptionArgs.getOrNull(1) as? Int) ?: 0
        stringResource(
            R.string.auditmsg_weak_desc,
            grade?.let { strengthGradeName(it) }.orEmpty(),
            bits,
        )
    }
    VulnerabilityType.STALE_PASSWORD ->
        stringResource(R.string.auditmsg_stale_desc, (descriptionArgs.getOrNull(0) as? Int) ?: 0)
    VulnerabilityType.MISSING_2FA -> stringResource(R.string.auditmsg_totp_desc)
}

@Composable
fun Vulnerability.localizedRecommendation(): String = when (type) {
    VulnerabilityType.REUSED_PASSWORD -> stringResource(R.string.auditmsg_reused_rec)
    VulnerabilityType.WEAK_PASSWORD -> stringResource(R.string.auditmsg_weak_rec)
    VulnerabilityType.STALE_PASSWORD -> stringResource(R.string.auditmsg_stale_rec)
    VulnerabilityType.MISSING_2FA -> stringResource(R.string.auditmsg_totp_rec)
}
