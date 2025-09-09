package app.flicky.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InstallDesktop
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.core.net.toUri
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.helper.openUrl
import app.flicky.helper.shareText
import app.flicky.install.TaskStage
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
    stage: TaskStage?,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    onOpenCategory: (String) -> Unit,
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
                DesktopLayout(app, installedVersionCode, isInstalling, stage, progress, onInstall, onOpen, onCancel, onUninstall, error, onOpenCategory)
            } else {
                MobileLayout(app, installedVersionCode, isInstalling, stage, progress, onInstall, onOpen, onCancel,  onUninstall, error, onOpenCategory)
            }
        }
    }
}

@Composable
private fun DesktopLayout(
    app: FDroidApp,
    installedVersionCode: Long?,
    isInstalling: Boolean,
    stage: TaskStage?,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    onOpenCategory: (String) -> Unit
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
                    AppHeader(app, installedVersionCode, isInstalling, stage, progress, onInstall, onOpen, onCancel, onUninstall, error, 96.dp)
                }
                item { ChipsSection(app, installedVersionCode, onOpenCategory) }
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
    stage: TaskStage?,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    error: String?,
    onOpenCategory: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                AppHeader(
                    app = app,
                    installedVersionCode = installedVersionCode,
                    isInstalling = isInstalling,
                    stage = stage,
                    progress = progress,
                    onInstall = onInstall,
                    onOpen = onOpen,
                    onCancel =  onCancel,
                    onUninstall = onUninstall,
                    error = error,
                    iconSize = 88.dp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        item { ChipsSection(app, installedVersionCode, onOpenCategory) }
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
    stage: TaskStage?,
    progress: Float,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
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
        val installingNow = stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled
        if (installingNow) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
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
            val percent = (progress * 100).toInt()
            Text(
                if (showPercent) "$label $percent%" else label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onCancel, Modifier.fillMaxWidth()) {
                    Icon(
                        painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_cancel)
                    )
                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_cancel))
                }
            }
        } else {
            if (installedVersionCode != null) {
                val hasUpdate = app.versionCode > installedVersionCode // for readability
//                val compact = LocalConfiguration.current.screenWidthDp < 360
                Row {
                    if (hasUpdate) {
                        FilledTonalButton(onClick = onInstall) {
                            Icon( // Size issues
                                imageVector = Icons.Outlined.KeyboardDoubleArrowUp,
                                contentDescription = stringResource(R.string.action_update) //else null
                            )
//                            if (!compact) {
//                                Spacer(Modifier.width(8.dp))
//                                Text(stringResource(R.string.action_update))
//                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
//                            contentDescription = if (compact) stringResource(R.string.action_open) else null
//                        )
//                        if (!compact) {
//                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_open))
//                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) {
//                        Icon(
//                            imageVector = Icons.Outlined.DeleteOutline,
//                            contentDescription = if (compact) stringResource(R.string.action_uninstall) else null
//                        )
//                        if (!compact) {
//                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_uninstall))
//                        }
                    }
                }
            } else {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.InstallDesktop, //else Icons.Outlined.InstallMobile
                        contentDescription = stringResource(R.string.action_uninstall)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_install))
                }
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
private fun ChipsSection(
    app: FDroidApp,
    installedVersionCode: Long?,
    onOpenCategory: (String) -> Unit
) {
    val ctx = LocalContext.current
    Column {
        SectionTitle(stringResource(R.string.info))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedAssistChip(onClick = {}, label = { Text(if (app.version.startsWith("v", ignoreCase = true)) app.version else "v${app.version}") })
            ElevatedAssistChip(onClick = {}, label = { Text(formatBytes(app.size)) })

            if (app.license.isNotBlank()) {
                AssistChip(
                    onClick = { openUrl(ctx, resolveLicenseLink(app.license)) },
                    label = { Text(app.license) }
                )
            }

            AssistChip(onClick = { /* maybe later */ }, label = { Text(app.repository) })
            if (app.category.isNotBlank()) {
                AssistChip(onClick = { onOpenCategory(app.category) }, label = { Text(app.category) })
            }

            AssistChip(
                onClick = { openUrl(ctx, exodusReportUrl(app.packageName)) },
                label = { Text(stringResource(R.string.exodus_privacy)) }
            )

            AssistChip(
                onClick = {
                    if (installedVersionCode != null) {
                        openAppSettings(ctx, app.packageName)
                    } else {
//                        openUrl(ctx, exodusReportUrl(app.packageName))
                        Toast.makeText(ctx, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
                    }
                },
                label = { Text(stringResource(R.string.permissions)) }
            )
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
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.primary,
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
            color = colorScheme.onSurfaceVariant,
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

private fun resolveLicenseLink(raw: String): String {
    val id = raw.trim()

    //  already a URL? (from fdroid index?) maybe in future
    if (id.startsWith("http://") || id.startsWith("https://")) return id

    val firstToken = id.split(" ", "OR", "AND", "/", "|", ",")
        .map { it.trim() }
        .firstOrNull { it.matches(Regex("^[A-Za-z0-9.+-]+$")) }
        ?: id

    val spdxUrl = "https://spdx.org/licenses/$firstToken.html"

    val looksSpdx = firstToken.matches(Regex("^[A-Za-z0-9.+-]+$"))
    return if (looksSpdx) spdxUrl
    else "https://www.duckduckgo.com/search?q=" +
            java.net.URLEncoder.encode("$id license", "UTF-8")
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
