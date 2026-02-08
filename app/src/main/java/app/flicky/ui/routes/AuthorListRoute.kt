package app.flicky.ui.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.ui.components.AdaptiveAppCard
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.viewmodel.AuthorListViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorListRoute(
    authorName: String,
    onAppClick: (FDroidApp) -> Unit,
    vm: AuthorListViewModel = koinViewModel(parameters = { parametersOf(authorName) })
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val apps = vm.pagedApps.collectAsLazyPagingItems()

    MyScreenScaffold(
        title = stringResource(R.string.more_apps_by_author, uiState.title),
        actions = {
            if (apps.itemCount > 0) {
                Text(
                    text = stringResource(R.string.app_count, apps.itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                apps.itemCount == 0 -> {
                    Text(
                        text = stringResource(R.string.no_apps_by_author, authorName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(apps.itemCount) { index ->
                            apps[index]?.let { app ->
                                AdaptiveAppCard(
                                    app = app,
                                    autofocus = index == 0,
                                    onClick = { onAppClick(app) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
