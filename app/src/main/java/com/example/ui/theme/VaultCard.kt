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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The single card container used across the app.
 *
 * One UI's separation cue is a lifted surface, not heavy elevation: every card
 * gets a soft drop shadow in the light theme (where a shadow reads on #FAFAFA)
 * and a faint accent-tinted halo in the dark theme (where a black shadow would
 * vanish against #000000). Elevation stays low so the chrome never competes
 * with content; the hairline border remains the primary edge.
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
    Card(
        modifier = modifier.vaultCardShadow(shape, dark),
        shape = shape,
        colors = colors,
        border = border,
        content = content,
    )
}

@Composable
private fun Modifier.vaultCardShadow(shape: Shape, dark: Boolean): Modifier = shadow(
    elevation = if (dark) 9.dp else 10.dp,
    shape = shape,
    clip = false,
    ambientColor = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f),
    spotColor = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.16f),
)
