package app.flicky.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(private val dao: AppDao) {
    fun appsFlow(
        query: String,
        sort: SortOption,
        hideAnti: Boolean,
        showIncompatible: Boolean
    ): Flow<List<FDroidApp>> =
        (if (query.isBlank()) dao.observeAll() else dao.search(query)).map { list ->
            val antiFiltered = if (hideAnti) list.filter { it.antiFeatures.isEmpty() } else list
            val compatFiltered = if (showIncompatible) antiFiltered else antiFiltered.filter { it.isCompatible }
            when (sort) {
                SortOption.Name -> compatFiltered.sortedBy { it.name.lowercase() }
                SortOption.NameDesc -> compatFiltered.sortedByDescending { it.name.lowercase() }
                SortOption.Updated -> compatFiltered.sortedByDescending { it.lastUpdated }
                SortOption.Size -> compatFiltered.sortedBy { it.size }
                SortOption.Added -> compatFiltered.sortedByDescending { it.added }
            }
        }

    fun categories(): Flow<List<String>> = dao.categories()

    fun pagedAppsFlow(
        query: String,
        sort: SortOption,
        hideAnti: Boolean,
        showIncompatible: Boolean
    ): Flow<PagingData<FDroidApp>> {
        val sourceFactory: () -> PagingSource<Int, FDroidApp> = {
            val hideAntiInt = if (hideAnti) 1 else 0
            val showIncompatInt = if (showIncompatible) 1 else 0
            when (sort) {
                SortOption.Updated -> dao.pagingByUpdated(query, hideAntiInt, showIncompatInt)
                SortOption.Name -> dao.pagingByName(query, hideAntiInt, showIncompatInt)
                SortOption.NameDesc -> dao.pagingByNameDesc(query, hideAntiInt, showIncompatInt)
                SortOption.Size -> dao.pagingBySize(query, hideAntiInt, showIncompatInt)
                SortOption.Added -> dao.pagingByAdded(query, hideAntiInt, showIncompatInt)
            }
        }
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 120,
                enablePlaceholders = false
            ),
            pagingSourceFactory = sourceFactory
        ).flow
    }
}


