package app.flicky.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.InstallDesktop
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import app.flicky.R
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.AppUpdatePreference
import app.flicky.data.repository.SettingsRepository
import app.flicky.helper.openUrl
import app.flicky.helper.shareText
import app.flicky.install.TaskStage
import app.flicky.ui.components.DebugOverlay
import app.flicky.ui.components.SmartExpandableText
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.components.snackbar.SnackbarManager
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow


@Composable
fun AppDetailScreen(
    app: FDroidApp,
    installedVersionCode: Long?,
    stage: TaskStage?,
    onInstall: () -> Unit,
    onInstallVariant: (AppVariant) -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    onOpenCategory: (String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    variants: List<AppVariant>,
) {
    val cfg = LocalConfiguration.current
    val isWide = cfg.screenWidthDp >= 900
    val settings: SettingsRepository = koinInject()

    MyScreenScaffold(
        title = app.name,
        actions = {
            val ctx = LocalContext.current
            val packageNameLabel = stringResource(R.string.share_subject_package, app.packageName)
            val sourceLabel = stringResource(R.string.share_subject_source, app.repository)

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
                    ),
                    tint = if (isFavorite) colorScheme.error else colorScheme.onSurface
                )
            }

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
    ) { _ ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background
        ) {
            if (isWide) {
                DesktopLayout(
                    app = app,
                    installedVersionCode = installedVersionCode,
                    stage = stage,
                    onInstall = onInstall,
                    onInstallVariant = onInstallVariant,
                    onOpen = onOpen,
                    onCancel = onCancel,
                    onUninstall = onUninstall,
                    error = error,
                    onOpenCategory = onOpenCategory,
                    variants = variants,
                    settings = settings
                )
            } else {
                MobileLayout(
                    app = app,
                    installedVersionCode = installedVersionCode,
                    stage = stage,
                    onInstall = onInstall,
                    onInstallVariant = onInstallVariant,
                    onOpen = onOpen,
                    onCancel = onCancel,
                    onUninstall = onUninstall,
                    error = error,
                    onOpenCategory = onOpenCategory,
                    variants = variants,
                    settings = settings
                )
            }
        }
    }

    val settingsState by settings.settingsFlow.collectAsState(initial = AppSettings())
    Box(Modifier.fillMaxSize()) {
        DebugOverlay(visible = settingsState.showDebugInfo)
    }
}

@Composable
private fun DesktopLayout(
    app: FDroidApp,
    installedVersionCode: Long?,
    stage: TaskStage?,
    onInstall: () -> Unit,
    onInstallVariant: (AppVariant) -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    onOpenCategory: (String) -> Unit,
    variants: List<AppVariant>,
    settings: SettingsRepository,
) {
    Row(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.width(350.dp).fillMaxHeight(),
            color = colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AppHeader(app, installedVersionCode, stage, onInstall, onOpen, onCancel, onUninstall, error, 96.dp)
                }
                item { ChipsSection(app, installedVersionCode, onOpenCategory) }
                item { DetailsSection(app) }
                if (app.antiFeatures.isNotEmpty()) item { AntiFeaturesSection(app.antiFeatures) }
                item { LinksSection(app) }
            }
        }

        Box(Modifier.fillMaxHeight().width(1.dp)) {
            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = colorScheme.outlineVariant)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RightPaneContent(
                    app = app,
                    variants = variants,
                    installedVersionCode = installedVersionCode,
                    onInstallVariant = onInstallVariant,
                    settings = settings
                )
            }
        }
    }
}

@Composable
private fun MobileLayout(
    app: FDroidApp,
    installedVersionCode: Long?,
    stage: TaskStage?,
    onInstall: () -> Unit,
    onInstallVariant: (AppVariant) -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    onOpenCategory: (String) -> Unit,
    variants: List<AppVariant>,
    settings: SettingsRepository,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surface)) {
                AppHeader(
                    app = app,
                    installedVersionCode = installedVersionCode,
                    stage = stage,
                    onInstall = onInstall,
                    onOpen = onOpen,
                    onCancel = onCancel,
                    onUninstall = onUninstall,
                    error = error,
                    iconSize = 88.dp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        item { ChipsSection(app, installedVersionCode, onOpenCategory) }
        if (app.antiFeatures.isNotEmpty()) item { AntiFeaturesSection(app.antiFeatures) }
        item { LinksSection(app) }
        item { DetailsSection(app) }
        item {
            RightPaneContent(
                app = app,
                variants = variants,
                installedVersionCode = installedVersionCode,
                onInstallVariant = onInstallVariant,
                settings = settings
            )
        }
    }
}


@Composable
private fun RightPaneContent(
    app: FDroidApp,
    variants: List<AppVariant>,
    installedVersionCode: Long?,
    onInstallVariant: (AppVariant) -> Unit,
    settings: SettingsRepository
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (app.whatsNew.isNotBlank()) {
            SectionTitle(stringResource(R.string.whats_new))
            SmartExpandableText(text = app.whatsNew, rich = true, collapsedMaxLines = 8)
        }
        if (app.summary.isNotBlank()) {
            SectionTitle(stringResource(R.string.overview))
            Text(app.summary, style = typography.bodyMedium, color = colorScheme.onBackground)
        }
        if (app.screenshots.isNotEmpty()) {
            ScreenshotsSection(app.screenshots)
        }
        if (app.description.isNotBlank()) {
            SectionTitle(stringResource(R.string.about))
            SmartExpandableText(text = app.description, rich = true, collapsedMaxLines = 10)
        }

        if (variants.isNotEmpty()) {
            SectionTitle(stringResource(id = R.string.versions))
            VersionsSection(
                variants = variants.take(8),
                installedVersionCode = installedVersionCode,
                onInstallVariant = onInstallVariant
            )
            PreferredSourceSection(
                pkg = app.packageName,
                variants = variants,
                settings = settings
            )
        }
    }
}

@Composable
private fun AppHeader(
    app: FDroidApp,
    installedVersionCode: Long?,
    stage: TaskStage?,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    iconSize: Dp,
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
                    style = typography.titleLarge,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.author.isNotBlank()) {
                    Text(
                        app.author,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        val progressValue = when (stage) {
            is TaskStage.Downloading -> stage.progress.coerceIn(0f, 0.999f)
            is TaskStage.Verifying -> 0.995f
            is TaskStage.Installing -> (0.99f + 0.01f * stage.progress).coerceIn(0.99f, 1f)
            else -> -1f
        }
        val showBar = stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled && progressValue >= 0f

        if (showBar) {
            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            val label = when (stage) {
                is TaskStage.Downloading -> "Downloading"
                is TaskStage.Verifying -> "Verifying"
                is TaskStage.Installing -> "Installing"
                is TaskStage.Finished -> if (stage.success) "Completed" else "Failed"
                is TaskStage.Cancelled -> "Cancelled"
                else -> "Working"
            }
            val showPercent = stage is TaskStage.Downloading || stage is TaskStage.Installing
            val percent = (progressValue * 100).toInt()
            Text(
                if (showPercent) "$label $percent%" else label,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_cancel))
                }
            }
        } else {
            if (installedVersionCode != null) {
                val hasUpdate = app.versionCode > installedVersionCode
                Row {
                    if (hasUpdate) {
                        FilledTonalButton(onClick = onInstall) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardDoubleArrowUp,
                                contentDescription = stringResource(R.string.action_update)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_open))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_uninstall))
                    }
                }
            } else {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.InstallDesktop,
                        contentDescription = stringResource(R.string.action_install)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_install))
                }
            }
        }
        error?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = colorScheme.error, style = typography.bodySmall)
        }
    }
}

@Composable
private fun ChipsSection(
    app: FDroidApp,
    installedVersionCode: Long?,
    onOpenCategory: (String) -> Unit
) {
    Column {
        SectionTitle(stringResource(R.string.info))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedAssistChip(
                onClick = {},
                label = { Text(if (app.version.startsWith("v", true)) app.version else "v${app.version}") }
            )
            ElevatedAssistChip(onClick = {}, label = { Text(formatBytes(app.size)) })
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (app.website.isNotBlank()) AssistChip(
                onClick = { openUrl(ctx, app.website) },
                label = { Text(stringResource(R.string.website)) }
            )
            if (app.sourceCode.isNotBlank()) AssistChip(
                onClick = { openUrl(ctx, app.sourceCode) },
                label = { Text(stringResource(R.string.source_code)) }
            )
            if (app.repositoryUrl.isNotBlank()) AssistChip(
                onClick = { openUrl(ctx, app.repositoryUrl) },
                label = { Text(stringResource(R.string.repository_url)) }
            )
            if (app.license.isNotBlank()) AssistChip(
                onClick = { openUrl(ctx, resolveLicenseLink(app.license)) },
                label = { Text(stringResource(R.string.license)) }
            )
            AssistChip(
                onClick = { openAppSettings(ctx, app.packageName) },
                label = { Text(stringResource(R.string.permissions)) }
            )
            AssistChip(
                onClick = { openUrl(ctx, exodusReportUrl(app.packageName)) },
                label = { Text(stringResource(R.string.exodus_privacy)) }
            )
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
                        .widthIn(max = 200.dp)
                        .heightIn(max = 400.dp)
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
        Dialog(
            onDismissRequest = { showViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
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
        style = typography.titleSmall,
        color = colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

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
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 96.dp).wrapContentWidth(Alignment.Start)
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = typography.bodyMedium, color = colorScheme.onSurface)
    }
}

fun formatBytes(bytes: Long): String {
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

private fun resolveLicenseLink(raw: String): String {
    val id = raw.trim()
    if (id.startsWith("http://") || id.startsWith("https://")) return id

    val firstToken = id.split(" ", "OR", "AND", "/", "|", ",")
        .map { it.trim() }
        .firstOrNull { it.matches(Regex("^[A-Za-z0-9.+-]+$")) }
        ?: id

    val spdxUrl = "https://spdx.org/licenses/$firstToken.html"
    val looksSpdx = firstToken.matches(Regex("^[A-Za-z0-9.+-]+$"))
    return if (looksSpdx) spdxUrl
    else "https://www.duckduckgo.com/search?q=" + URLEncoder.encode("$id license", "UTF-8")
}

private fun exodusReportUrl(packageName: String) =
    "https://reports.exodus-privacy.eu.org/en/reports/$packageName/latest/"

private fun openAppSettings(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:$packageName".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun VersionsSection(
    variants: List<AppVariant>,
    installedVersionCode: Long?,
    onInstallVariant: (AppVariant) -> Unit
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarManager: SnackbarManager = koinInject()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        variants.forEach { v ->
            val installed = installedVersionCode?.let { v.versionCode.toLong() == it } == true
            val compat = v.isCompatible
            var showMenu by remember { mutableStateOf(false) }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("v${v.versionName} (${v.versionCode})", style = typography.bodyLarge)
                        Text(
                            "${v.repositoryName} • ${formatBytes(v.size)}",
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (installed) {
                            Text(
                                "✓ Installed",
                                style = typography.labelSmall,
                                color = colorScheme.primary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                containerColor = colorScheme.background,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy URL") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        clipboard.nativeClipboard.text = AnnotatedString(v.apkUrl)
                                        snackbarManager.show("URL copied to clipboard")
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        shareText(ctx, v.apkUrl)
                                        showMenu = false
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        Button(
                            onClick = { onInstallVariant(v) },
                            enabled = compat && !installed,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text(if (installed) "Installed" else "Install")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferredSourceSection(
    pkg: String,
    variants: List<AppVariant>,
    settings: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val pref by settings.observeAppUpdatePreference(pkg)
        .collectAsState(initial = AppUpdatePreference())

    var showMenu by remember { mutableStateOf(false) }

    val repos = variants
        .map { it.repositoryName to it.repositoryUrl.trim().trimEnd('/') }
        .distinct()

    val pinnedLabel = remember(pref, repos) {
        pref.preferredRepoUrl?.let { url ->
            repos.firstOrNull { it.second.equals(url.trimEnd('/'), ignoreCase = true) }?.first ?: url
        } ?: "Auto"
    }

    Column {
        SectionTitle(stringResource(R.string.preferred_source))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { showMenu = true },
                label = { Text(pinnedLabel) }
            )
            FilterChip(
                selected = pref.lockToRepo,
                onClick = {
                    scope.launch {
                        settings.updateAppUpdatePreference(pkg) {
                            it.copy(lockToRepo = !it.lockToRepo)
                        }
                    }
                },
                label = { Text(stringResource(R.string.lock_to_repo)) }
            )
        }
        DropdownMenu(expanded = showMenu,
            containerColor = colorScheme.background,
            onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.auto)) },
                onClick = {
                    scope.launch {
                        settings.setPreferredRepo(pkg, null, lock = true)
                    }
                    showMenu = false
                }
            )
            repos.forEach { (name, url) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        scope.launch {
                            settings.setPreferredRepo(pkg, url, lock = false)
                        }
                        showMenu = false
                    }
                )
            }
        }
    }
}