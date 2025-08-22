package app.flicky.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flicky.data.model.FDroidApp
import app.flicky.helper.openUrl
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.res.painterResource
import app.flicky.ui.components.SmartExpandableText
import kotlin.math.log10
import kotlin.math.pow
import app.flicky.R

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
                    Text(
                        app.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isWide) {
                DesktopLayout(
                    app = app,
                    installedVersionCode = installedVersionCode,
                    isInstalling = isInstalling,
                    progress = progress,
                    onInstall = onInstall,
                    onOpen = onOpen,
                    onUninstall = onUninstall,
                    error = error
                )
            } else {
                MobileLayout(
                    app = app,
                    installedVersionCode = installedVersionCode,
                    isInstalling = isInstalling,
                    progress = progress,
                    onInstall = onInstall,
                    onOpen = onOpen,
                    onUninstall = onUninstall,
                    error = error
                )
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
        // Left side panel
        Surface(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AppHeader(
                        app = app,
                        installedVersionCode = installedVersionCode,
                        isInstalling = isInstalling,
                        progress = progress,
                        onInstall = onInstall,
                        onOpen = onOpen,
                        onUninstall = onUninstall,
                        error = error,
                        iconSize = 96.dp
                    )
                }
                item { ChipsSection(app) }
                item { DetailsSection(app) }
                if (app.antiFeatures.isNotEmpty()) {
                    item { AntiFeaturesSection(app.antiFeatures) }
                }
                if (app.website.isNotBlank() || app.sourceCode.isNotBlank()) {
                    item { LinksSection(app) }
                }
            }
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        // Right content panel
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (app.summary.isNotBlank()) {
                item {
                    Column {
                        SectionTitle("Overview")
                        Text(
                            app.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            if (app.whatsNew.isNotBlank()) {
                item {
                    Column {
                        SectionTitle("What's new")
                        SmartExpandableText(app.whatsNew)
                    }
                }
            }
            if (app.screenshots.isNotEmpty()) {
                item { ScreenshotsSection(app.screenshots) }
            }
            if (app.description.isNotBlank()) {
                item {
                    Column {
                        SectionTitle("About")
                        SmartExpandableText(app.description)
                    }
                }
            }
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
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                AppHeader(
                    app = app,
                    installedVersionCode = installedVersionCode,
                    isInstalling = isInstalling,
                    progress = progress,
                    onInstall = onInstall,
                    onOpen = onOpen,
                    onUninstall = onUninstall,
                    error = error,
                    iconSize = 88.dp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        item { ChipsSection(app) }
        if (app.summary.isNotBlank()) {
            item {
                Column {
                    SectionTitle("Overview")
                    Text(
                        app.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        if (app.whatsNew.isNotBlank()) {
            item {
                Column {
                    SectionTitle("What's new")
                    SmartExpandableText(app.whatsNew)
                }
            }
        }
        if (app.screenshots.isNotEmpty()) {
            item { ScreenshotsSection(app.screenshots) }
        }
        if (app.description.isNotBlank()) {
            item {
                Column {
                    SectionTitle("About")
                    SmartExpandableText(app.description)
                }
            }
        }
        item { DetailsSection(app) }
        if (app.antiFeatures.isNotEmpty()) {
            item { AntiFeaturesSection(app.antiFeatures) }
        }
        if (app.website.isNotBlank() || app.sourceCode.isNotBlank()) {
            item { LinksSection(app) }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (app.author.isNotBlank()) {
                    Text(
                        app.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                "Installing… ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (installedVersionCode != null) {
                Row {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onUninstall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Uninstall")
                    }
                }
            } else {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install")
                }
            }
        }

        error?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSection(app: FDroidApp) {
    Column {
        SectionTitle("Info")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedAssistChip(onClick = {}, label = { Text("v${app.version}") })
            ElevatedAssistChip(onClick = {}, label = { Text(formatBytes(app.size)) })
            if (app.license.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text(app.license) })
            }
            AssistChip(onClick = {}, label = { Text(app.repository) })
            if (app.category.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text(app.category) })
            }
        }
    }
}

@Composable
private fun DetailsSection(app: FDroidApp) {
    Column {
        SectionTitle("Details")
        InfoRow("Package", app.packageName)
        InfoRow("Version code", app.versionCode.toString())
        if (app.lastUpdated > 0) InfoRow("Updated", formatDate(app.lastUpdated))
        if (app.added > 0) InfoRow("Added", formatDate(app.added))
    }
}

@Composable
private fun AntiFeaturesSection(tags: List<String>) {
    Column {
        SectionTitle("Anti-features")
        Spacer(Modifier.height(4.dp))
        AssistChipsFlow(tags)
    }
}

@Composable
private fun LinksSection(app: FDroidApp) {
    val ctx = LocalContext.current
    Column {
        SectionTitle("Links")
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.website.isNotBlank()) {
                AssistChip(onClick = { openUrl(ctx, app.website) }, label = { Text("Website") })
            }
            if (app.sourceCode.isNotBlank()) {
                AssistChip(onClick = { openUrl(ctx, app.sourceCode) }, label = { Text("Source Code") })
            }
        }
    }
}

@Composable
private fun ScreenshotsSection(urls: List<String>) {
    var showViewer by remember { mutableStateOf(false) }
    var startIndex by remember { mutableIntStateOf(0) }

    Column {
        SectionTitle("Screenshots")
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(urls) { url ->
                val idx = urls.indexOf(url)
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(260.dp)
                        .clickable {
                            startIndex = idx
                            showViewer = true
                        }
                )
            }
        }
    }

    if (showViewer) {
        FullscreenImageViewer(
            images = urls,
            initialPage = startIndex,
            onClose = { showViewer = false }
        )
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
        items.forEach { tag ->
            AssistChip(onClick = {}, label = { Text(tag) })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 96.dp).wrapContentWidth(Alignment.Start)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
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