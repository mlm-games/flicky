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
import java.util.concurrent.atomic.AtomicReference

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
    val batchUpdateJob = remember { AtomicReference<Job?>(null) }

    val batchProgress by remember(installerTasks, ui.updates, isBatchUpdating) {
        derivedStateOf {
            if (!isBatchUpdating) 0f
            else {
                val total = ui.updates.size
                if (total == 0) return@derivedStateOf 0f

                val completed = ui.updates.count { app ->
                    val stage = installerTasks[app.packageName]
                    stage is TaskStage.Finished
                }

                val inProgress = ui.updates.sumOf { app ->
                    when (val stage = installerTasks[app.packageName]) {
                        is TaskStage.Downloading -> stage.progress * 0.33
                        is TaskStage.Verifying -> 0.33 + 0.33
                        is TaskStage.Installing -> 0.66 + stage.progress * 0.34
                        is TaskStage.Finished -> 1
                        else -> 0
                    }.toDouble()
                }.toFloat()

                (inProgress / total).coerceIn(0f, 1f)
            }
        }
    }

    val actions = remember(vm, installer) {
        object : UpdatesActions {
            override fun updateAll() {
                if (isBatchUpdating) return
                isBatchUpdating = true

                val job = scope.launch {
                    val updates = ui.updates.toList()
                    if (updates.isEmpty()) {
                        isBatchUpdating = false
                        return@launch
                    }

                    val queue = Channel<FDroidApp>(updates.size)
                    updates.forEach { queue.send(it) }
                    queue.close()

                    try {
                        // Process 3 concurrent installations
                        coroutineScope {
                            repeat(minOf(3, updates.size)) { workerId ->
                                launch {
                                    for (app in queue) {
                                        if (!isActive) break

                                        try {
                                            Log.d("UpdatesRoute", "Worker $workerId: Installing ${app.packageName}")
                                            installer.install(app)

                                            // Wait for completion before next
                                            withTimeoutOrNull(300_000) { // 5 minute timeout per app
                                                installer.tasks.first { tasks ->
                                                    val stage = tasks[app.packageName]
                                                    stage is TaskStage.Finished ||
                                                            stage is TaskStage.Cancelled ||
                                                            stage == null
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
                        isBatchUpdating = false
                        batchUpdateJob.set(null)
                    }
                }
                batchUpdateJob.set(job)
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

            override fun ignoreThisVersion(app: FDroidApp) {
                vm.ignoreThisVersion(app.packageName, app.versionCode.toLong())
            }

            override fun ignoreAll(app: FDroidApp) {
                vm.ignoreAllUpdates(app.packageName)
            }

            override fun stopIgnoring(app: FDroidApp) {
                vm.stopIgnoring(app.packageName)
            }

            override fun cancelBatch() {
                batchUpdateJob.get()?.cancel()
                isBatchUpdating = false

                // Cancel all ongoing installations
                ui.updates.forEach { app ->
                    val stage = installerTasks[app.packageName]
                    if (stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled) {
                        installer.cancel(app.packageName)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            batchUpdateJob.get()?.cancel()
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