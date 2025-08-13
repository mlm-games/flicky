import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:crypto/crypto.dart';
import '../models/fdroid_app.dart';
import '../models/repository.dart';
import 'fdroid_service.dart';
import '../providers/app_providers.dart';


class RepositorySyncService {
  static const String SYNC_BOX = 'repository_sync';
  static const String LAST_SYNC_KEY = 'last_sync';
  static const String REPO_DATA_PREFIX = 'repo_data_';
  static const String REPO_HASH_PREFIX = 'repo_hash_';
  static const String APPS_CACHE_KEY = 'cached_apps';
  
  static const Duration CACHE_DURATION = Duration(hours: 6); // Cache for 6 hours
  static const Duration CHECK_INTERVAL = Duration(hours: 1); // Check for updates every hour
  
  final Ref _ref;
  
  RepositorySyncService(this._ref);
  
  static Future<void> init() async {
    await Hive.openBox(SYNC_BOX);
  }
  
  Future<DateTime?> getLastSyncTime() async {
    final box = await Hive.openBox(SYNC_BOX);
    final timestamp = box.get(LAST_SYNC_KEY);
    if (timestamp != null) {
      return DateTime.fromMillisecondsSinceEpoch(timestamp);
    }
    return null;
  }
  
  Future<bool> shouldSync() async {
    final lastSync = await getLastSyncTime();
    if (lastSync == null) return true;
    
    final now = DateTime.now();
    return now.difference(lastSync) > CHECK_INTERVAL;
  }
  
  Future<List<FDroidApp>> getCachedApps() async {
    final box = await Hive.openBox(SYNC_BOX);
    final cachedData = box.get(APPS_CACHE_KEY);
    
    if (cachedData == null) return [];
    
    try {
      final List<dynamic> appsJson = jsonDecode(cachedData);
      return appsJson.map((json) => FDroidApp(
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
        lastUpdated: DateTime.fromMillisecondsSinceEpoch(json['lastUpdated']),
        screenshots: List<String>.from(json['screenshots'] ?? []),
        antiFeatures: List<String>.from(json['antiFeatures'] ?? []),
        downloads: json['downloads'],
        isInstalled: json['isInstalled'],
        repository: json['repository'] ?? 'F-Droid',
      )).toList();
    } catch (e) {
      print('Error loading cached apps: $e');
      return [];
    }
  }
  
  Future<void> cacheApps(List<FDroidApp> apps) async {
    final box = await Hive.openBox(SYNC_BOX);
    
    final appsJson = apps.map((app) => {
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
    }).toList();
    
    await box.put(APPS_CACHE_KEY, jsonEncode(appsJson));
    await box.put(LAST_SYNC_KEY, DateTime.now().millisecondsSinceEpoch);
  }
  
  Future<String> _getRepositoryHash(Repository repo) async {
    // Create a hash of repository metadata to detect changes
    final data = '${repo.url}:${repo.lastUpdated.millisecondsSinceEpoch}';
    final bytes = utf8.encode(data);
    final digest = sha256.convert(bytes);
    return digest.toString();
  }
  
  Future<bool> hasRepositoryChanged(Repository repo) async {
    final box = await Hive.openBox(SYNC_BOX);
    final storedHash = box.get('${REPO_HASH_PREFIX}${repo.url}');
    final currentHash = await _getRepositoryHash(repo);
    
    return storedHash != currentHash;
  }
  
  Future<void> updateRepositoryHash(Repository repo) async {
    final box = await Hive.openBox(SYNC_BOX);
    final hash = await _getRepositoryHash(repo);
    await box.put('${REPO_HASH_PREFIX}${repo.url}', hash);
  }
  
  Future<List<FDroidApp>> syncAllRepositories({bool force = false}) async {
    final shouldUpdate = force || await shouldSync();
    
    if (!shouldUpdate) {
      // Return cached data if available and not expired
      final cached = await getCachedApps();
      if (cached.isNotEmpty) {
        print('Using cached apps (${cached.length} apps)');
        return cached;
      }
    }
    
    print('Syncing repositories...');
    
    // Get enabled repositories
    final repos = _ref.read(repositoriesProvider).where((r) => r.enabled).toList();
    
    // Fetch fresh data
    final service = _ref.read(fdroidServiceProvider);
    final apps = await service.fetchAppsFromMultipleRepos(repos);
    
    // Cache the results
    await cacheApps(apps);
    
    // Update repository hashes
    for (final repo in repos) {
      await updateRepositoryHash(repo);
    }
    
    print('Synced ${apps.length} apps');
    return apps;
  }
  
  Future<void> clearCache() async {
    final box = await Hive.openBox(SYNC_BOX);
    await box.clear();
  }
}