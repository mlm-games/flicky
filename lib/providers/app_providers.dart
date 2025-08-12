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