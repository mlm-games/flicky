import 'dart:io';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import '../models/fdroid_app.dart';
import 'apk_installer.dart';

final fdroidServiceProvider = Provider((ref) => FDroidService());

class FDroidService {
  final dio = Dio();
  static const String REPO_URL = 'https://f-droid.org/repo';
  static const String INDEX_URL = 'https://f-droid.org/repo/index-v2.json';
  
  Future<List<FDroidApp>> fetchApps() async {
    try {
      // Show loading with timeout
      final response = await dio.get(
        INDEX_URL,
        options: Options(
          receiveTimeout: Duration(seconds: 30),
          headers: {
            'Accept': 'application/json',
          },
        ),
        onReceiveProgress: (received, total) {
          if (total != -1) {
            print('Loading repository: ${(received / total * 100).toStringAsFixed(0)}%');
          }
        },
      );
      
      return _parseAppsV2(response.data);
    } catch (e) {
      print('Error fetching apps: $e');
      // Fallback to mock data if real data fails
      return _getMockApps();
    }
  }
  
  List<FDroidApp> _parseAppsV2(Map<String, dynamic> data) {
    final List<FDroidApp> apps = [];
    
    try {
      final repo = data['repo'] as Map<String, dynamic>?;
      final packages = data['packages'] as Map<String, dynamic>?;
      
      if (packages == null) return apps;
      
      packages.forEach((packageName, packageData) {
        try {
          final metadata = packageData['metadata'] as Map<String, dynamic>?;
          final versions = packageData['versions'] as Map<String, dynamic>?;
          
          if (metadata == null || versions == null || versions.isEmpty) return;
          
          // Get latest version
          final latestVersionKey = versions.keys.first;
          final latestVersion = versions[latestVersionKey] as Map<String, dynamic>;
          
          // Parse app data
          final name = _getLocalizedValue(metadata['name']) ?? packageName;
          final summary = _getLocalizedValue(metadata['summary']) ?? '';
          final description = _getLocalizedValue(metadata['description']) ?? '';
          
          // Get icon
          String iconUrl = '';
          final icon = metadata['icon'];
          if (icon != null) {
            if (icon is Map) {
              final enIcon = icon['en-US'] ?? icon['en'] ?? icon.values.firstOrNull;
              if (enIcon != null && enIcon is Map && enIcon['name'] != null) {
                iconUrl = '$REPO_URL/${enIcon['name']}';
              }
            } else if (icon is String) {
              iconUrl = icon.contains('http') ? icon : '$REPO_URL/$icon';
            }
          }
          
          // Fallback
          if (iconUrl.isEmpty) {
            iconUrl = 'https://f-droid.org/assets/ic_repo_app_default.png';
          }
          
          // Get screenshots
          final List<String> screenshots = [];
          final screenshotsData = metadata['screenshots'];
          if (screenshotsData != null) {
            if (screenshotsData is Map) {
              final phoneScreenshots = screenshotsData['phone'];
              if (phoneScreenshots != null && phoneScreenshots is Map) {
                final enScreenshots = phoneScreenshots['en-US'];
                if (enScreenshots != null && enScreenshots is List) {
                  for (final screenshot in enScreenshots) {
                    if (screenshot is Map && screenshot['name'] != null) {
                      screenshots.add('$REPO_URL/${screenshot['name']}');
                    }
                  }
                }
              }
            }
          }
          
          // Get APK info
          final manifest = latestVersion['manifest'] as Map<String, dynamic>?;
          final file = latestVersion['file'] as Map<String, dynamic>?;
          
          apps.add(FDroidApp(
            packageName: packageName,
            name: name,
            summary: summary,
            description: description,
            iconUrl: iconUrl.isNotEmpty ? iconUrl : 'https://f-droid.org/assets/ic_repo_app_default.png',
            version: manifest?['versionName']?.toString() ?? '1.0',
            size: file?['size'] ?? 0,
            apkUrl: file?['name'] != null ? '$REPO_URL/${file!['name']}' : '',
            license: metadata['license']?.toString() ?? 'Unknown',
            category: _parseCategories(metadata['categories']),
            author: metadata['authorName']?.toString() ?? 'Unknown',
            website: metadata['webSite']?.toString() ?? '',
            sourceCode: metadata['sourceCode']?.toString() ?? '',
            added: DateTime.fromMillisecondsSinceEpoch(metadata['added'] ?? 0),
            lastUpdated: DateTime.fromMillisecondsSinceEpoch(latestVersion['added'] ?? 0),
            screenshots: screenshots,
            downloads: 0,
            isInstalled: false,
          ));
        } catch (e) {
          print('Error parsing app $packageName: $e');
        }
      });
    } catch (e) {
      print('Error parsing repository data: $e');
    }
    
    // Sort by last updated
    apps.sort((a, b) => b.lastUpdated.compareTo(a.lastUpdated));
    
    return apps;
  }
  
  String? _getLocalizedValue(dynamic value) {
    if (value == null) return null;
    if (value is String) return value;
    if (value is Map) {
      // Try to get English value first
      return value['en-US']?.toString() ?? 
             value['en']?.toString() ?? 
             value.values.first?.toString();
    }
    return null;
  }
  
  String _parseCategories(dynamic categories) {
    if (categories == null) return 'Other';
    if (categories is List && categories.isNotEmpty) {
      return categories.first.toString();
    }
    if (categories is String) return categories;
    return 'Other';
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
      
      await dio.download(
        app.apkUrl,
        savePath,
        onReceiveProgress: (received, total) {
          if (total != -1 && onProgress != null) {
            onProgress(received / total);
          }
        },
      );
      
      await ApkInstaller.installApk(savePath);
    } catch (e) {
      throw Exception('Failed to install: $e');
    }
  }
  
  // Keep mock data as fallback
  List<FDroidApp> _getMockApps() {
    return [
      FDroidApp(
        packageName: 'org.fdroid.fdroid',
        name: 'F-Droid',
        summary: 'The app store that respects freedom and privacy',
        description: 'F-Droid is an installable catalogue of FOSS applications for Android.',
        iconUrl: 'https://f-droid.org/repo/icons-640/org.fdroid.fdroid.1018051.png',
        version: '1.18.0',
        size: 8 * 1024 * 1024,
        apkUrl: 'https://f-droid.org/repo/org.fdroid.fdroid_1018051.apk',
        license: 'GPL-3.0',
        category: 'System',
        author: 'F-Droid Team',
        website: 'https://f-droid.org',
        sourceCode: 'https://gitlab.com/fdroid/fdroidclient',
        added: DateTime.now().subtract(Duration(days: 30)),
        lastUpdated: DateTime.now().subtract(Duration(days: 1)),
        screenshots: [],
        downloads: 1000000,
        isInstalled: false,
      ),
      FDroidApp(
        packageName: 'com.termux',
        name: 'Termux',
        summary: 'Terminal emulator with packages',
        description: 'Terminal emulator and Linux environment for Android.',
        iconUrl: 'https://f-droid.org/repo/icons-640/com.termux.118.png',
        version: '0.118',
        size: 1024 * 1024,
        apkUrl: 'https://f-droid.org/repo/com.termux_118.apk',
        license: 'GPL-3.0',
        category: 'Development',
        author: 'Fredrik Fornwall',
        website: 'https://termux.com',
        sourceCode: 'https://github.com/termux/termux-app',
        added: DateTime.now().subtract(Duration(days: 60)),
        lastUpdated: DateTime.now().subtract(Duration(days: 5)),
        screenshots: [],
        downloads: 500000,
        isInstalled: false,
      ),
    ];
  }
}