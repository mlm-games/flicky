import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:hive_flutter/hive_flutter.dart';
import '../models/fdroid_app.dart';
import '../models/repository.dart';
import 'apk_installer.dart';

final fdroidServiceProvider = Provider<FDroidService>((ref) {
  return FDroidService();
});

class FDroidService {
  final Dio _dio = Dio(BaseOptions(
    connectTimeout: Duration(seconds: 30),
    receiveTimeout: Duration(seconds: 30),
  ));
  
  Future<List<FDroidApp>> fetchApps() async {
    // Default repositories
    final repos = [
      Repository.fdroid(),
      Repository.izzyOnDroid(),
    ];
    
    return fetchAppsFromMultipleRepos(repos);
  }
  
  Future<List<FDroidApp>> fetchAppsFromMultipleRepos(List<Repository> repos) async {
    List<FDroidApp> allApps = [];
    
    for (final repo in repos.where((r) => r.enabled)) {
      try {
        final apps = await fetchAppsFromRepo(repo);
        allApps.addAll(apps);
      } catch (e) {
        print('Error fetching from ${repo.name}: $e');
      }
    }
    
    // Remove duplicates (prefer F-Droid version)
    final uniqueApps = <String, FDroidApp>{};
    for (final app in allApps) {
      if (!uniqueApps.containsKey(app.packageName) || 
          app.repository == 'F-Droid') {
        uniqueApps[app.packageName] = app;
      }
    }
    
    return uniqueApps.values.toList();
  }
  
  Future<List<FDroidApp>> fetchAppsFromRepo(Repository repo) async {
    try {
      final response = await _dio.get('${repo.url}/index-v1.json');
      return _parseApps(response.data, repo.name);
    } catch (e) {
      print('Error fetching from ${repo.name}: $e');
      return [];
    }
  }
  
  List<FDroidApp> _parseApps(Map<String, dynamic> data, String repoName) {
    final List<FDroidApp> apps = [];
    
    try {
      final packages = data['packages'] ?? {};
      final appsData = data['apps'] ?? [];
      
      for (final appData in appsData) {
        try {
          final packageName = appData['packageName'];
          final packageInfo = packages[packageName];
          
          if (packageInfo != null && packageInfo.isNotEmpty) {
            final latestPackage = packageInfo[0];
            
            apps.add(FDroidApp(
              packageName: packageName,
              name: appData['name'] ?? 'Unknown',
              summary: appData['summary'] ?? '',
              description: appData['description'] ?? '',
              iconUrl: _buildIconUrl(repoName, packageName, appData['icon']),
              version: latestPackage['versionName'] ?? '1.0',
              versionCode: latestPackage['versionCode'] ?? 1,
              size: latestPackage['size'] ?? 0,
              apkUrl: _buildApkUrl(repoName, latestPackage['apkName']),
              license: appData['license'] ?? 'Unknown',
              category: _normalizeCategory(appData['categories']?.first ?? 'Other'),
              author: appData['authorName'] ?? 'Unknown',
              website: appData['webSite'] ?? '',
              sourceCode: appData['sourceCode'] ?? '',
              added: DateTime.fromMillisecondsSinceEpoch(appData['added'] ?? 0),
              lastUpdated: DateTime.fromMillisecondsSinceEpoch(appData['lastUpdated'] ?? 0),
              screenshots: _parseScreenshots(appData['screenshots']),
              antiFeatures: List<String>.from(appData['antiFeatures'] ?? []),
              downloads: 0,
              isInstalled: false,
              repository: repoName,
            ));
          }
        } catch (e) {
          print('Error parsing app: $e');
        }
      }
    } catch (e) {
      print('Error in _parseApps: $e');
    }
    
    return apps;
  }
  
  String _buildIconUrl(String repoName, String packageName, dynamic icon) {
    String baseUrl = repoName == 'IzzyOnDroid' 
        ? 'https://apt.izzysoft.de/fdroid/repo'
        : 'https://f-droid.org/repo';
    
    if (icon != null && icon is String) {
      return '$baseUrl/icons-640/$icon';
    }
    
    return '$baseUrl/icons-640/${packageName}.png';
  }
  
  String _buildApkUrl(String repoName, String apkName) {
    if (repoName == 'IzzyOnDroid') {
      return 'https://apt.izzysoft.de/fdroid/repo/$apkName';
    }
    return 'https://f-droid.org/repo/$apkName';
  }
  
  List<String> _parseScreenshots(dynamic screenshots) {
    if (screenshots == null) return [];
    
    List<String> result = [];
    if (screenshots is List) {
      for (var screenshot in screenshots) {
        if (screenshot is String) {
          result.add('https://f-droid.org/repo/$screenshot');
        }
      }
    }
    
    return result;
  }
  
  String _normalizeCategory(String category) {
    final categoryMap = {
      'System': 'System',
      'Development': 'Development',
      'Games': 'Games',
      'Internet': 'Internet',
      'Multimedia': 'Multimedia',
      'Navigation': 'Navigation',
      'Phone & SMS': 'Phone & SMS',
      'Reading': 'Reading',
      'Science & Education': 'Science & Education',
      'Security': 'Security',
      'Sports & Health': 'Sports & Health',
      'Theming': 'Theming',
      'Time': 'Time',
      'Writing': 'Writing',
      'Money': 'Money',
      'Connectivity': 'Connectivity',
      'Graphics': 'Graphics',
    };
    
    return categoryMap[category] ?? 'Other';
  }
  
  Future<void> downloadAndInstall(
    FDroidApp app, {
    Function(double)? onProgress,
  }) async {
    try {
      final Directory tempDir = await getTemporaryDirectory();
      final String fileName = '${app.packageName}_${app.version}.apk';
      final String savePath = '${tempDir.path}/$fileName';
      
      print('Downloading APK from: ${app.apkUrl}');
      print('Saving to: $savePath');
      
      await _dio.download(
        app.apkUrl,
        savePath,
        onReceiveProgress: (received, total) {
          if (total != -1 && onProgress != null) {
            onProgress(received / total);
          }
        },
      );
      
      await ApkInstaller.installApk(savePath);
      
      // Mark as installed
      await markAsInstalled(app);
    } catch (e) {
      throw Exception('Failed to install: $e');
    }
  }
  
  // Installation tracking with Hive
  Future<void> markAsInstalled(FDroidApp app) async {
    if (!Hive.isBoxOpen('installed_apps')) {
      await Hive.openBox<String>('installed_apps');
    }
    
    final box = Hive.box<String>('installed_apps');
    final installedInfo = jsonEncode({
      'packageName': app.packageName,
      'version': app.version,
      'versionCode': app.versionCode,
      'installedAt': DateTime.now().toIso8601String(),
    });
    
    await box.put(app.packageName, installedInfo);
  }
  
  Future<void> markAsUninstalled(String packageName) async {
    if (!Hive.isBoxOpen('installed_apps')) {
      await Hive.openBox<String>('installed_apps');
    }
    
    final box = Hive.box<String>('installed_apps');
    await box.delete(packageName);
  }
  
  Future<bool> isInstalled(String packageName) async {
    if (!Hive.isBoxOpen('installed_apps')) {
      await Hive.openBox<String>('installed_apps');
    }
    
    final box = Hive.box<String>('installed_apps');
    return box.containsKey(packageName);
  }
  
  Future<List<FDroidApp>> getInstalledApps(List<FDroidApp> allApps) async {
    final installed = <FDroidApp>[];
    
    for (final app in allApps) {
      if (await isInstalled(app.packageName)) {
        installed.add(app);
      }
    }
    
    return installed;
  }
  
  Future<List<FDroidApp>> getUpdatableApps(List<FDroidApp> allApps) async {
    if (!Hive.isBoxOpen('installed_apps')) {
      await Hive.openBox<String>('installed_apps');
    }
    
    final box = Hive.box<String>('installed_apps');
    final updatable = <FDroidApp>[];
    
    for (final app in allApps) {
      final installedInfo = box.get(app.packageName);
      if (installedInfo != null) {
        final info = jsonDecode(installedInfo);
        final installedVersionCode = info['versionCode'] as int;
        if (app.versionCode > installedVersionCode) {
          updatable.add(app);
        }
      }
    }
    
    return updatable;
  }
  
  // Mock data for testing
  List<FDroidApp> _getMockApps() {
    return List.generate(5, (index) => FDroidApp(
      packageName: 'com.example.app$index',
      name: 'App ${index + 1}',
      summary: 'This is a summary for app ${index + 1}',
      description: 'This is a longer description for app ${index + 1}',
      iconUrl: 'https://via.placeholder.com/150',
      version: '1.0.$index',
      versionCode: index + 1,
      size: 1024 * 1024 * (index + 1),
      apkUrl: 'https://example.com/app$index.apk',
      license: 'GPL-3.0',
      category: 'Tools',
      author: 'Author $index',
      website: 'https://example.com',
      sourceCode: 'https://github.com/example/app$index',
      added: DateTime.now().subtract(Duration(days: index * 10)),
      lastUpdated: DateTime.now().subtract(Duration(days: index)),
      screenshots: [],
      antiFeatures: [],
      downloads: 1000 * (index + 1),
      isInstalled: false,
      repository: 'F-Droid',
    ));
  }
}