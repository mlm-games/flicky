package app.flicky.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.helper.openUrl
import app.flicky.helper.shareText
import app.flicky.ui.components.SmartExpandableText
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: FDroidApp,
    installedVersionCode: Long?,
    isInstalling: Boolean,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    error: String?
) {
    val cfg = LocalConfiguration.current
    val isWide = cfg.screenWidthDp >= 900

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    val ctx = LocalContext.current
                    val packageNameLabel = stringResource(R.string.share_subject_package, app.packageName)
                    val sourceLabel = stringResource(R.string.share_subject_source, app.repository)

                    IconButton(onClick = {
                        val shareTextContent = buildString {
                            append(app.name).append("\n")
                            append(packageNameLabel).append("\n")
                            if (app.website.isNotBlank()) append(app.website).append("\n")
                            append(sourceLabel)
                        }
                        shareText(ctx, shareTextContent)
                    }) {
                        Icon(
                            painterResource(android.R.drawable.ic_menu_share),
                            contentDescription = stringResource(R.string.action_share)
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isWide) {
                DesktopLayout(app, installedVersionCode, isInstalling, progress, onInstall, onOpen, onUninstall, error)
            } else {
                MobileLayout(app, installedVersionCode, isInstalling, progress, onInstall, onOpen, onUninstall, error)
            }
        }
    }
}


@Composable
private fun DesktopLayout(
    app: FDroidApp,
    installedVersionCode: Long?,
    isInstalling: Boolean,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    error: String?
) {
    Row(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.width(380.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AppHeader(app, installedVersionCode, isInstalling, progress, onInstall, onOpen, onUninstall, error, 96.dp)
                }
                item { ChipsSection(app) }
                item { DetailsSection(app) }
                if (app.antiFeatures.isNotEmpty()) item { AntiFeaturesSection(app.antiFeatures) }
                if (app.website.isNotBlank() || app.sourceCode.isNotBlank()) item { LinksSection(app) }
            }
        }

        Box(Modifier.fillMaxHeight().width(1.dp)) {
            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { RightPaneContent(app) }
        }
    }
}

@Composable
private fun MobileLayout(
    app: FDroidApp,
    installedVersionCode: Long?,
    isInstalling: Boolean,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    error: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                AppHeader(app, installedVersionCode, isInstalling, progress, onInstall, onOpen, onUninstall, error, 88.dp, Modifier.padding(16.dp))
            }
        }
        item { ChipsSection(app) }
        item { RightPaneContent(app) }
    }
}


@Composable
private fun RightPaneContent(app: FDroidApp) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (app.summary.isNotBlank()) {
            SectionTitle(stringResource(R.string.overview))
            Text(app.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (app.whatsNew.isNotBlank()) {
            SectionTitle(stringResource(R.string.whats_new))
            SmartExpandableText(text = app.whatsNew, rich = true, collapsedMaxLines = 8)
        }
        if (app.screenshots.isNotEmpty()) {
            ScreenshotsSection(app.screenshots)
        }
        if (app.description.isNotBlank()) {
            SectionTitle(stringResource(R.string.about))
            SmartExpandableText(text = app.description, rich = true, collapsedMaxLines = 10)
        }
    }
}

@Composable
private fun AppHeader(
    app: FDroidApp,
    installedVersionCode: Long?,
    isInstalling: Boolean,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.name,
                modifier = Modifier.size(iconSize),
                placeholder = painterResource(R.drawable.ic_app_placeholder),
                error = painterResource(R.drawable.ic_app_placeholder)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.author.isNotBlank()) {
                    Text(app.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (isInstalling) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.installing_progress, (progress * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (installedVersionCode != null) {
                Row {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_open)) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_uninstall)) }
                }
            } else {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_install)) }
            }
        }
        error?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSection(app: FDroidApp) {
    Column {
        SectionTitle(stringResource(R.string.info))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedAssistChip(onClick = {}, label = { Text(stringResource(R.string.version_prefix, app.version)) })
            ElevatedAssistChip(onClick = {}, label = { Text(formatBytes(app.size)) })
            if (app.license.isNotBlank()) AssistChip(onClick = {}, label = { Text(app.license) })
            AssistChip(onClick = {}, label = { Text(app.repository) })
            if (app.category.isNotBlank()) AssistChip(onClick = {}, label = { Text(app.category) })
        }
    }
}

@Composable
private fun DetailsSection(app: FDroidApp) {
    Column {
        SectionTitle(stringResource(R.string.details))
        InfoRow(stringResource(R.string.package_name), app.packageName)
        InfoRow(stringResource(R.string.version_code), app.versionCode.toString())
        if (app.lastUpdated > 0) InfoRow(stringResource(R.string.updated), formatDate(app.lastUpdated))
        if (app.added > 0) InfoRow(stringResource(R.string.added), formatDate(app.added))
    }
}

@Composable
private fun AntiFeaturesSection(tags: List<String>) {
    Column {
        SectionTitle(stringResource(R.string.anti_features))
        Spacer(Modifier.height(4.dp))
        AssistChipsFlow(tags)
    }
}

@Composable
private fun LinksSection(app: FDroidApp) {
    val ctx = LocalContext.current
    Column {
        SectionTitle(stringResource(R.string.links))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.website.isNotBlank()) AssistChip(onClick = { openUrl(ctx, app.website) }, label = { Text(stringResource(R.string.website)) })
            if (app.sourceCode.isNotBlank()) AssistChip(onClick = { openUrl(ctx, app.sourceCode) }, label = { Text(stringResource(R.string.source_code)) })
            if (app.repositoryUrl.isNotBlank()) AssistChip(onClick = { openUrl(ctx, app.repositoryUrl) }, label = { Text(stringResource(R.string.repository_url)) })

        }
    }
}

@Composable
private fun ScreenshotsSection(urls: List<String>) {
    var showViewer by rememberSaveable { mutableStateOf(false) }
    var startIndex by rememberSaveable { mutableIntStateOf(0) }

    Column {
        SectionTitle(stringResource(R.string.screenshots))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(urls, key = { idx, url -> "$idx-$url" }) { index, url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(260.dp)
                        .clickable {
                            startIndex = index
                            showViewer = true
                        },
                    placeholder = painterResource(R.drawable.ic_app_placeholder),
                    error = painterResource(R.drawable.ic_app_placeholder)
                )
            }
        }
    }
    if (showViewer) {
        Dialog(onDismissRequest = { showViewer = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                FullscreenImageViewer(
                    images = urls,
                    initialPage = startIndex,
                    onClose = { showViewer = false }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistChipsFlow(items: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 96.dp).wrapContentWidth(Alignment.Start)
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.getDefault(), if (digitGroups <= 1) "%.0f %s" else "%.1f %s", value, units[digitGroups])
}

private fun formatDate(epochMillis: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date(epochMillis))
    } catch (_: Exception) {
        epochMillis.toString()
    }
}