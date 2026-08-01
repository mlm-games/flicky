package app.flicky.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import app.flicky.helper.RequestFocusOnKey
import app.flicky.helper.tvContentPane
import app.flicky.ui.components.TvNavigationSidebar

@Composable
fun TvMainScreen(
    selectedIndex: Int,
    contentKey: Any,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    val contentFocusRequester = remember { FocusRequester() }

    // Whenever tab or nested destination changes, land focus in the content pane
    // (first focusable child), not the left rail.
    RequestFocusOnKey(key = contentKey, focusRequester = contentFocusRequester)

    Row(Modifier.fillMaxSize()) {
        TvNavigationSidebar(selected = selectedIndex, onSelect = onSelect)
        Box(
            Modifier
                .weight(1f)
                .tvContentPane(contentFocusRequester)
        ) {
            content()
        }
    }
}
