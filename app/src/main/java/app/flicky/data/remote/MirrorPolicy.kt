package app.flicky.data.remote

data class MirrorPolicy(
    val enabled: Boolean = true,
    val rotateMirrors: Boolean = false,
    val includeOnion: Boolean = false,
    val strategy: MirrorRegistry.Strategy = MirrorRegistry.Strategy.StickyLastGood
)