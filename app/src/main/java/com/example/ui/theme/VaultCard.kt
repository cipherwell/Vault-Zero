package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The single card container used across the app.
 *
 * One UI's separation cue is a lifted surface, not heavy elevation. Cards get a
 * soft drop shadow in the light theme only (where it reads on #FAFAFA); the
 * dark theme has no shadows at all, because on true black they are invisible at
 * best and a compositing artefact at worst. The hairline border remains the
 * primary edge in both themes.
 */
@Composable
fun VaultCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    border: BorderStroke? = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // A translucent card lets the shadow underneath show through and stamps the
    // shadow layer's rectangular bounds across the surface. Composite the
    // container onto the background so every card is opaque without changing
    // how it looks.
    // Dark cards are a single neutral grey, regardless of the near-black
    // surface the caller asked for. Compositing a translucent or black
    // container leaves the card indistinguishable from the ground (or picks up
    // the backdrop's tint); One UI's separation cue is the lifted grey surface.
    val darkContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val opaqueColors = colors.copy(
        containerColor = if (dark) {
            darkContainer
        } else {
            colors.containerColor.compositeOver(MaterialTheme.colorScheme.background)
        }
    )
    Card(
        // Dark mode has no shadows: on true black they are invisible at best and
        // a compositing artefact at worst. Separation there comes from the lifted
        // surface and the hairline border.
        modifier = if (dark) modifier else modifier.vaultCardShadow(shape),
        shape = shape,
        colors = opaqueColors,
        border = border,
        content = content,
    )
}

@Composable
private fun Modifier.vaultCardShadow(shape: Shape): Modifier = shadow(
    elevation = 10.dp,
    shape = shape,
    clip = true,
    ambientColor = Color.Black.copy(alpha = 0.10f),
    spotColor = Color.Black.copy(alpha = 0.16f),
)
