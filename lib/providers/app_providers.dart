import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/fdroid_app.dart';
import '../services/fdroid_service.dart';

// Apps provider
final appsProvider = FutureProvider<List<FDroidApp>>((ref) async {
  return ref.watch(fdroidServiceProvider).fetchApps();
});

// Search provider
final searchQueryProvider = StateProvider<String>((ref) => '');

// Filtered apps provider
final filteredAppsProvider = Provider<AsyncValue<List<FDroidApp>>>((ref) {
  final query = ref.watch(searchQueryProvider).toLowerCase();
  final apps = ref.watch(appsProvider);
  
  return apps.whenData((appList) {
    if (query.isEmpty) return appList;
    
    return appList.where((app) {
      return app.name.toLowerCase().contains(query) ||
             app.summary.toLowerCase().contains(query) ||
             app.packageName.toLowerCase().contains(query);
    }).toList();
  });
});

// Theme provider
final themeModeProvider = StateProvider<ThemeMode>((ref) => ThemeMode.system);

// Categories provider
final categoriesProvider = Provider<List<String>>((ref) {
  final apps = ref.watch(appsProvider);
  
  return apps.maybeWhen(
    data: (appList) {
      final categories = appList.map((app) => app.category).toSet().toList();
      categories.sort();
      return categories;
    },
    orElse: () => [],
  );
});

// Installed apps provider (mock for now)
final installedAppsProvider = Provider<List<FDroidApp>>((ref) {
  // TODO: Get actual installed apps
  return [];
});

// Available updates provider
final availableUpdatesProvider = Provider<List<FDroidApp>>((ref) {
  final installed = ref.watch(installedAppsProvider);
  final allApps = ref.watch(appsProvider).valueOrNull ?? [];
  
  // TODO: Compare versions to find actual updates
  return [];
});

// Sort options
enum SortOption { name, updated, size, added }

final sortOptionProvider = StateProvider<SortOption>((ref) => SortOption.updated);

// Sorted apps provider
final sortedAppsProvider = Provider<AsyncValue<List<FDroidApp>>>((ref) {
  final apps = ref.watch(filteredAppsProvider);
  final sortOption = ref.watch(sortOptionProvider);
  
  return apps.whenData((appList) {
    final sorted = List<FDroidApp>.from(appList);
    
    switch (sortOption) {
      case SortOption.name:
        sorted.sort((a, b) => a.name.compareTo(b.name));
        break;
      case SortOption.updated:
        sorted.sort((a, b) => b.lastUpdated.compareTo(a.lastUpdated));
        break;
      case SortOption.size:
        sorted.sort((a, b) => a.size.compareTo(b.size));
        break;
      case SortOption.added:
        sorted.sort((a, b) => b.added.compareTo(a.added));
        break;
    }
    
    return sorted;
  });
});