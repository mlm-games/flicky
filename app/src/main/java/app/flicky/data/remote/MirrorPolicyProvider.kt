package app.flicky.data.remote

import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MirrorPolicyProvider {
    suspend fun policyFor(baseUrl: String): MirrorPolicy
    suspend fun ensureDefault(baseUrl: String)
}

class DbMirrorPolicyProvider(
    private val dao: RepoConfigDao
) : MirrorPolicyProvider {

    override suspend fun policyFor(baseUrl: String): MirrorPolicy = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        val cfg = dao.get(base) ?: RepoConfig(baseUrl = base)
        MirrorRegistry.setCustomMirrors(base, cfg.customMirrors)
        MirrorPolicy(
            enabled = cfg.enabled,
            rotateMirrors = cfg.rotateMirrors,
            includeOnion = cfg.includeOnion,
            strategy = runCatching { MirrorRegistry.Strategy.valueOf(cfg.strategy) }
                .getOrDefault(MirrorRegistry.Strategy.StickyLastGood)
        )
    }

    override suspend fun ensureDefault(baseUrl: String) = withContext(Dispatchers.IO) {
        dao.insertIgnore(RepoConfig(baseUrl = baseUrl.trim().trimEnd('/')))
    }
}