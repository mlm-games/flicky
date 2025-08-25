package app.flicky.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageViewer(
    images: List<String>,
    initialPage: Int,
    onClose: () -> Unit
) {
    // Guard against empty list to prevent pager crashes
    if (images.isEmpty()) return

    val safeInitial = initialPage.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { images.size }
    )

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
            val density = LocalDensity.current
            // Convert screen dp to px for Coil's target size to avoid huge allocations or OOMs
            val widthPx = with(density) { cfg.screenWidthDp.dp.roundToPx() }
            val heightPx = with(density) { cfg.screenHeightDp.dp.roundToPx() }
            val targetW = widthPx.coerceAtLeast(720).coerceAtMost(2160)
            val targetH = heightPx.coerceAtLeast(480).coerceAtMost(1440)

            HorizontalPager(state = pagerState) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                // Lightweight zoom via graphicsLayer; avoid excessive scale
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.scaleX = scale
                            this.scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val req = ImageRequest.Builder(context)
                        .data(images[page])
                        .size(targetW, targetH) // px sizes
                        .crossfade(true)
                        .build()

                    AsyncImage(
                        model = req,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {},
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}