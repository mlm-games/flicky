package app.flicky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.flicky.helper.DebugLog
import kotlinx.coroutines.launch

@Composable
fun DebugOverlay(visible: Boolean, maxLines: Int = 50) {
    if (!visible) return
    val scope = rememberCoroutineScope()
    val linesState = remember { mutableStateListOf<String>() }
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        DebugLog.lines.collect { line ->
            if (linesState.size >= maxLines) linesState.removeAt(0)
            linesState.add(line)
            scope.launch {
                // auto-scroll to bottom
                scroll.animateScrollTo((scroll.maxValue + 200).coerceAtLeast(0))
            }
        }
    }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color(0x99121212))
            .padding(8.dp)
    ) {
        Column(Modifier.verticalScroll(scroll)) {
            linesState.forEach { l ->
                Text(l, color = Color.White, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
