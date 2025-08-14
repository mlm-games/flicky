package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CategoriesScreen(categories: List<String>) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Categories", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        categories.forEach { c ->
            ListItem(headlineContent = { Text(c) })
            Divider()
        }
    }
}
