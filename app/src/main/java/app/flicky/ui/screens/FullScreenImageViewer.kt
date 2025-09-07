package app.flicky.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageViewer(
    images: List<String>,
    initialPage: Int,
    onClose: () -> Unit
) {
    if (images.isEmpty()) return

    val scope = rememberCoroutineScope()
    val safeInitial = initialPage.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { images.size })

    BackHandler { onClose() }

    val handleKey: (KeyEvent) -> Boolean = { ev ->
        if (ev.type != KeyEventType.KeyDown) false else when (ev.key) {
            Key.DirectionLeft -> {
                val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                if (prev != pagerState.currentPage) { scope.launch { pagerState.animateScrollToPage(prev) }; true } else false
            }
            Key.DirectionRight -> {
                val next = (pagerState.currentPage + 1).coerceAtMost(images.lastIndex)
                if (next != pagerState.currentPage) { scope.launch { pagerState.animateScrollToPage(next) }; true } else false
            }
            Key.Back, Key.Escape -> { onClose(); true }
            else -> false
        }
    }

    // Ensure the viewer content gets focus initially on TV
    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { contentFocus.requestFocus() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent(handleKey),
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} / ${images.size}") },
                actions = { TextButton(onClick = onClose) { Text( stringResource(R.string.action_close)) } }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(contentFocus)
                .focusable()
        ) {
            val cfg = LocalConfiguration.current
            val density = LocalDensity.current
            // Convert screen dp to px for Coil's target size to avoid huge allocations or OOMs
            val widthPx = with(density) { cfg.screenWidthDp.dp.roundToPx() }
            val heightPx = with(density) { cfg.screenHeightDp.dp.roundToPx() }
            val targetW = widthPx.coerceAtLeast(720).coerceAtMost(2160)
            val targetH = heightPx.coerceAtLeast(480).coerceAtMost(1440)


            HorizontalPager(state = pagerState) { page ->
                // Viewport size for clamp calculations
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                    var scale by remember(page) { mutableFloatStateOf(1f) }
                    var offset by remember(page) { mutableStateOf(Offset.Zero) }

                    val minScale = 1f
                    val maxScale = 5f

                    fun clampOffset(o: Offset, s: Float): Offset {
                        // Allow panning only when content is larger than viewport
                        val maxX = ((s * viewportW - viewportW) / 2f).coerceAtLeast(0f)
                        val maxY = ((s * viewportH - viewportH) / 2f).coerceAtLeast(0f)
                        return Offset(
                            x = o.x.coerceIn(-maxX, maxX),
                            y = o.y.coerceIn(-maxY, maxY)
                        )
                    }

                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
                        // Apply pan in screen space and clamp for new scale
                        val newOffset = clampOffset(offset + panChange, newScale)
                        scale = newScale
                        offset = newOffset
                    }

                    // Double‑tap to zoom: 1x -> 2x -> 3x -> 1x, keeping the tap point in place
                    fun onDoubleTap(pos: Offset) {
                        val target = when {
                            scale < 1.75f -> 2f
                            scale < 2.75f -> 3f
                            else -> 1f
                        }
                        val old = scale
                        val factor = target / old
                        // scale around the tap position: o' = (o - pos) * factor + pos
                        val newOffset = (offset - pos) * factor + pos
                        scale = target
                        offset = clampOffset(newOffset, target).let {
                            // When returning to 1x, recenter
                            if (target == 1f) Offset.Zero else it
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(page) {
                                detectTapGestures(
                                    onDoubleTap = { pos -> onDoubleTap(pos) }
                                )
                            }
                            .transformable(transformState)
                            .graphicsLayer {
                                translationX = offset.x
                                translationY = offset.y
                                scaleX = scale
                                scaleY = scale
                            }
                            // TV remote/D‑pad zoom controls on this page
                            .onPreviewKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (ev.key) {
                                    Key.DirectionUp, Key.Plus, Key.NumPadAdd -> {
                                        scale = (scale + 0.25f).coerceIn(minScale, maxScale)
                                        offset = clampOffset(offset, scale)
                                        true
                                    }
                                    Key.DirectionDown, Key.Minus, Key.NumPadSubtract -> {
                                        scale = (scale - 0.25f).coerceIn(minScale, maxScale)
                                        offset = if (scale == minScale) Offset.Zero else clampOffset(offset, scale)
                                        true
                                    }
                                    Key.Enter, Key.MediaPlayPause -> {
                                        if (scale > 1f) {
                                            scale = 1f; offset = Offset.Zero
                                        } else {
                                            scale = 2f // quick toggle
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val req = ImageRequest.Builder(LocalContext.current)
                            .data(images[page])
                            .size(targetW, targetH) // keep your existing target sizes
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
}