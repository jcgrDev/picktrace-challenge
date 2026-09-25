package com.jcgrdev.picktracechallenge.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jcgrdev.picktracechallenge.core.designsystem.theme.LocalExtendedColors

/** Designsystem-local tone, so this module needs no domain types. */
enum class StatusTone { Neutral, Success, Error }

/** A status label. The text is always shown, so color is never the only signal. */
@Composable
fun StatusChip(label: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.Success -> LocalExtendedColors.current.successContainer to LocalExtendedColors.current.onSuccessContainer
        StatusTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
