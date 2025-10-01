package app.flicky.ui.routes

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flicky.AppGraph
import app.flicky.data.model.FDroidApp
import app.flicky.helper.viewModelFactory
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import app.flicky.ui.screens.UpdatesScreen
import app.flicky.viewmodel.UpdatesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first

interface UpdatesActions {
    fun updateAll()
    fun updateOne(app: FDroidApp)
    fun openDetails(app: FDroidApp)
    fun ignoreThisVersion(app: FDroidApp)
    fun ignoreAll(app: FDroidApp)
    fun stopIgnoring(app: FDroidApp)
    fun cancelBatch()
}

@Composable
fun UpdatesRoute(
    vm: UpdatesViewModel = viewModel(factory = viewModelFactory {
        UpdatesViewModel(
            AppGraph.appRepo,
            AppGraph.installedRepo,
            AppGraph.installer
        )
    }),
    installer: Installer = AppGraph.installer,
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

            // Calculate progress based on the STABLE appsInBatch list
            val inProgressSum = appsInBatch.value.sumOf { app ->
                val stage = installerTasks[app.packageName]
                when {
                    stage is TaskStage.Downloading -> stage.progress * 0.33
                    stage is TaskStage.Verifying -> 0.33 + 0.33
                    stage is TaskStage.Installing -> 0.66 + stage.progress * 0.34
                    stage is TaskStage.Finished && stage.success -> 1.0 // Only count successful as 100%
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
                    val queue = Channel<FDroidApp>(updatesToRun.size)
                    updatesToRun.forEach { queue.send(it) }
                    queue.close()

                    try {
                        // Process 3 concurrent installations
                        coroutineScope {
                            repeat(minOf(3, updatesToRun.size)) { workerId ->
                                launch {
                                    for (app in queue) {
                                        if (!isActive) break
                                        try {
                                            Log.d("UpdatesRoute", "Worker $workerId: Installing ${app.packageName}")
                                            installer.install(app)
                                            // Wait for completion before starting the next one in this worker
                                            withTimeoutOrNull(300_000L) {
                                                installer.tasks.first { tasks ->
                                                    val stage = tasks[app.packageName]
                                                    stage is TaskStage.Finished || stage is TaskStage.Cancelled
                                                }
                                            }
                                        } catch (e: CancellationException) {
                                            throw e // Re-throw to propagate cancellation
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
                        installer.install(app)
                    } catch (e: Exception) {
                        Log.e("UpdatesRoute", "Failed to update ${app.packageName}", e)
                    }
                }
            }

            override fun openDetails(app: FDroidApp) = onOpenDetails(app.packageName)

            override fun ignoreThisVersion(app: FDroidApp) = vm.ignoreThisVersion(app)

            override fun ignoreAll(app: FDroidApp) = vm.ignoreAllUpdates(app)

            override fun stopIgnoring(app: FDroidApp) = vm.stopIgnoring(app)

            override fun cancelBatch() {
                batchUpdateJob?.cancel()
                batchUpdateJob = null
                isBatchUpdating = false

                // Cancel all ongoing installations from this batch
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