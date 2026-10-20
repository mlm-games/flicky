@file:OptIn(ExperimentalMaterial3Api::class)

package app.flicky.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flicky.R
import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.MirrorPolicyProvider
import app.flicky.data.remote.MirrorRegistry
import app.flicky.ui.components.global.ConfirmationDialog
import app.flicky.ui.components.global.FlickyDialog
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.helper.tvFocusRing
import app.flicky.viewmodel.SettingsViewModel
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun RepositorySettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    pendingRepoUrl: String? = null,
    onPendingRepoUrlConsumed: () -> Unit = {}
) {
    val repos by vm.repositories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repoConfigDao: RepoConfigDao = koinInject()
    val mirrorPolicyProvider: MirrorPolicyProvider = koinInject()
    val stringProvider: StringResourceProvider = koinInject()
    val snackbarManager: SnackbarManager = koinInject()

    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<RepositoryInfo?>(null) }
    var showAddRepo by remember { mutableStateOf(false) }
    var addRepoPrefillUrl by remember { mutableStateOf("") }

    LaunchedEffect(pendingRepoUrl) {
        if (!pendingRepoUrl.isNullOrBlank()) {
            addRepoPrefillUrl = pendingRepoUrl
            showAddRepo = true
            onPendingRepoUrlConsumed()
        }
    }

    val repoConfigs by produceState(
        initialValue = emptyMap(),
        key1 = repos
    ) {
        value = withContext(Dispatchers.IO) {
            repos.associate { repo ->
                val base = repo.url.trimEnd('/')
                base to (repoConfigDao.get(base) ?: RepoConfig(baseUrl = base, enabled = repo.enabled))
            }
        }
    }

    MyScreenScaffold(
        title = stringResource(R.string.repositories),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(repos, key = { it.url }) { repo ->
                val base = repo.url.trimEnd('/')
                val config = repoConfigs[base] ?: RepoConfig(baseUrl = base, enabled = repo.enabled)

                RepoConfigCard(
                    repo = repo,
                    config = config,
                    stringProvider = stringProvider,
                    onConfigChange = { newCfg ->
                        scope.launch {
                            vm.upsertRepoConfig(newCfg)
                        }
                    },
                    onToggle = { vm.toggleRepository(repo.url) },
                    onTestMirrors = {
                        scope.launch {
                            val results = vm.testRepoMirrors(base)
                            val message = buildString {
                                results.forEach { (url, ok, code, ms) ->
                                    append(if (ok) "✓" else "✗")
                                    append(" ").append(url).append("\n")
                                    append(" ")
                                    append(if (ok) "${ms}ms (HTTP $code)" else "HTTP $code / fail")
                                    append("\n")
                                }
                            }
                            snackbarManager.show(message)
                        }
                    },
                    onForgetMirror = {
                        MirrorRegistry.clearLastGood(base)
                        snackbarManager.show(context.getString(R.string.forgot_mirror, repo.name))
                    },
                    onDelete = {
                        repoToDelete = repo
                        showDeleteConfirm = true
                    }
                )
            }

            item(key = "repo_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            addRepoPrefillUrl = ""
                            showAddRepo = true
                        },
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

    if (showAddRepo) {
        AddRepoDialog(
            initialUrl = addRepoPrefillUrl,
            onDismiss = { showAddRepo = false },
            onAdd = { name, url, trustMode ->
                scope.launch {
                    vm.addRepository(name, url, trustMode)
                    mirrorPolicyProvider.ensureDefault(url.trimEnd('/'))
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

    if (showDeleteConfirm && repoToDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_repository),
            message = stringResource(R.string.delete_repository_confirm, repoToDelete!!.name),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.action_cancel),
            isDangerous = true,
            onConfirm = {
                repoToDelete?.let { vm.deleteRepository(it.url) }
                showDeleteConfirm = false
                repoToDelete = null
            },
            onDismiss = {
                showDeleteConfirm = false
                repoToDelete = null
            }
        )
    }
}

@Composable
private fun RepoConfigCard(
    repo: RepositoryInfo,
    config: RepoConfig,
    stringProvider: StringResourceProvider,
    onConfigChange: (RepoConfig) -> Unit,
    onToggle: () -> Unit,
    onTestMirrors: () -> Unit,
    onForgetMirror: () -> Unit,
    onDelete: () -> Unit
) {
    var localConfig by remember(config) { mutableStateOf(config) }
    var showMenu by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var openStrategy by remember { mutableStateOf(false) }
    var openTrust by remember { mutableStateOf(false) }

    val strategyFocusRequester = remember { FocusRequester() }
    val trustFocusRequester = remember { FocusRequester() }

    val updateConfig: (RepoConfig) -> Unit = { newConfig ->
        localConfig = newConfig
        onConfigChange(newConfig)
    }

    Surface(
        color = colorScheme.surfaceContainerLow,
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
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .tvFocusRing(shape = CircleShape)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options_for, repo.name),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            containerColor = colorScheme.background,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.advanced)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showAdvanced = true
                                }
                            )

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

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.delete_repository),
                                        color = colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Switch(
                        checked = repo.enabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.tvFocusRing(
                            shape = RoundedCornerShape(16.dp)
                        )
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
                        label = { Text(stringResource(R.string.rotate_mirrors)) },
                        modifier = Modifier.tvFocusRing()
                    )

                    FilterChip(
                        selected = localConfig.includeOnion,
                        onClick = {
                            updateConfig(localConfig.copy(includeOnion = !localConfig.includeOnion))
                        },
                        label = { Text(stringResource(R.string.use_onion)) },
                        modifier = Modifier.tvFocusRing()
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Mirror strategy dropdown
                val strategies = listOf("StickyLastGood", "RoundRobin", "CanonicalFirst")
                val strategyIdx = strategies.indexOf(localConfig.strategy).coerceAtLeast(0)
                val strategyLabels = stringProvider.getStringArray(R.array.setting_mirror_strategy_options)

                ExposedDropdownMenuBox(
                    expanded = openStrategy,
                    onExpandedChange = { openStrategy = it },
                ) {
                    OutlinedTextField(
                        value = strategyLabels.getOrNull(strategyIdx)
                            ?: strategies[strategyIdx],
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.mirror_strategy)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openStrategy) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .focusRequester(strategyFocusRequester)
                    )

                    ExposedDropdownMenu(
                        expanded = openStrategy,
                        containerColor = colorScheme.background,
                        onDismissRequest = {
                            openStrategy = false
                            strategyFocusRequester.requestFocus()
                        },
                    ) {
                        strategies.forEachIndexed { index, s ->
                            DropdownMenuItem(
                                text = { Text(strategyLabels.getOrNull(index) ?: s) },
                                onClick = {
                                    openStrategy = false
                                    updateConfig(localConfig.copy(strategy = s))
                                    strategyFocusRequester.requestFocus()
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

                val trustModes = listOf("HttpsOnly", "Pinned", "CustomCA", "InsecureHttp")
                val trustIdx = trustModes.indexOf(localConfig.trustMode).coerceAtLeast(0)
                val trustLabels = stringProvider.getStringArray(R.array.setting_trust_mode_options)

                ExposedDropdownMenuBox(
                    expanded = openTrust,
                    onExpandedChange = { openTrust = !openTrust },
                ) {
                    OutlinedTextField(
                        value = trustLabels.getOrNull(trustIdx) ?: trustModes[trustIdx],
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
                            .focusRequester(trustFocusRequester)
                    )

                    ExposedDropdownMenu(
                        expanded = openTrust,
                        containerColor = colorScheme.background,
                        onDismissRequest = {
                            openTrust = false
                            trustFocusRequester.requestFocus()
                        }
                    ) {
                        trustModes.forEachIndexed { index, s ->
                            DropdownMenuItem(
                                text = { Text(trustLabels.getOrNull(index) ?: s) },
                                onClick = {
                                    openTrust = false
                                    updateConfig(config.copy(trustMode = s))
                                    trustFocusRequester.requestFocus()
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

    if (showAdvanced) {
        AdvancedRepoDialog(
            config = localConfig,
            repoUrl = repo.url,
            fingerprint = repo.signingKey,
            onSave = { newConfig ->
                updateConfig(newConfig)
                showAdvanced = false
            },
            onDismiss = { showAdvanced = false }
        )
    }
}

@Composable
private fun AdvancedRepoDialog(
    config: RepoConfig,
    repoUrl: String,
    fingerprint: String,
    onSave: (RepoConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var user by remember { mutableStateOf(config.basicAuthUser) }
    var password by remember { mutableStateOf(config.basicAuthPassword) }
    var downloadBase by remember { mutableStateOf(config.downloadBase) }
    var mirrors by remember { mutableStateOf(config.customMirrors) }
    var newMirror by remember { mutableStateOf("") }

    val hasFingerprint = fingerprint.isNotBlank()

    FlickyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.advanced),
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        config.copy(
                            basicAuthUser = user.trim(),
                            basicAuthPassword = password,
                            downloadBase = downloadBase.trim().trimEnd('/'),
                            customMirrors = mirrors
                        )
                    )
                },
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
            ) {
                Text(stringResource(R.string.action_apply))
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
            Text(stringResource(R.string.basic_auth), style = typography.labelLarge)

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.custom_mirrors), style = typography.labelLarge)

            mirrors.forEach { mirror ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        mirror,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { mirrors = mirrors - mirror },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.remove),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newMirror,
                    onValueChange = { newMirror = it },
                    label = { Text(stringResource(R.string.url)) },
                    placeholder = { Text("https://example.com/fdroid/repo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val candidate = newMirror.trim().trimEnd('/')
                        if (candidate.isNotBlank() && !mirrors.contains(candidate)) {
                            mirrors = mirrors + candidate
                        }
                        newMirror = ""
                    },
                    enabled = newMirror.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.signer_fingerprint), style = typography.labelLarge)

            Text(
                if (hasFingerprint) fingerprint else stringResource(R.string.not_verified),
                style = typography.bodySmall,
                color = if (hasFingerprint) colorScheme.onSurfaceVariant else colorScheme.error
            )

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.download_base), style = typography.labelLarge)

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = downloadBase,
                onValueChange = { downloadBase = it },
                placeholder = { Text(repoUrl) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AddRepoDialog(
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var allowInsecureHttp by remember(initialUrl) {
        mutableStateOf(initialUrl.startsWith("http://", ignoreCase = true))
    }

    val normalizedUrl = url.trim()
    val isHttpRepo = normalizedUrl.startsWith("http://", ignoreCase = true)
    val canAdd = normalizedUrl.isNotBlank() && (!isHttpRepo || allowInsecureHttp)

    FlickyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_repository),
        confirmButton = {
            TextButton(
                onClick = {
                    if (canAdd) {
                        val trustMode = if (isHttpRepo && allowInsecureHttp) "InsecureHttp" else "HttpsOnly"
                        onAdd(name.ifBlank { url }, normalizedUrl, trustMode)
                    }
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

            if (isHttpRepo) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.insecure_http_warning),
                    style = typography.bodySmall,
                    color = colorScheme.error
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = allowInsecureHttp,
                        onCheckedChange = { allowInsecureHttp = it },
                        modifier = Modifier.tvFocusRing(
                            shape = RoundedCornerShape(16.dp)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.allow_insecure_http),
                        style = typography.bodyMedium
                    )
                }
            }
        }
    }
}
