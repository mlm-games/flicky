package app.flicky.ui.components

import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.flicky.R
import app.flicky.helper.DeviceUtils
import app.flicky.helper.HtmlUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * - If HTML is present and rich=true, renders HTML (links clickable).
 * - Otherwise renders plain text (HTML stripped if needed).
 *
 * For lists/grids, set rich=false to keep performance high.
 */
@Composable
fun SmartText(
    text: String,
    modifier: Modifier = Modifier,
    rich: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    ellipsizeEnd: Boolean = false
) {
    val hasHtml = remember(text) { HtmlUtils.containsHtml(text) }
    val plain = remember(text, hasHtml) { if (hasHtml) HtmlUtils.toPlainText(text) else text }

    if (rich && hasHtml) {
        val color = colorScheme.onSurface.toArgb()
        val link = colorScheme.primary.toArgb()
        val spanned = remember(text) { HtmlUtils.toSpanned(text) }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(color)
                    setLinkTextColor(link)
                    textSize = 14f
                    movementMethod = LinkMovementMethod.getInstance()
                    setLineSpacing(0f, 1.1f)
                    if (maxLines != Int.MAX_VALUE) {
                        this.maxLines = maxLines
                        if (ellipsizeEnd) this.ellipsize = TextUtils.TruncateAt.END
                    }
                }
            },
            update = { tv ->
                tv.setTextColor(color)
                tv.setLinkTextColor(link)
                if (maxLines != Int.MAX_VALUE) {
                    tv.maxLines = maxLines
                    tv.ellipsize = if (ellipsizeEnd) TextUtils.TruncateAt.END else null
                } else {
                    tv.maxLines = Int.MAX_VALUE
                    tv.ellipsize = null
                }
                tv.text = spanned
            }
        )
    } else {
        Text(
            text = plain,
            modifier = modifier,
            style = typography.bodyMedium,
            color = colorScheme.onSurface,
            maxLines = maxLines
        )
    }
}

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    rich: Boolean = true,
    collapsedMaxLines: Int = 6,
    showReadMoreThreshold: Int = 300
) {
    var expanded by remember { mutableStateOf(false) }
    val hasHtml = remember(text) { HtmlUtils.containsHtml(text) }

    Column(modifier) {
        SmartText(
            text = text,
            rich = rich && hasHtml,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            ellipsizeEnd = !expanded
        )
        if (!expanded && text.length > showReadMoreThreshold) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { expanded = true }) {
                Text( stringResource(R.string.read_more))
            }
        }
    }
}

@Composable
fun SmartExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    rich: Boolean = true,
    collapsedMaxLines: Int = 6,
    showReadMoreThreshold: Int = 300
) {
    val isTv = DeviceUtils.isTV(LocalContext.current.packageManager)
    if (!isTv) {
        ExpandableText(
            text = text,
            modifier = modifier,
            rich = rich,
            collapsedMaxLines = collapsedMaxLines,
            showReadMoreThreshold = showReadMoreThreshold
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val hasHtml = remember(text) { HtmlUtils.containsHtml(text) }
    val scroll = rememberScrollState()
    val textFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var textFocused by remember { mutableStateOf(false) }
    val ringAlpha by animateFloatAsState(if (textFocused) 1f else 0f, label = "tv_text_focus_alpha")

    Column(modifier.animateContentSize()) {
        if (!expanded) {
            SmartText(
                text = text,
                rich = rich && hasHtml,
                maxLines = collapsedMaxLines,
                ellipsizeEnd = true
            )
            if (text.length > showReadMoreThreshold) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.focusRequester(buttonFocus)
                ) { Text(stringResource(R.string.read_more)) }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .border(2.dp, colorScheme.primary.copy(ringAlpha), shapes.medium)
                    .background(
                        if (textFocused) colorScheme.primaryContainer.copy(alpha = 0.08f)
                        else Color.Transparent,
                        shapes.medium
                    )
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .focusRequester(textFocus)
                        .focusable()
                        .onFocusChanged { textFocused = it.isFocused }
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.DirectionUp -> {
                                    if (scroll.value > 0) {
                                        scope.launch {
                                            scroll.animateScrollTo((scroll.value - 200).coerceAtLeast(0))
                                        }
                                        true
                                    } else false
                                }
                                Key.DirectionDown -> {
                                    if (scroll.value < scroll.maxValue) {
                                        scope.launch {
                                            scroll.animateScrollTo((scroll.value + 200).coerceAtMost(scroll.maxValue))
                                        }
                                        true
                                    } else {
                                        // At bottom -> move to Show less
                                        scope.launch { buttonFocus.requestFocus() }
                                        true
                                    }
                                }
                                Key.Back, Key.Escape -> {
                                    expanded = false
                                    scope.launch {
                                        delay(80)
                                        buttonFocus.requestFocus()
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .padding(12.dp)
                ) {
                    SmartText(
                        text = text,
                        rich = rich && hasHtml,
                        maxLines = Int.MAX_VALUE,
                        ellipsizeEnd = false
                    )
                }
                // Optional simple arrows to hint scroll (appear only when focused)
                if (textFocused && scroll.canScrollBackward)
                    Text("↑", modifier = Modifier.align(Alignment.TopCenter), style = typography.labelMedium, color = colorScheme.primary)
                if (textFocused && scroll.canScrollForward)
                    Text("↓", modifier = Modifier.align(Alignment.BottomCenter), style = typography.labelMedium, color = colorScheme.primary)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    expanded = false
                    scope.launch { buttonFocus.requestFocus() }
                },
                modifier = Modifier
                    .focusRequester(buttonFocus)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                            scope.launch { textFocus.requestFocus() }
                            true
                        } else false
                    }
            ) { Text(stringResource(R.string.show_less)) }

            LaunchedEffect(expanded) {
                if (expanded) {
                    delay(120) // let layout settle
                    textFocus.requestFocus()
                }
            }
        }
    }
}