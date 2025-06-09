package dev.lokksmith.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = lightColorScheme(
        primary = AppColors.Primary,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
