package app.flicky.data.repository

import app.flicky.data.local.AppVariant

enum class PreferredRepo(val idx: Int) {
    Auto(0), FDroid(1), IzzyOnDroid(2);

    companion object {
        fun fromIndex(i: Int) = entries.firstOrNull { it.idx == i } ?: Auto
    }
}

object VariantSelector {
    /**
     * Based on preferred repo, then by compatibility, then by version code.
     */
    fun pick(variants: List<AppVariant>, preferred: PreferredRepo): AppVariant? {
        if (variants.isEmpty()) return null

        val preferMatcher: (AppVariant) -> Boolean = when (preferred) {
            PreferredRepo.FDroid     -> { v -> v.repositoryName.equals("F-Droid", true) || v.repositoryUrl.contains("f-droid", true) }
            PreferredRepo.IzzyOnDroid-> { v -> v.repositoryName.contains("izzy", true) || v.repositoryUrl.contains("izzy", true) }
            else -> { _ -> false }
        }

        val sorted = variants.sortedWith(
            compareByDescending<AppVariant> { v ->
                if (preferred != PreferredRepo.Auto && preferMatcher(v)) 1 else 0
            }.thenByDescending { it.isCompatible }
                .thenByDescending { it.versionCode }
        )
        return sorted.first()
    }
}