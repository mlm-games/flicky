package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.flicky.ui.components.TvNavigationSidebar
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp

@Composable
fun TvMainScreen(
    selectedIndex: Int,
    onSelect: (Int)->Unit,
    content: @Composable ()->Unit
) {
    Row(Modifier.fillMaxSize()) {
        TvNavigationSidebar(selectedIndex, onSelect)
        Divider(Modifier.width(1.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
