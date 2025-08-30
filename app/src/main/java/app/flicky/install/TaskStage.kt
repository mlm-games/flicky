package app.flicky.install

sealed class TaskStage {
    data object Idle : TaskStage()
    data class Downloading(val progress: Float) : TaskStage()
    data object Verifying : TaskStage()
    data class Installing(val progress: Float) : TaskStage()
    data class Finished(val success: Boolean) : TaskStage()
}