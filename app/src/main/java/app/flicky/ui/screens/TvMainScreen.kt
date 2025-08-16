package app.flicky.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import app.flicky.ui.components.TvNavigationSidebar

@Composable
fun TvMainScreen(
    selectedIndex: Int,
    onSelect: (Int)->Unit,
    content: @Composable ()->Unit
) {
    Row(Modifier.fillMaxSize()) {
        TvNavigationSidebar(selectedIndex, onSelect)
        Box(Modifier.weight(1f)) { content() }
    }
}
