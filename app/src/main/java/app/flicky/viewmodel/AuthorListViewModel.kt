package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthorListUiState(
    val authorName: String = "",
    val title: String = "",
    val isLoading: Boolean = true,
    val appCount: Int = 0
)

class AuthorListViewModel(
    private val authorName: String,
    private val appDao: AppDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthorListUiState(
            authorName = authorName,
            title = authorName,
            isLoading = true
        )
    )
    val uiState: StateFlow<AuthorListUiState> = _uiState.asStateFlow()

    val pagedApps: Flow<PagingData<FDroidApp>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { appDao.pagingByAuthor(authorName) }
    ).flow.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
