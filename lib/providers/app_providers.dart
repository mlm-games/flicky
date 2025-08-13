import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/fdroid_app.dart';
import '../models/repository.dart';
import '../services/fdroid_service.dart';
import '../services/installation_service.dart';
import '../services/package_info_service.dart';

// Repository management
final repositoriesProvider = StateNotifierProvider<RepositoryNotifier, List<Repository>>((ref) {
  return RepositoryNotifier();
});

class RepositoryNotifier extends StateNotifier<List<Repository>> {
  RepositoryNotifier() : super([
    Repository.fdroid(),
    Repository.izzyOnDroid(),
    Repository.fdroidArchive(),
    Repository.guardian(),
  ]);
  
  void toggleRepository(String url) {
    state = state.map((repo) {
      if (repo.url == url) {
        return Repository(
          name: repo.name,
          url: repo.url,
          description: repo.description,
          enabled: !repo.enabled,
          lastUpdated: repo.lastUpdated,
          publicKey: repo.publicKey,
        );
      }
      return repo;
    }).toList();
  }
  
  void addRepository(Repository repo) {
    state = [...state, repo];
  }
  
  void removeRepository(String url) {
    state = state.where((repo) => repo.url != url).toList();
  }
}

// Apps provider with multiple repositories
final appsProvider = FutureProvider<List<FDroidApp>>((ref) async {
  final repos = ref.watch(repositoriesProvider);
  final service = ref.watch(fdroidServiceProvider);
  return service.fetchAppsFromMultipleRepos(repos);
});

// Installation service provider
final installationServiceProvider = Provider<InstallationService>((ref) {
  return InstallationService();
});

// Installed apps provider
final installedAppsProvider = FutureProvider<List<FDroidApp>>((ref) async {
  final allApps = await ref.watch(appsProvider.future);
  final installedPackageNames = await PackageInfoService.getInstalledPackageNames();
  
  return allApps.where((app) => installedPackageNames.contains(app.packageName)).toList();
});

// Available updates provider
final availableUpdatesProvider = FutureProvider<List<FDroidApp>>((ref) async {
  final installed = await ref.watch(installedAppsProvider.future);
  final allApps = await ref.watch(appsProvider.future);
  
  final updates = <FDroidApp>[];
  
  for (final installedApp in installed) {
    final currentVersion = await PackageInfoService.getAppVersion(installedApp.packageName);
    if (currentVersion != null) {
      final latestApp = allApps.firstWhere(
        (app) => app.packageName == installedApp.packageName,
        orElse: () => installedApp,
      );
      
      if (latestApp.version != currentVersion) {
        updates.add(latestApp);
      }
    }
  }
  
  return updates;
});


// Download progress provider
final downloadProgressProvider = StateProvider<Map<String, double>>((ref) => {});

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