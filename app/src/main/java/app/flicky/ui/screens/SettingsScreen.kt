package app.flicky.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.local.RepoConfig
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.MirrorRegistry
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.Setting
import app.flicky.data.repository.SettingCategory
import app.flicky.data.repository.SettingType
import app.flicky.data.repository.SettingsManager
import app.flicky.ui.components.global.ConfirmationDialog
import app.flicky.ui.components.global.DropdownSettingDialog
import app.flicky.ui.components.global.FlickyDialog
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.components.global.SettingsAction
import app.flicky.ui.components.global.SettingsItem
import app.flicky.ui.components.global.SettingsSection
import app.flicky.ui.components.global.SettingsToggle
import app.flicky.ui.components.global.SliderSettingDialog
import app.flicky.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty1
import kotlin.system.measureTimeMillis

private data class ProbeResult(
    val url: String,
    val ok: Boolean,
    val code: Int,
    val ms: Long
)

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    val repos by vm.repositories.collectAsState()
    val manager = remember { SettingsManager() }
    val scope = rememberCoroutineScope()

    var showDropdown by remember { mutableStateOf(false) }
    var showSlider by remember { mutableStateOf(false) }
    var currentProp by remember { mutableStateOf<KProperty1<AppSettings, *>?>(null) }
    var currentAnn by remember { mutableStateOf<Setting?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showAddRepo by remember { mutableStateOf(false) }

    val grouped = remember { manager.getByCategory() }
    val cfg = LocalConfiguration.current

    val isTablet = cfg.screenWidthDp >= 600
    val gridCells = if (isTablet) {
        GridCells.Adaptive(minSize = 400.dp)
    } else {
        GridCells.Fixed(1)
    }

    val context = LocalContext.current

    // Load all repo configs at once
    val repoConfigs by produceState(
        initialValue = emptyMap(),
        key1 = repos
    ) {
        value = withContext(Dispatchers.IO) {
            val dao = AppGraph.db.repoConfigDao()
            repos.associate { repo ->
                val base = repo.url.trimEnd('/')
                base to (dao.get(base) ?: RepoConfig(
                    baseUrl = base,
                    enabled = repo.enabled
                ))
            }
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SettingsViewModel.UiEvent.Toast -> {
                    Toast.makeText(
                        context,
                        context.getString(event.messageResId),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is SettingsViewModel.UiEvent.RequestExport -> {
                    // TODO: Implement settings export
                }
            }
        }
    }

    MyScreenScaffold(
        title = stringResource(R.string.nav_settings),
        actions = {
//            IconButton(onClick = { /* TODO: Search settings */ }) {
//                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
//            }
        }
    ) {
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Settings by category
            for (category in SettingCategory.entries) {
                val itemsForCat = grouped[category] ?: emptyList()
                if (itemsForCat.isEmpty()) continue

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsSection(
                        title = when (category) {
                            SettingCategory.APPEARANCE -> stringResource(R.string.category_appearance)
                            SettingCategory.GENERAL -> stringResource(R.string.category_general)
                            SettingCategory.DOWNLOADS -> stringResource(R.string.category_downloads)
                            SettingCategory.FILTERS -> stringResource(R.string.category_filters)
                            SettingCategory.SYNC -> stringResource(R.string.category_sync)
                            SettingCategory.PROXY -> stringResource(R.string.category_proxy)
                            SettingCategory.OTHER -> stringResource(R.string.category_other)
                        }
                    ) {
                        // Content is added as items below
                    }
                }

                items(itemsForCat, key = { it.first.name }) { (prop, ann) ->
                    val enabled = manager.isEnabled(settings, prop, ann)

                    when (ann.type) {
                        SettingType.TOGGLE -> {
                            val value = prop.get(settings) as? Boolean ?: false
                            SettingsToggle(
                                title = ann.title,
                                description = ann.description.takeIf { it.isNotBlank() },
                                isChecked = value,
                                enabled = enabled,
                                onCheckedChange = { vm.updateSetting(prop.name, it) }
                            )
                        }

                        SettingType.DROPDOWN -> {
                            val idx = prop.get(settings) as? Int ?: 0
                            val options = ann.options.toList()
                            SettingsItem(
                                title = ann.title,
                                subtitle = options.getOrNull(idx) ?: stringResource(R.string.unknown),
                                description = ann.description.takeIf { it.isNotBlank() },
                                enabled = enabled,
                                onClick = {
                                    currentProp = prop
                                    currentAnn = ann
                                    showDropdown = true
                                }
                            )
                        }

                        SettingType.SLIDER -> {
                            val valueText = when (val v = prop.get(settings)) {
                                is Int -> v.toString()
                                is Float -> String.format(Locale.getDefault(), "%.1f", v)
                                else -> ""
                            }
                            SettingsItem(
                                title = ann.title,
                                subtitle = valueText,
                                description = ann.description.takeIf { it.isNotBlank() },
                                enabled = enabled,
                                onClick = {
                                    currentProp = prop
                                    currentAnn = ann
                                    showSlider = true
                                }
                            )
                        }

                        SettingType.BUTTON -> {
                            SettingsAction(
                                title = ann.title,
                                description = ann.description.takeIf { it.isNotBlank() },
                                buttonText = stringResource(R.string.run),
                                enabled = enabled,
                                onClick = { vm.performAction(prop.name) }
                            )
                        }
                    }
                }
            }

            // Repositories section
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.repositories),
                    style = typography.titleMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                )
            }

            // Repository cards
            items(repos, key = { it.url }) { repo ->
                val base = repo.url.trimEnd('/')
                val config = repoConfigs[base] ?: RepoConfig(
                    baseUrl = base,
                    enabled = repo.enabled
                )

                RepoConfigCard(
                    repo = repo,
                    config = config,
                    onConfigChange = { newCfg ->
                        scope.launch {
                            AppGraph.db.repoConfigDao().upsert(newCfg)
                            vm.reloadConfigs()
                        }
                    },
                    onToggle = { vm.toggleRepository(repo.url) },
                    onTestMirrors = {
                        scope.launch {
                            val results = testRepoMirrors(base)
                            val message = buildString {
                                results.forEach { (url, ok, code, ms) ->
                                    append(if (ok) "✓" else "✗")
                                    append(" ").append(url).append("\n")
                                    append("   ")
                                    append(if (ok) "${ms}ms (HTTP $code)" else "HTTP $code / fail")
                                    append("\n")
                                }
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onForgetMirror = {
                        MirrorRegistry.clear(base)
                        Toast.makeText(
                            context,
                            context.getString(R.string.forgot_mirror, repo.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // Repository actions
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showAddRepo = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_repository))
                    }

                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.reset_to_defaults))
                    }
                }
            }
        }
    }

    // Dialogs
    if (showDropdown && currentProp != null && currentAnn != null) {
        val prop = currentProp!!
        val ann = currentAnn!!
        val idx = prop.get(settings) as? Int ?: 0

        DropdownSettingDialog(
            title = ann.title,
            options = ann.options.toList(),
            selectedIndex = idx,
            onDismiss = { showDropdown = false },
            onOptionSelected = { i ->
                vm.updateSetting(prop.name, i)
                showDropdown = false
            }
        )
    }

    if (showSlider && currentProp != null && currentAnn != null) {
        val prop = currentProp!!
        val ann = currentAnn!!
        val cur = when (val v = prop.get(settings)) {
            is Int -> v.toFloat()
            is Float -> v
            else -> 0f
        }

        SliderSettingDialog(
            title = ann.title,
            currentValue = cur,
            min = ann.min,
            max = ann.max,
            step = ann.step,
            onDismiss = { showSlider = false },
            onValueSelected = { value ->
                when (prop.returnType.classifier) {
                    Int::class -> vm.updateSetting(prop.name, value.toInt())
                    Float::class -> vm.updateSetting(prop.name, value)
                }
                showSlider = false
            }
        )
    }

    if (showAddRepo) {
        AddRepoDialog(
            onDismiss = { showAddRepo = false },
            onAdd = { name, url ->
                scope.launch {
                    vm.addRepository(name, url)
                    AppGraph.mirrorPolicyProvider.ensureDefault(url.trimEnd('/'))
                }
                showAddRepo = false
            }
        )
    }

    if (showResetConfirm) {
        ConfirmationDialog(
            title = stringResource(R.string.reset_repositories),
            message = stringResource(R.string.reset_repositories_confirm),
            confirmText = stringResource(R.string.reset),
            dismissText = stringResource(R.string.action_cancel),
            isDangerous = true,
            onConfirm = {
                showResetConfirm = false
                vm.resetRepositoriesToDefaults()
            },
            onDismiss = { showResetConfirm = false }
        )
    }
}

@Composable
private fun RepoConfigCard(
    repo: RepositoryInfo,
    config: RepoConfig,
    onConfigChange: (RepoConfig) -> Unit,
    onToggle: () -> Unit,
    onTestMirrors: () -> Unit,
    onForgetMirror: () -> Unit
) {
    var localConfig by remember(config) { mutableStateOf(config) }

    var showMenu by remember { mutableStateOf(false) }
    var openStrategy by remember { mutableStateOf(false) }
    var openTrust by remember { mutableStateOf(false) }

    val updateConfig: (RepoConfig) -> Unit = { newConfig -> // HACK: For instant ui updates (perf. cost)
        localConfig = newConfig
        onConfigChange(newConfig)
    }

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header with name and switch
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(repo.name, style = typography.bodyLarge)
                    Text(
                        repo.url,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Menu button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options_for, repo.name),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.test_ping)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onTestMirrors()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forget_last_mirror)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onForgetMirror()
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Enable/disable switch
                    Switch(
                        checked = repo.enabled,
                        onCheckedChange = { onToggle() }
                    )
                }
            }

            if (repo.enabled) {
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = localConfig.rotateMirrors,
                        onClick = {
                            updateConfig(localConfig.copy(rotateMirrors = !localConfig.rotateMirrors))
                        },
                        label = { Text(stringResource(R.string.rotate_mirrors)) }
                    )

                    FilterChip(
                        selected = localConfig.includeOnion,
                        onClick = {
                            updateConfig(localConfig.copy(includeOnion = !localConfig.includeOnion))
                        },
                        label = { Text(stringResource(R.string.use_onion)) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Mirror strategy dropdown
                val strategies = listOf("StickyLastGood", "RoundRobin", "CanonicalFirst")
                val strategyIdx = strategies.indexOf(localConfig.strategy).coerceAtLeast(0)

                ExposedDropdownMenuBox(
                    expanded = openStrategy,
                    onExpandedChange = { openStrategy = !openStrategy },
                ) {
                    OutlinedTextField(
                        value = strategies[strategyIdx],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.mirror_strategy)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = openStrategy)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { openStrategy = !openStrategy }
                    )

                    ExposedDropdownMenu(
                        expanded = openStrategy,
                        onDismissRequest = { openStrategy = false }
                    ) {
                        strategies.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    openStrategy = false
                                    updateConfig(localConfig.copy(strategy = s))
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Trust options
                Text(
                    stringResource(R.string.trust),
                    style = typography.labelLarge
                )

                Spacer(Modifier.height(6.dp))

                val trustModes = listOf("HttpsOnly", "Pinned", "CustomCA")
                val trustIdx = trustModes.indexOf(localConfig.trustMode).coerceAtLeast(0)

                ExposedDropdownMenuBox(
                    expanded = openTrust,
                    onExpandedChange = { openTrust = !openTrust }
                ) {
                    OutlinedTextField(
                        value = trustModes[trustIdx],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.trust_mode)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = openTrust)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { openTrust = !openTrust }
                    )

                    ExposedDropdownMenu(
                        expanded = openTrust,
                        onDismissRequest = { openTrust = false }
                    ) {
                        trustModes.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    openTrust = false
                                    updateConfig(config.copy(trustMode = s))
                                }
                            )
                        }
                    }
                }

                // Additional fields based on trust mode
                when (config.trustMode) {
                    "Pinned" -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config.pins,
                            onValueChange = {
                                onConfigChange(config.copy(pins = it))
                            },
                            label = { Text(stringResource(R.string.pins_label)) },
                            placeholder = { Text(stringResource(R.string.pins_placeholder)) },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    "CustomCA" -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config.caPem,
                            onValueChange = {
                                onConfigChange(config.copy(caPem = it))
                            },
                            label = { Text(stringResource(R.string.custom_ca_pem)) },
                            singleLine = false,
                            minLines = 4,
                            maxLines = 12,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRepoDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val canAdd = url.isNotBlank()

    FlickyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_repository),
        confirmButton = {
            TextButton(
                onClick = {
                    if (canAdd) onAdd(name.ifBlank { url }, url)
                },
                enabled = canAdd,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.primary,
                    disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(R.string.optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.url)) },
                placeholder = { Text("https://example.com/fdroid/repo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private suspend fun testRepoMirrors(base: String): List<ProbeResult> = withContext(Dispatchers.IO) {
    val policy = AppGraph.mirrorPolicyProvider.policyFor(base)
    val candidates = MirrorRegistry.candidates(
        base = base,
        includeOnion = policy.includeOnion,
        strategy = MirrorRegistry.Strategy.RoundRobin
    ).ifEmpty { listOf(base) }

    val client = try {
        AppGraph.httpClients.clientFor(base).newBuilder()
            .callTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    } catch (_: Exception) {
        OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun probe(urlBase: String): ProbeResult {
        // Try HEAD on index-v2.json first
        val url = "$urlBase/index-v2.json"
        var ok = false
        var code = -1
        val elapsed = measureTimeMillis {
            runCatching {
                runBlocking {
                    withTimeout(5000) {
                        val req = Request.Builder()
                            .url(url)
                            .head()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            code = resp.code
                            ok = resp.isSuccessful ||
                                    resp.code in 200..399 ||
                                    resp.code == 405 ||
                                    resp.code == 501
                        }
                    }
                }
            }
        }

        // If HEAD failed, try GET with Range header on v1
        if (!ok && code != 200) {
            val v1Url = "$urlBase/index-v1.jar"
            val v1Elapsed = measureTimeMillis {
                runCatching {
                    runBlocking {
                        withTimeout(5000) {
                            val req = Request.Builder()
                                .url(v1Url)
                                .get()
                                .header("Range", "bytes=0-0")
                                .build()
                            client.newCall(req).execute().use { resp ->
                                code = resp.code
                                ok = resp.isSuccessful ||
                                        resp.code in 200..399 ||
                                        resp.code == 206
                            }
                        }
                    }
                }
            }
            return ProbeResult(urlBase, ok, code, v1Elapsed)
        }

        return ProbeResult(urlBase, ok, code, elapsed)
    }

    candidates.map { cand -> probe(cand) }
}