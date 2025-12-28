package app.flicky.ui.screens

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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
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
import app.flicky.data.repository.AppSettingsSchema
import app.flicky.ui.components.global.ConfirmationDialog
import app.flicky.ui.components.global.DropdownSettingDialog
import app.flicky.ui.components.global.FlickyDialog
import app.flicky.ui.components.global.InputDialog
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.components.global.SettingsAction
import app.flicky.ui.components.global.SettingsItem
import app.flicky.ui.components.global.SettingsSection
import app.flicky.ui.components.global.SettingsToggle
import app.flicky.ui.components.global.SliderSettingDialog
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.viewmodel.SettingsViewModel
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.annotations.CategoryDefinition
import io.github.mlmgames.settings.core.types.Button
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.Slider
import io.github.mlmgames.settings.core.types.TextInput
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.system.measureTimeMillis

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    val repos by vm.repositories.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val schema = remember { AppSettingsSchema }

    val snackbarManager : SnackbarManager = koinInject()

    var showDropdown by remember { mutableStateOf(false) }
    var showSlider by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }

    var currentField by remember { mutableStateOf<SettingField<AppSettings, *>?>(null) }

    var showResetConfirm by remember { mutableStateOf(false) }
    var showAddRepo by remember { mutableStateOf(false) }

    val cfg = LocalConfiguration.current
    val isTablet = cfg.screenWidthDp >= 600
    val gridCells = if (isTablet) GridCells.Adaptive(minSize = 400.dp) else GridCells.Fixed(1)

    val repoConfigs by produceState(
        initialValue = emptyMap(),
        key1 = repos
    ) {
        value = withContext(Dispatchers.IO) {
            val dao = AppGraph.db.repoConfigDao()
            repos.associate { repo ->
                val base = repo.url.trimEnd('/')
                base to (dao.get(base) ?: RepoConfig(baseUrl = base, enabled = repo.enabled))
            }
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SettingsViewModel.UiEvent.Toast -> {
                    snackbarManager.show(context.getString(event.messageResId),)
                }
                is SettingsViewModel.UiEvent.RequestExport -> {
                    // TODO: implement export
                }
            }
        }
    }

    fun categoryTitle(cat: KClass<*>): String {
        val ann = cat.findAnnotation<CategoryDefinition>()
        val res = ann?.titleRes ?: 0
        return if (res != 0) context.getString(res) else (cat.simpleName ?: "Settings")
    }

    MyScreenScaffold(
        title = stringResource(R.string.nav_settings),
        actions = {}
    ) {
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // Settings cards by category (schema-driven)
            val categories = schema.orderedCategories()
            val grouped = schema.groupedByCategory()

            items(
                items = categories,
                key = { it.qualifiedName ?: it.simpleName ?: "cat" }
            ) { cat ->
                val fields = grouped[cat].orEmpty()
                if (fields.isEmpty()) return@items

                SettingsSection(title = categoryTitle(cat)) {
                    fields.forEach { field ->
                        val meta = field.meta ?: return@forEach
                        val enabled = schema.isEnabled(settings, field)

                        when (meta.type) {
                            Toggle::class -> {
                                val v = (field.get(settings) as? Boolean) ?: false
                                SettingsToggle(
                                    title = meta.title,
                                    description = meta.description.takeIf { it.isNotBlank() },
                                    isChecked = v,
                                    enabled = enabled,
                                    onCheckedChange = { vm.updateSetting(field.name, it) }
                                )
                            }

                            Dropdown::class -> {
                                val idx = (field.get(settings) as? Int) ?: 0
                                val options = meta.options
                                SettingsItem(
                                    title = meta.title,
                                    subtitle = options.getOrNull(idx) ?: stringResource(R.string.unknown),
                                    description = meta.description.takeIf { it.isNotBlank() },
                                    enabled = enabled,
                                    onClick = {
                                        currentField = field
                                        showDropdown = true
                                    }
                                )
                            }

                            Slider::class -> {
                                val subtitle = when (val v = field.get(settings)) {
                                    is Int -> v.toString()
                                    is Float -> String.format(Locale.getDefault(), "%.1f", v)
                                    is Double -> String.format(Locale.getDefault(), "%.1f", v)
                                    is Long -> v.toString()
                                    else -> ""
                                }
                                SettingsItem(
                                    title = meta.title,
                                    subtitle = subtitle,
                                    description = meta.description.takeIf { it.isNotBlank() },
                                    enabled = enabled,
                                    onClick = {
                                        currentField = field
                                        showSlider = true
                                    }
                                )
                            }

                            TextInput::class -> {
                                val cur = (field.get(settings) as? String).orEmpty()
                                SettingsItem(
                                    title = meta.title,
                                    subtitle = cur.ifBlank { stringResource(R.string.optional) },
                                    description = meta.description.takeIf { it.isNotBlank() },
                                    enabled = enabled,
                                    onClick = {
                                        currentField = field
                                        showTextInput = true
                                    }
                                )
                            }

                            Button::class -> {
                                SettingsAction(
                                    title = meta.title,
                                    description = meta.description.takeIf { it.isNotBlank() },
                                    buttonText = stringResource(R.string.run),
                                    enabled = enabled,
                                    onClick = {
                                        vm.performAction(field.name)

                                        // Only do this if the field type is Long.
                                        val current = field.get(settings)
                                        if (current is Long) {
                                            vm.updateSetting(field.name, System.currentTimeMillis())
                                        }
                                    }
                                )
                            }

                            else -> {
                                // unsupported type
                                SettingsItem(
                                    title = meta.title,
                                    subtitle = stringResource(R.string.unknown),
                                    description = meta.description.takeIf { it.isNotBlank() },
                                    enabled = false,
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }

            // Repositories section header (unchanged)
            item(key = "repos_header") {
                Text(
                    text = stringResource(R.string.repositories),
                    style = typography.titleMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Repository cards (unchanged)
            items(repos, key = { it.url }) { repo ->
                val base = repo.url.trimEnd('/')
                val config = repoConfigs[base] ?: RepoConfig(baseUrl = base, enabled = repo.enabled)

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
                            snackbarManager.show(message)
                        }
                    },
                    onForgetMirror = {
                        MirrorRegistry.clear(base)
                        snackbarManager.show(context.getString(R.string.forgot_mirror, repo.name))
                    }
                )
            }

            item(key = "repo_actions") {
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.reset_to_defaults))
                    }
                }
            }
        }
    }

    if (showDropdown) {
        val field = currentField
        val meta = field?.meta
        if (field != null && meta != null) {
            val idx = (field.get(settings) as? Int) ?: 0
            DropdownSettingDialog(
                title = meta.title,
                options = meta.options,
                selectedIndex = idx,
                onDismiss = { showDropdown = false },
                onOptionSelected = { i ->
                    vm.updateSetting(field.name, i)
                    showDropdown = false
                }
            )
        }
    }

    if (showSlider) {
        val field = currentField
        val meta = field?.meta
        if (field != null && meta != null) {
            val cur = when (val v = field.get(settings)) {
                is Int -> v.toFloat()
                is Float -> v
                is Double -> v.toFloat()
                is Long -> v.toFloat()
                else -> 0f
            }

            SliderSettingDialog(
                title = meta.title,
                currentValue = cur,
                min = meta.min,
                max = meta.max,
                step = meta.step,
                onDismiss = { showSlider = false },
                onValueSelected = { value ->
                    when (field.get(settings)) {
                        is Int -> vm.updateSetting(field.name, value.toInt())
                        is Float -> vm.updateSetting(field.name, value)
                        is Double -> vm.updateSetting(field.name, value.toDouble())
                        is Long -> vm.updateSetting(field.name, value.toLong())
                    }
                    showSlider = false
                }
            )
        }
    }

    if (showTextInput) {
        val field = currentField
        val meta = field?.meta
        if (field != null && meta != null) {
            val current = (field.get(settings) as? String).orEmpty()

            InputDialog(
                title = meta.title,
                label = meta.title,
                value = current,
                placeholder = "",
                confirmText = stringResource(R.string.action_confirm),
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = { newValue ->
                    vm.updateSetting(field.name, newValue)
                    showTextInput = false
                },
                onDismiss = { showTextInput = false },
                validator = { true }
            )
        }
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
                    onExpandedChange = { openStrategy = it },
                ) {
                    OutlinedTextField(
                        value = strategies[strategyIdx],
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.mirror_strategy)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openStrategy) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = openStrategy,
                        onDismissRequest = { openStrategy = false },
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
                        singleLine = true,
                        label = { Text(stringResource(R.string.trust_mode)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = openTrust)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
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

private data class ProbeResult(
    val url: String,
    val ok: Boolean,
    val code: Int,
    val ms: Long
)