package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.flicky.ui.components.TvNavigationSidebar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.dp

@Composable
fun TvMainScreen(
    selectedIndex: Int,
    onSelect: (Int)->Unit,
    content: @Composable ()->Unit
) {
    Row(Modifier.fillMaxSize()) {
        TvNavigationSidebar(selectedIndex, onSelect)
        HorizontalDivider(Modifier.width(1.dp), DividerDefaults.Thickness, DividerDefaults.color)
        Box(Modifier.weight(1f)) { content() }
    }
}
