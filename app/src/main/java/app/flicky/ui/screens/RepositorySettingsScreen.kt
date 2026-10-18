package app.flicky.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flicky.R
import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.MirrorPolicyProvider
import app.flicky.data.remote.MirrorRegistry
import app.flicky.ui.components.global.ConfirmationDialog
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun RepositorySettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    val repos by vm.repositories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val repoConfigDao: RepoConfigDao = koinInject()
    val mirrorPolicyProvider: MirrorPolicyProvider = koinInject()
    val snackbarManager: SnackbarManager = koinInject()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showAddRepo by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<RepositoryInfo?>(null) }

    val repoConfigs by produceState(initialValue = emptyMap(), key1 = repos) {
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
                RepoConfigCard(
                    repo = repo,
                    config = repoConfigs[base] ?: RepoConfig(baseUrl = base, enabled = repo.enabled),
                    stringProvider = koinInject(),
                    onConfigChange = { vm.upsertRepoConfig(it) },
                    onToggle = { vm.toggleRepository(repo.url) },
                    onTestMirrors = {
                        scope.launch {
                            val message = vm.testRepoMirrors(base).joinToString("\n") { (url, ok, code, ms) ->
                                "${if (ok) "✓" else "✗"} $url\n ${if (ok) "${ms}ms (HTTP $code)" else "HTTP $code / fail"}"
                            }
                            snackbarManager.show(message)
                        }
                    },
                    onForgetMirror = {
                        MirrorRegistry.clear(base)
                        snackbarManager.show(context.getString(R.string.forgot_mirror, repo.name))
                    },
                    onDelete = { repoToDelete = repo }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { showAddRepo = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_repository))
                    }
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error)
                    ) {
                        Icon(Icons.Default.RestartAlt, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.reset_to_defaults))
                    }
                }
            }
        }
    }

    if (showAddRepo) AddRepoDialog(
        onDismiss = { showAddRepo = false },
        onAdd = { name, url, trustMode ->
            vm.addRepository(name, url, trustMode)
            scope.launch { mirrorPolicyProvider.ensureDefault(url.trimEnd('/')) }
            showAddRepo = false
        }
    )
    if (showResetConfirm) ConfirmationDialog(
        title = stringResource(R.string.reset_repositories),
        message = stringResource(R.string.reset_repositories_confirm),
        confirmText = stringResource(R.string.reset),
        dismissText = stringResource(R.string.action_cancel),
        isDangerous = true,
        onConfirm = { showResetConfirm = false; vm.resetRepositoriesToDefaults() },
        onDismiss = { showResetConfirm = false }
    )
    repoToDelete?.let { repo ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_repository),
            message = stringResource(R.string.delete_repository_confirm, repo.name),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.action_cancel),
            isDangerous = true,
            onConfirm = { vm.deleteRepository(repo.url); repoToDelete = null },
            onDismiss = { repoToDelete = null }
        )
    }
}
