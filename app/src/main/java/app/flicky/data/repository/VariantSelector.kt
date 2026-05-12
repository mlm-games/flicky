package app.flicky.data.repository

import app.flicky.data.local.AppVariant

enum class PreferredRepo(val idx: Int) {
    Auto(0), FDroid(1), IzzyOnDroid(2);

    companion object {
        fun fromIndex(i: Int) = entries.firstOrNull { it.idx == i } ?: Auto
    }
}

/**
 * Picks based on preferred repo, then by compatibility, then by version code.
 */
object VariantSelector {
    private fun norm(u: String?) = u?.trim()?.trimEnd('/')?.lowercase().orEmpty()

    private fun isStable(v: AppVariant): Boolean = v.releaseChannels.isEmpty()

    fun pick(
        variants: List<AppVariant>,
        preferred: PreferredRepo,
        preferredRepoUrl: String? = null,
        strict: Boolean = false,
        ignoreUnstable: Boolean = false
    ): AppVariant? {
        if (variants.isEmpty()) return null
        val pin = norm(preferredRepoUrl)
        val pool = if (ignoreUnstable) variants.filter { isStable(it) } else variants
        val candidates = if (pin.isNotEmpty()) {
            val pinned = pool.filter { norm(it.repositoryUrl) == pin }
            if (strict) pinned else (pinned + pool.filter { norm(it.repositoryUrl) != pin })
        } else pool

        if (strict && candidates.isEmpty()) return null

        val sorted = candidates.sortedWith(
            compareByDescending<AppVariant> { v -> if (pin.isNotEmpty() && norm(v.repositoryUrl) == pin) 1 else 0 }
                .thenByDescending { v -> if (preferred != PreferredRepo.Auto && preferMatcher(v, preferred)) 1 else 0 }
                .thenByDescending { it.isCompatible }
                .thenByDescending { it.versionCode }
        )
        return sorted.firstOrNull()
    }

    fun pickCompatible(
        variants: List<AppVariant>,
        preferred: PreferredRepo,
        preferredRepoUrl: String? = null,
        strict: Boolean = false,
        ignoreUnstable: Boolean = false
    ): AppVariant? {
        val compatible = variants.filter { it.isCompatible }
        if (compatible.isEmpty()) return null
        return pick(
            variants = compatible,
            preferred = preferred,
            preferredRepoUrl = preferredRepoUrl,
            strict = strict,
            ignoreUnstable = ignoreUnstable
        )
    }

    fun matchesPreferred(variant: AppVariant, preferred: PreferredRepo): Boolean = preferMatcher(variant, preferred)

    private fun preferMatcher(v: AppVariant, preferred: PreferredRepo): Boolean = when (preferred) {
        PreferredRepo.FDroid ->
            v.repositoryName.equals("F-Droid", true) ||
                    v.repositoryUrl.contains("f-droid", true)
        PreferredRepo.IzzyOnDroid ->
            v.repositoryName.contains("izzy", true) ||
                    v.repositoryUrl.contains("izzy", true)
        PreferredRepo.Auto -> false
    }
}
