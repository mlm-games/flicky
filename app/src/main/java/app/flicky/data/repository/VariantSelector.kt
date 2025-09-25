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

    fun pick(
        variants: List<AppVariant>,
        preferred: PreferredRepo,
        preferredRepoUrl: String? = null,
        strict: Boolean = false
    ): AppVariant? {
        if (variants.isEmpty()) return null
        val pin = norm(preferredRepoUrl)

        val preferMatcher: (AppVariant) -> Boolean = when (preferred) {
            PreferredRepo.FDroid      -> { v -> v.repositoryName.equals("F-Droid", true) || v.repositoryUrl.contains("f-droid", true) }
            PreferredRepo.IzzyOnDroid -> { v -> v.repositoryName.contains("izzy", true) || v.repositoryUrl.contains("izzy", true) }
            else -> { _ -> false }
        }

        val candidates = if (pin.isNotEmpty()) {
            val pinned = variants.filter { norm(it.repositoryUrl) == pin }
            if (strict) pinned else (pinned + variants.filter { norm(it.repositoryUrl) != pin })
        } else variants

        if (strict && candidates.isEmpty()) return null

        val sorted = candidates.sortedWith(
            compareByDescending<AppVariant> { v -> if (pin.isNotEmpty() && norm(v.repositoryUrl) == pin) 1 else 0 }
                .thenByDescending { v -> if (preferred != PreferredRepo.Auto && preferMatcher(v)) 1 else 0 }
                .thenByDescending { it.isCompatible }
                .thenByDescending { it.versionCode }
        )
        return sorted.firstOrNull()
    }
}