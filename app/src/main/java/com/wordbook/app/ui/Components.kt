package com.wordbook.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Shared building blocks for the toss-like look: flat bars, big rounded buttons, thick progress.

val ButtonShape = RoundedCornerShape(16.dp)

/** Flat top bar that blends into the page background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
        ),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** Grey secondary button. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = null,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun ThickProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(8.dp).clip(RoundedCornerShape(4.dp)),
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
