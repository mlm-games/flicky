package app.flicky.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageViewer(
    images: List<String>,
    initialPage: Int,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} / ${images.size}") },
                actions = {
                    TextButton(onClick = onClose) { Text("Close") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pagerState) { page ->
                var scale by remember { mutableStateOf(1f) }
                val transformState = rememberTransformableState { zoomChange, _, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 4f)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .graphicsLayer {
                            this.scaleX = scale
                            this.scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(model = images[page], contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}