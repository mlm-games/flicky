package app.flicky.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
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

@Composable
fun FullscreenImageViewer(
    images: List<String>,
    initialPage: Int,
    onClose: () -> Unit
) {
    if (images.isEmpty()) return

    val safeInitial = initialPage.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { images.size })

    BackHandler { onClose() }

    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = with(density) { cfg.screenWidthDp.dp.roundToPx() }
    val heightPx = with(density) { cfg.screenHeightDp.dp.roundToPx() }
    val targetW = widthPx.coerceIn(720, 2160)
    val targetH = heightPx.coerceIn(480, 1440)

    val viewerScope = rememberCoroutineScope()
    var currentPageScale by remember { mutableFloatStateOf(1f) }
    var isNavigating by remember { mutableStateOf(false) }

    // Store current page's control functions
    var zoomIn: (() -> Unit)? by remember { mutableStateOf(null) }
    var zoomOut: (() -> Unit)? by remember { mutableStateOf(null) }
    var toggleZoom: (() -> Unit)? by remember { mutableStateOf(null) }
    var panLeft: (() -> Unit)? by remember { mutableStateOf(null) }
    var panRight: (() -> Unit)? by remember { mutableStateOf(null) }
    var panUp: (() -> Unit)? by remember { mutableStateOf(null) }
    var panDown: (() -> Unit)? by remember { mutableStateOf(null) }

    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        contentFocus.requestFocus()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(modifier = Modifier.focusable(false)) {
                TopAppBar(
                    title = { Text("${pagerState.currentPage + 1} / ${images.size}") },
                    actions = {
                        TextButton(
                            onClick = onClose,
                            modifier = Modifier.focusable(false)
                        ) { Text(stringResource(R.string.action_close)) }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(contentFocus)
                .focusable()
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (ev.key) {
                        Key.Back, Key.Escape -> {
                            onClose()
                            true
                        }
                        // Zoom controls
                        Key.Plus, Key.NumPadAdd -> {
                            zoomIn?.invoke()
                            true
                        }
                        Key.Minus, Key.NumPadSubtract -> {
                            zoomOut?.invoke()
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            toggleZoom?.invoke()
                            true
                        }
                        // Directional keys
                        Key.DirectionLeft -> {
                            if (currentPageScale <= 1.01f) {
                                // Navigate pages at 1x
                                if (!isNavigating && pagerState.currentPage > 0) {
                                    isNavigating = true
                                    viewerScope.launch {
                                        try {
                                            pagerState.scrollToPage(pagerState.currentPage - 1)
                                        } finally {
                                            isNavigating = false
                                        }
                                    }
                                }
                            } else {
                                // Pan when zoomed
                                panLeft?.invoke()
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (currentPageScale <= 1.01f) {
                                // Navigate pages at 1x
                                if (!isNavigating && pagerState.currentPage < images.lastIndex) {
                                    isNavigating = true
                                    viewerScope.launch {
                                        try {
                                            pagerState.scrollToPage(pagerState.currentPage + 1)
                                        } finally {
                                            isNavigating = false
                                        }
                                    }
                                }
                            } else {
                                // Pan when zoomed
                                panRight?.invoke()
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (currentPageScale > 1.01f) {
                                panUp?.invoke()
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (currentPageScale > 1.01f) {
                                panDown?.invoke()
                            }
                            true
                        }
                        else -> {
                            // Check for DPAD_CENTER
                            val isDpadCenter = ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                            if (isDpadCenter) {
                                toggleZoom?.invoke()
                                true
                            } else false
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0
            ) { page ->
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val pageScope = rememberCoroutineScope()
                    val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                    val minScale = 1f
                    val maxScale = 5f
                    val scaleAnim = remember(page) { Animatable(1f) }
                    val offsetAnim = remember(page) { Animatable(Offset.Zero, Offset.VectorConverter) }

                    LaunchedEffect(page, scaleAnim.value) {
                        if (page == pagerState.currentPage) {
                            currentPageScale = scaleAnim.value
                        }
                    }

                    fun maxBoundsX(s: Float) = ((s * viewportW - viewportW) / 2f).coerceAtLeast(0f)
                    fun maxBoundsY(s: Float) = ((s * viewportH - viewportH) / 2f).coerceAtLeast(0f)
                    fun clampOffset(o: Offset, s: Float): Offset {
                        val mx = maxBoundsX(s)
                        val my = maxBoundsY(s)
                        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
                    }

                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        val newScale = (scaleAnim.value * zoomChange).coerceIn(minScale, maxScale)
                        pageScope.launch { scaleAnim.snapTo(newScale) }
                        val newOffset = clampOffset(offsetAnim.value + panChange, newScale)
                        pageScope.launch { offsetAnim.snapTo(newOffset) }
                    }

                    fun animateZoomTo(target: Float, tap: Offset? = null) {
                        val startScale = scaleAnim.value
                        val factor = (target / startScale).coerceIn(0.01f, 100f)
                        val startOffset = offsetAnim.value
                        val targetOffset = if (tap != null) {
                            clampOffset((startOffset - tap) * factor + tap, target)
                        } else {
                            if (target == 1f) Offset.Zero else clampOffset(startOffset, target)
                        }
                        val spec = tween<Float>(durationMillis = 180)
                        pageScope.launch {
                            launch { scaleAnim.animateTo(target, spec) }
                            launch { offsetAnim.animateTo(targetOffset, tween(180)) }
                        }
                    }

                    // Register control functions for current page
                    LaunchedEffect(page) {
                        if (page == pagerState.currentPage) {
                            val panStepX = viewportW * 0.25f
                            val panStepY = viewportH * 0.25f

                            zoomIn = {
                                val target = (scaleAnim.value + 0.25f).coerceIn(minScale, maxScale)
                                animateZoomTo(target)
                            }
                            zoomOut = {
                                val target = (scaleAnim.value - 0.25f).coerceIn(minScale, maxScale)
                                animateZoomTo(if (target == minScale) 1f else target)
                            }
                            toggleZoom = {
                                animateZoomTo(if (scaleAnim.value > 1f) 1f else 2f)
                            }

                            panLeft = {
                                val scale = scaleAnim.value
                                val offset = offsetAnim.value
                                val new = clampOffset(offset + Offset(panStepX, 0f), scale)
                                if (new == offset && offset.x >= -4f && maxBoundsX(scale) > 0f && !isNavigating && pagerState.currentPage > 0) {
                                    // At edge, navigate
                                    isNavigating = true
                                    viewerScope.launch {
                                        try {
                                            pagerState.scrollToPage(pagerState.currentPage - 1)
                                        } finally {
                                            isNavigating = false
                                        }
                                    }
                                } else {
                                    pageScope.launch { offsetAnim.animateTo(new, tween(120)) }
                                }
                            }

                            panRight = {
                                val scale = scaleAnim.value
                                val offset = offsetAnim.value
                                val new = clampOffset(offset + Offset(-panStepX, 0f), scale)
                                if (new == offset && (maxBoundsX(scale) - offset.x) <= 4f && maxBoundsX(scale) > 0f && !isNavigating && pagerState.currentPage < images.lastIndex) {
                                    // At edge, navigate
                                    isNavigating = true
                                    viewerScope.launch {
                                        try {
                                            pagerState.scrollToPage(pagerState.currentPage + 1)
                                        } finally {
                                            isNavigating = false
                                        }
                                    }
                                } else {
                                    pageScope.launch { offsetAnim.animateTo(new, tween(120)) }
                                }
                            }

                            panUp = {
                                val scale = scaleAnim.value
                                val offset = offsetAnim.value
                                val new = clampOffset(offset + Offset(0f, panStepY), scale)
                                pageScope.launch { offsetAnim.animateTo(new, tween(120)) }
                            }

                            panDown = {
                                val scale = scaleAnim.value
                                val offset = offsetAnim.value
                                val new = clampOffset(offset + Offset(0f, -panStepY), scale)
                                pageScope.launch { offsetAnim.animateTo(new, tween(120)) }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(page) {
                                detectTapGestures(
                                    onDoubleTap = { pos ->
                                        val s = scaleAnim.value
                                        val next = when {
                                            s < 1.75f -> 2f
                                            s < 2.75f -> 3f
                                            else -> 1f
                                        }
                                        animateZoomTo(next, tap = pos)
                                    }
                                )
                            }
                            .transformable(
                                state = transformState,
                                enabled = scaleAnim.value > 1.01f
                            )
                            .graphicsLayer {
                                translationX = offsetAnim.value.x
                                translationY = offsetAnim.value.y
                                scaleX = scaleAnim.value
                                scaleY = scaleAnim.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val req = ImageRequest.Builder(LocalContext.current)
                            .data(images[page])
                            .size(targetW, targetH)
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