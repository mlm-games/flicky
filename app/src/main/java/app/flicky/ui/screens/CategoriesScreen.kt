package app.flicky.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.data.model.FDroidApp
import app.flicky.ui.components.cards.MobileAppCard
import app.flicky.ui.components.cards.TVAppCard
import app.flicky.ui.theme.AppColors
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Composable
fun CategoriesScreen(
    onSyncClick: () -> Unit,
                     isSyncing: Boolean,
                     progress: Float
) {
    val pm = LocalContext.current.packageManager
    val config = LocalConfiguration.current
    val isTV = pm.hasSystemFeature("android.software.leanback") || pm.hasSystemFeature("android.hardware.type.television")
    val widthDp = config.screenWidthDp
    val isTablet = widthDp >= 900

    if (isTV || isTablet) {
        TVCategoriesScreen(onSyncClick = onSyncClick, isSyncing = isSyncing, progress = progress)
    } else {
        MobileCategoriesScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileCategoriesScreen() {
    val categories by AppGraph.appRepo.categories().collectAsState(initial = emptyList())
    val appsFlow = AppGraph.appRepo.appsFlow("", app.flicky.data.model.SortOption.Name, hideAnti = false)
    val apps by appsFlow.collectAsState(initial = emptyList())

    var selected by remember { mutableStateOf("All") }
    val filtered = remember(selected, apps) {
        if (selected == "All") apps else apps.filter { it.category == selected }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Chip row
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                FilterChipCategory(
                    label = "All",
                    count = apps.size,
                    selected = selected == "All",
                    onSelect = { selected = "All" }
                )
                Spacer(Modifier.width(8.dp))
                categories.forEach { c ->
                    val count = apps.count { it.category == c }
                    FilterChipCategory(
                        label = c,
                        count = count,
                        selected = selected == c,
                        onSelect = { selected = c }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }

            // Grid
            val columns = when {
                LocalConfiguration.current.screenWidthDp > 900 -> 4
                LocalConfiguration.current.screenWidthDp > 600 -> 3
                else -> 2
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->

                    MobileAppCard(app = app)
                }
            }
        }
    }
}

@Composable
private fun FilterChipCategory(label: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text("$label ($count)") },
        leadingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        }
    )
}

@Composable
private fun TVCategoriesScreen(
    onSyncClick: () -> Unit,
    isSyncing: Boolean,
    progress: Float
) {
    val categories by AppGraph.appRepo.categories().collectAsState(initial = emptyList())
    val apps by AppGraph.appRepo.appsFlow("", app.flicky.data.model.SortOption.Updated, hideAnti = false)
        .collectAsState(initial = emptyList())

    var selected by remember { mutableStateOf("All") }
    val filtered = remember(selected, apps) {
        if (selected == "All") apps else apps.filter { it.category == selected }
    }

    Row(Modifier.fillMaxSize()) {
        CategoriesSidebarTV(
            categories = listOf("All") + categories,
            apps = apps,
            selected = selected,
            onSelect = { selected = it }
        )
        Column(Modifier.weight(1f)) {
            CategoryHeaderTV(
                category = selected,
                count = filtered.size,
                onSyncClick = onSyncClick,
                isSyncing = isSyncing,
                progress = progress
            )
            TVAppGrid(apps = filtered)
        }
    }
}

@Composable
private fun CategoryHeaderTV(
    category: String,
    count: Int,
    onSyncClick: () -> Unit,
    isSyncing: Boolean,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "tv_sync_progress")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = getCategoryIcon(category), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text = category, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            AssistChip(onClick = {}, label = { Text("$count apps") })
            Spacer(Modifier.weight(1f))
            Button(onClick = onSyncClick) { Text("Sync Now") }
        }
        if (isSyncing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CategoriesSidebarTV(
    categories: List<String>,
    apps: List<FDroidApp>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Surface(
        Modifier.width(250.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(20.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(categories) { cat ->
                    val count = if (cat == "All") apps.size else apps.count { it.category == cat }
                    TVCategoryItem(
                        title = cat,
                        isSelected = selected == cat,
                        count = count,
                        onTap = { onSelect(cat) },
                        autofocus = selected == cat
                    )
                }
            }
        }
    }
}

@Composable
private fun TVCategoryItem(
    title: String,
    isSelected: Boolean,
    count: Int,
    onTap: () -> Unit,
    autofocus: Boolean
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> AppColors.PrimaryGreen
        isSelected -> AppColors.PrimaryGreen.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    val bgColor = when {
        isSelected -> AppColors.PrimaryGreen.copy(alpha = 0.08f)
        focused -> Color.Gray.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                BorderStroke(if (focused) 2.dp else if (isSelected) 1.dp else 0.dp, borderColor),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(bgColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        onClick = onTap,
        tonalElevation = if (focused || isSelected) 1.dp else 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getCategoryIcon(title),
                contentDescription = null,
                tint = if (focused || isSelected) AppColors.PrimaryGreen else LocalContentColor.current
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (focused || isSelected) AppColors.PrimaryGreen else LocalContentColor.current,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (focused || isSelected) AppColors.PrimaryGreen else Color.Gray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "$count",
                    color = if (focused || isSelected) Color.White else LocalContentColor.current,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TVAppGrid(apps: List<FDroidApp>) {
    val widthDp = LocalConfiguration.current.screenWidthDp - 250 // subtract sidebar
    val columns = when {
        widthDp > 1400 -> 6
        widthDp > 1200 -> 5
        widthDp > 900 -> 4
        widthDp > 600 -> 3
        else -> 2
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            TVAppCard(app = app, autofocus = false)
        }
    }
}

@Composable
private fun getCategoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (category.lowercase()) {
        "all" -> Icons.Default.Apps
        "connectivity" -> Icons.Default.Wifi
        "development" -> Icons.Default.Code
        "games" -> Icons.Default.Games
        "graphics" -> Icons.Default.Palette
        "internet" -> Icons.Default.Language
        "money" -> Icons.Default.AttachMoney
        "multimedia" -> Icons.Default.Movie
        "navigation" -> Icons.Default.Navigation
        "phone & sms", "phone sms" -> Icons.Default.Phone
        "reading" -> Icons.Default.Book
        "science & education", "science education" -> Icons.Default.School
        "security" -> Icons.Default.Security
        "sports & health", "sports health" -> Icons.Default.FitnessCenter
        "system" -> Icons.Default.SettingsApplications
        "theming" -> Icons.Default.ColorLens
        "time" -> Icons.Default.AccessTime
        "writing" -> Icons.Default.Edit
        else -> Icons.Default.Category
    }
}
