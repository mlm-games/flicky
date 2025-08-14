import 'dart:convert';
import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/core/models/repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:crypto/crypto.dart';
import 'fdroid_service.dart';

class RepositorySyncService {
  static const String syncBox = 'repository_sync';
  static const String lastSyncKey = 'last_sync';
  static const String repoDataPrefix = 'repo_data_';
  static const String repoHashPrefix = 'repo_hash_';
  static const String appsCacheKey = 'cached_apps';

  static const Duration cacheDuration = Duration(hours: 6);
  static const Duration checkInterval = Duration(hours: 1);

  final Ref _ref;
  final List<Repository> Function() _getRepositories;
  final FDroidService _fdroidService;

  RepositorySyncService(this._ref, this._getRepositories, this._fdroidService);

  static Future<void> init() async {
    await Hive.openBox(syncBox);
  }

  Future<DateTime?> getLastSyncTime() async {
    final box = await Hive.openBox(syncBox);
    final timestamp = box.get(lastSyncKey);
    if (timestamp != null) {
      return DateTime.fromMillisecondsSinceEpoch(timestamp);
    }
    return null;
  }

  Future<bool> shouldSync() async {
    final lastSync = await getLastSyncTime();
    if (lastSync == null) return true;

    final now = DateTime.now();
    return now.difference(lastSync) > checkInterval;
  }

  Future<List<FDroidApp>> getCachedApps() async {
    final box = await Hive.openBox(syncBox);
    final cachedData = box.get(appsCacheKey);

    if (cachedData == null) return [];

    try {
      final List<dynamic> appsJson = jsonDecode(cachedData);
      return appsJson
          .map(
            (json) => FDroidApp(
              packageName: json['packageName'],
              name: json['name'],
              summary: json['summary'],
              description: json['description'],
              iconUrl: json['iconUrl'],
              version: json['version'],
              versionCode: json['versionCode'] ?? 1,
              size: json['size'],
              apkUrl: json['apkUrl'],
              license: json['license'],
              category: json['category'],
              author: json['author'],
              website: json['website'] ?? '',
              sourceCode: json['sourceCode'] ?? '',
              added: DateTime.fromMillisecondsSinceEpoch(json['added']),
              lastUpdated: DateTime.fromMillisecondsSinceEpoch(
                json['lastUpdated'],
              ),
              screenshots: List<String>.from(json['screenshots'] ?? []),
              antiFeatures: List<String>.from(json['antiFeatures'] ?? []),
              downloads: json['downloads'],
              isInstalled: json['isInstalled'],
              repository: json['repository'] ?? 'F-Droid',
            ),
          )
          .toList();
    } catch (e) {
      // Use debugPrint or logging instead of print
      return [];
    }
  }

  Future<void> cacheApps(List<FDroidApp> apps) async {
    final box = await Hive.openBox(syncBox);

    final appsJson = apps
        .map(
          (app) => {
            'packageName': app.packageName,
            'name': app.name,
            'summary': app.summary,
            'description': app.description,
            'iconUrl': app.iconUrl,
            'version': app.version,
            'versionCode': app.versionCode,
            'size': app.size,
            'apkUrl': app.apkUrl,
            'license': app.license,
            'category': app.category,
            'author': app.author,
            'website': app.website,
            'sourceCode': app.sourceCode,
            'added': app.added.millisecondsSinceEpoch,
            'lastUpdated': app.lastUpdated.millisecondsSinceEpoch,
            'screenshots': app.screenshots,
            'antiFeatures': app.antiFeatures,
            'downloads': app.downloads,
            'isInstalled': app.isInstalled,
            'repository': app.repository,
          },
        )
        .toList();

    await box.put(appsCacheKey, jsonEncode(appsJson));
    await box.put(lastSyncKey, DateTime.now().millisecondsSinceEpoch);
  }

  Future<String> _getRepositoryHash(Repository repo) async {
    final data = '${repo.url}:${repo.lastUpdated.millisecondsSinceEpoch}';
    final bytes = utf8.encode(data);
    final digest = sha256.convert(bytes);
    return digest.toString();
  }

  Future<bool> hasRepositoryChanged(Repository repo) async {
    final box = await Hive.openBox(syncBox);
    final storedHash = box.get('$repoHashPrefix${repo.url}');
    final currentHash = await _getRepositoryHash(repo);

    return storedHash != currentHash;
  }

  Future<void> updateRepositoryHash(Repository repo) async {
    final box = await Hive.openBox(syncBox);
    final hash = await _getRepositoryHash(repo);
    await box.put('$repoHashPrefix${repo.url}', hash);
  }

  Future<List<FDroidApp>> syncAllRepositories({
    bool force = false,
    Function(String status, double progress)? onProgress,
  }) async {
    final shouldUpdate = force || await shouldSync();

    if (!shouldUpdate) {
      final cached = await getCachedApps();
      if (cached.isNotEmpty) {
        return cached;
      }
    }

    final repos = _getRepositories().where((r) => r.enabled).toList();
    final allApps = <FDroidApp>[];

    for (int i = 0; i < repos.length; i++) {
      final repo = repos[i];
      final progress = i / repos.length;

      onProgress?.call('Syncing ${repo.name}...', progress);

      try {
        final apps = await _fdroidService.fetchAppsFromRepo(repo);
        allApps.addAll(apps);
        await updateRepositoryHash(repo);
      } catch (e) {
        print('Error syncing ${repo.name}: $e');
      }

      onProgress?.call('Synced ${repo.name}', (i + 1) / repos.length);
    }

    onProgress?.call('Caching apps...', 0.95);
    await cacheApps(allApps);

    onProgress?.call('Complete', 1.0);

    return allApps;
  }

  Future<void> clearCache() async {
    final box = await Hive.openBox(syncBox);
    await box.clear();
  }
}
