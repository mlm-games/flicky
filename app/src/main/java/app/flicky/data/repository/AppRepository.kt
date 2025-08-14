package app.flicky.data.repository

import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(private val dao: AppDao) {
    fun appsFlow(query: String, sort: SortOption, hideAnti: Boolean): Flow<List<FDroidApp>> =
        (if (query.isBlank()) dao.observeAll() else dao.search(query)).map { list ->
            val filtered = if (hideAnti) list.filter { it.antiFeatures.isEmpty() } else list
            when (sort) {
                SortOption.Name -> filtered.sortedBy { it.name.lowercase() }
                SortOption.Updated -> filtered.sortedByDescending { it.lastUpdated }
                SortOption.Size -> filtered.sortedBy { it.size }
                SortOption.Added -> filtered.sortedByDescending { it.added }
            }
        }

    fun categories(): Flow<List<String>> = dao.categories()

}