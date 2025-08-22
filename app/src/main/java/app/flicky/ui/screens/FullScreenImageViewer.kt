package app.flicky.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlin.math.max
import kotlin.math.min

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
            val cfg = LocalConfiguration.current
            val targetW = max(720, cfg.screenWidthDp).coerceAtMost(1920)
            val targetH = max(480, cfg.screenHeightDp).coerceAtMost(1080)

            HorizontalPager(state = pagerState) { page ->
                var scale by remember { mutableStateOf(1f) }
                val transformState = rememberTransformableState { zoomChange, _, _ ->
                    // Limit zoom to prevent extreme allocations
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
                    val context = LocalContext.current
                    val req = ImageRequest.Builder(context)
                        .data(images[page])
                        .size(targetW, targetH) // downsample to screen-ish size
                        .allowHardware(false)
                        .crossfade(true)
                        .build()

                    AsyncImage(
                        model = req,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}