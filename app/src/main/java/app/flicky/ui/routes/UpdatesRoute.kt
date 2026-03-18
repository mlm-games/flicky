package app.flicky.ui.routes

import android.util.Log
import androidx.compose.runtime.*
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.SettingsRepository
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import app.flicky.ui.screens.UpdatesScreen
import app.flicky.viewmodel.UpdatesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

interface UpdatesActions {
    fun updateAll()
    fun updateOne(app: FDroidApp)
    fun openDetails(app: FDroidApp)
    fun ignoreThisVersion(packageName: String, versionCode: Long)
    fun ignoreAll(app: FDroidApp)
    fun stopIgnoring(app: FDroidApp)
    fun cancelBatch()
}

@Composable
fun UpdatesRoute(
    vm: UpdatesViewModel = koinViewModel(),
    installer: Installer = koinInject(),
    settings: SettingsRepository = koinInject(),
    onOpenDetails: (String) -> Unit
) {
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val installerTasks by installer.tasks.collectAsState(initial = emptyMap())

    var isBatchUpdating by remember { mutableStateOf(false) }
    val appsInBatch = remember { mutableStateOf<List<FDroidApp>>(emptyList()) }
    var batchUpdateJob by remember { mutableStateOf<Job?>(null) }


    val batchProgress by remember(installerTasks, appsInBatch.value, isBatchUpdating) {
        derivedStateOf {
            if (!isBatchUpdating || appsInBatch.value.isEmpty()) {
                return@derivedStateOf 0f
            }
            val total = appsInBatch.value.size.toFloat()
            if (total == 0f) return@derivedStateOf 0f

            val inProgressSum = appsInBatch.value.sumOf { app ->
                when (val stage = installerTasks[app.packageName]) {
                    is TaskStage.Downloading -> stage.progress * 0.33
                    is TaskStage.Verifying -> 0.33 + 0.33
                    is TaskStage.Installing -> 0.66 + stage.progress * 0.34
                    is TaskStage.Finished if stage.success -> 1.0
                    else -> 0.0
                }
            }.toFloat()
            (inProgressSum / total).coerceIn(0f, 1f)
        }
    }

    val actions = remember(vm, installer, isBatchUpdating) {
        object : UpdatesActions {
            override fun updateAll() {
                if (isBatchUpdating) return

                val updatesToRun = ui.updates.toList()
                if (updatesToRun.isEmpty()) return

                appsInBatch.value = updatesToRun
                isBatchUpdating = true

                batchUpdateJob = scope.launch {
                    val installerMode = runCatching { settings.settingsFlow.first().installerMode }.getOrDefault(0)
                    val parallelism = when (installerMode) {
                        2, 3 -> 3 // Test
                        else -> 10 // Certain roms might have problems with parallel root installs
                    }
                    Log.d("UpdatesRoute", "Starting batch update with parallelism: $parallelism")

                    val queue = Channel<FDroidApp>(updatesToRun.size)
                    updatesToRun.forEach { queue.trySend(it) }
                    queue.close()

                    try {
                        coroutineScope {
                            repeat(minOf(parallelism, updatesToRun.size)) { workerId ->
                                launch {
                                    for (app in queue) {
                                        if (!isActive) break
                                        try {
                                            Log.d("UpdatesRoute", "Worker $workerId: Installing ${app.packageName}")
                                            val variant = ui.updateCandidates[app.packageName]
                                            if (variant != null) {
                                                installer.install(variant)
                                            } else {
                                                installer.install(app)
                                            }
                                            // Wait for completion before starting the next one in this worker
                                            withTimeoutOrNull(300_000L) {
                                                installer.tasks.first { tasks ->
                                                    val stage = tasks[app.packageName]
                                                    stage is TaskStage.Finished || stage is TaskStage.Cancelled || stage == null
                                                }
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Log.e("UpdatesRoute", "Failed to update ${app.packageName}", e)
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            isBatchUpdating = false
                            appsInBatch.value = emptyList()
                            batchUpdateJob = null
                        }
                    }
                }
            }

            override fun updateOne(app: FDroidApp) {
                scope.launch {
                    try {
                        val variant = ui.updateCandidates[app.packageName]
                        if (variant != null) {
                            installer.install(variant)
                        } else {
                            installer.install(app)
                        }
                    } catch (e: Exception) {
                        Log.e("UpdatesRoute", "Failed to update ${app.packageName}", e)
                    }
                }
            }

            override fun openDetails(app: FDroidApp) = onOpenDetails(app.packageName)

            override fun ignoreThisVersion(packageName: String, versionCode: Long) =
                vm.ignoreThisVersion(packageName, versionCode)

            override fun ignoreAll(app: FDroidApp) = vm.ignoreAllUpdates(app)

            override fun stopIgnoring(app: FDroidApp) = vm.stopIgnoring(app)

            override fun cancelBatch() {
                batchUpdateJob?.cancel()
                batchUpdateJob = null
                isBatchUpdating = false

                appsInBatch.value.forEach { app ->
                    val stage = installerTasks[app.packageName]
                    if (stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled) {
                        installer.cancel(app.packageName)
                    }
                }
                appsInBatch.value = emptyList()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            batchUpdateJob?.cancel()
        }
    }

    UpdatesScreen(
        ui = ui,
        actions = actions,
        installerTasks = installerTasks,
        isBatchUpdating = isBatchUpdating,
        batchProgress = batchProgress
    )
}
