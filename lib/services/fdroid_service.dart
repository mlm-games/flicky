import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/core/models/repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'apk_installer.dart';
import 'package:path/path.dart' as path;
import 'package:collection/collection.dart';
import 'package_info_service.dart';

final fdroidServiceProvider = Provider<FDroidService>((ref) {
  return FDroidService();
});

class FDroidService {
  final Dio _dio = Dio(
    BaseOptions(
      connectTimeout: Duration(seconds: 30),
      receiveTimeout: Duration(seconds: 60),
      followRedirects: true,
      maxRedirects: 5,
      validateStatus: (status) {
        return status! < 500;
      },
    ),
  );

  static final DeviceInfoPlugin _deviceInfo = DeviceInfoPlugin();

  // Get device ABI using device_info_plus
  static Future<List<String>> _getDeviceAbis() async {
    try {
      if (Platform.isAndroid) {
        final androidInfo = await _deviceInfo.androidInfo;
        return androidInfo.supportedAbis;
      }
    } catch (e) {
      print('Error getting device ABIs: $e');
    }
    return ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'];
  }

  // Clean and normalize URL
  String _normalizeUrl(String url) {
    // Remove trailing slashes
    url = url.trimRight();
    while (url.endsWith('/')) {
      url = url.substring(0, url.length - 1);
    }

    // Ensure proper protocol
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      url = 'https://$url';
    }

    return url;
  }

  // Build proper URL for resources
  String _buildResourceUrl(String baseUrl, String resourcePath) {
    baseUrl = _normalizeUrl(baseUrl);

    // Remove leading slash from resource path if present
    if (resourcePath.startsWith('/')) {
      resourcePath = resourcePath.substring(1);
    }

    // Check if resource path is already a full URL
    if (resourcePath.startsWith('http://') ||
        resourcePath.startsWith('https://')) {
      return resourcePath;
    }

    // Special handling for IzzyOnDroid repository
    if (baseUrl.contains('apt.izzysoft.de') ||
        baseUrl.contains('android.izzysoft.de')) {
      // IzzyOnDroid uses different structure
      if (!resourcePath.startsWith('fdroid/')) {
        return '$baseUrl/$resourcePath';
      }
    }

    return '$baseUrl/$resourcePath';
  }

  Future<List<FDroidApp>> fetchApps() async {
    // Default repositories
    final repos = [Repository.fdroid(), Repository.izzyOnDroid()];

    return fetchAppsFromMultipleRepos(repos);
  }

  Future<List<FDroidApp>> fetchAppsFromMultipleRepos(
    List<Repository> repos,
  ) async {
    List<FDroidApp> allApps = [];

    for (final repo in repos.where((r) => r.enabled)) {
      try {
        print('Fetching apps from ${repo.name} at ${repo.url}');
        final apps = await fetchAppsFromRepo(repo);
        print('Got ${apps.length} apps from ${repo.name}');
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
      final normalizedUrl = _normalizeUrl(repo.url);

      // Try index-v2 first
      try {
        print('Trying index-v2 for ${repo.name}');
        final entryUrl = '$normalizedUrl/entry.json';
        final entryResponse = await _dio.get(entryUrl);
        final entryData = entryResponse.data;

        if (entryData['index'] != null) {
          final indexPath = entryData['index']['name'];
          final indexUrl = _buildResourceUrl(normalizedUrl, indexPath);
          print('Fetching index from: $indexUrl');
          final indexResponse = await _dio.get(indexUrl);
          return await _parseIndexV2(indexResponse.data, repo);
        }
      } catch (e) {
        print('Index v2 not available for ${repo.name}, trying v1: $e');
      }

      // Fallback to index-v1
      try {
        final indexUrl = '$normalizedUrl/index-v1.json';
        print('Fetching index-v1 from: $indexUrl');
        final response = await _dio.get(indexUrl);
        return _parseIndexV1(response.data, repo);
      } catch (e) {
        print('Index v1 also failed for ${repo.name}: $e');
      }

      return [];
    } catch (e) {
      print('Error fetching from ${repo.name}: $e');
      return [];
    }
  }

  List<FDroidApp> _parseIndexV1(Map<String, dynamic> data, Repository repo) {
    final List<FDroidApp> apps = [];
    final normalizedUrl = _normalizeUrl(repo.url);

    try {
      final packages = data['packages'] ?? {};
      final appsData = data['apps'] ?? [];

      for (final appData in appsData) {
        try {
          final packageName = appData['packageName'];
          final packageInfo = packages[packageName];

          if (packageInfo != null && packageInfo.isNotEmpty) {
            final latestPackage = packageInfo[0];

            // Parse name
            final name = appData['name'] ?? packageName;

            // Parse category
            String category = 'Other';
            final categoriesData = appData['categories'];
            if (categoriesData is List && categoriesData.isNotEmpty) {
              category = _normalizeCategory(categoriesData.first.toString());
            }

            // Build APK URL
            String apkUrl = '';
            if (latestPackage['apkName'] != null) {
              apkUrl = _buildResourceUrl(
                normalizedUrl,
                latestPackage['apkName'],
              );
            }

            // Build icon URL
            String iconUrl = _buildIconUrlV1(
              repo,
              packageName,
              appData['icon'],
            );

            apps.add(
              FDroidApp(
                packageName: packageName,
                name: name,
                summary: appData['summary'] ?? '',
                description: appData['description'] ?? '',
                iconUrl: iconUrl,
                version: latestPackage['versionName'] ?? '1.0',
                versionCode: latestPackage['versionCode'] ?? 1,
                size: latestPackage['size'] ?? 0,
                apkUrl: apkUrl,
                license: appData['license'] ?? 'Unknown',
                category: category,
                author: appData['authorName'] ?? 'Unknown',
                website: appData['webSite'] ?? '',
                sourceCode: appData['sourceCode'] ?? '',
                added: DateTime.fromMillisecondsSinceEpoch(
                  appData['added'] ?? DateTime.now().millisecondsSinceEpoch,
                ),
                lastUpdated: DateTime.fromMillisecondsSinceEpoch(
                  appData['lastUpdated'] ??
                      DateTime.now().millisecondsSinceEpoch,
                ),
                screenshots: _parseScreenshotsV1(appData['screenshots'], repo),
                antiFeatures: List<String>.from(appData['antiFeatures'] ?? []),
                downloads: 0,
                isInstalled: false,
                repository: repo.name,
              ),
            );
          }
        } catch (e) {
          print('Error parsing app ${appData['packageName']}: $e');
        }
      }
    } catch (e) {
      print('Error in _parseIndexV1: $e');
    }

    return apps;
  }

  String _buildIconUrlV2(Repository repo, dynamic icon, String packageName) {
    const defaultIcon = 'https://f-droid.org/assets/ic_repo_app_default.png';
    final normalizedUrl = _normalizeUrl(repo.url);

    if (icon == null) {
      return defaultIcon;
    }

    String? iconPath;

    // Handle the nested structure of index-v2 icons
    if (icon is Map) {
      // First, try to get the localized icon entry
      dynamic iconEntry =
          icon['en-US'] ?? icon['en'] ?? icon['en_US'] ?? icon['en_GB'];

      // If no English version, try to get any available locale
      if (iconEntry == null && icon.isNotEmpty) {
        iconEntry = icon.values.first;
      }

      // Now extract the actual icon filename
      if (iconEntry != null) {
        if (iconEntry is Map) {
          // This is the index-v2 format with name, sha256, size
          iconPath = iconEntry['name']?.toString();
        } else if (iconEntry is String) {
          iconPath = iconEntry;
        }
      }
    } else if (icon is String) {
      iconPath = icon;
    }

    if (iconPath == null || iconPath.isEmpty) {
      return defaultIcon;
    }

    // Check if it's already a full URL
    if (iconPath.startsWith('http://') || iconPath.startsWith('https://')) {
      return iconPath;
    }

    // Build the full URL
    return _buildResourceUrl(normalizedUrl, iconPath);
  }

  String? _getLocalizedString(dynamic value) {
    if (value == null) return null;

    // If it's already a string, return it
    if (value is String) return value;

    // If it's a map of localized strings
    if (value is Map) {
      // Check if this is a nested structure (like icon with name, sha256, size)
      if (value.containsKey('name') && value['name'] is String) {
        return value['name'];
      }

      // Try to get the best locale match
      final result =
          value['en-US'] ?? value['en'] ?? value['en_US'] ?? value['en_GB'];

      if (result != null) {
        // If the result is still a Map (nested structure), extract string from it
        if (result is Map && result.containsKey('name')) {
          return result['name']?.toString();
        }
        return result.toString();
      }

      // Fallback to first available value
      if (value.values.isNotEmpty) {
        final firstValue = value.values.first;
        if (firstValue is Map && firstValue.containsKey('name')) {
          return firstValue['name']?.toString();
        }
        return firstValue?.toString();
      }
    }

    return value.toString();
  }

  Future<List<FDroidApp>> _parseIndexV2(
    Map<String, dynamic> data,
    Repository repo,
  ) async {
    final List<FDroidApp> apps = [];
    final normalizedUrl = _normalizeUrl(repo.url);

    try {
      final packages = data['packages'] ?? {};

      for (var entry in packages.entries) {
        try {
          final packageName = entry.key;
          final packageData = entry.value;

          final metadata = packageData['metadata'];
          if (metadata == null) continue;

          final versions = packageData['versions'] ?? {};
          if (versions.isEmpty) continue;

          // Get latest version
          final latestVersionKey = versions.keys.first;
          final latestVersion = versions[latestVersionKey];
          if (latestVersion == null) continue;

          // Get localized name
          String name = packageName;
          try {
            name = _getLocalizedString(metadata['name']) ?? packageName;
          } catch (e) {
            print('Error parsing name for $packageName: $e');
          }

          // Get localized summary and description
          String summary = '';
          String description = '';
          try {
            summary = _getLocalizedString(metadata['summary']) ?? '';
            description = _getLocalizedString(metadata['description']) ?? '';
          } catch (e) {
            print('Error parsing summary/description for $packageName: $e');
          }

          // Parse categories correctly (can be List or Map)
          String category = 'Other';
          try {
            final categoriesData = metadata['categories'];
            if (categoriesData != null) {
              if (categoriesData is List && categoriesData.isNotEmpty) {
                final firstCat = categoriesData.first;
                if (firstCat is String) {
                  category = _normalizeCategory(firstCat);
                } else if (firstCat is Map) {
                  // Localized category
                  category = _normalizeCategory(
                    _getLocalizedString(firstCat) ?? 'Other',
                  );
                }
              } else if (categoriesData is String) {
                category = _normalizeCategory(categoriesData);
              }
            }
          } catch (e) {
            print('Error parsing category for $packageName: $e');
          }

          // Build icon URL
          String iconUrl = 'https://f-droid.org/assets/ic_repo_app_default.png';
          try {
            iconUrl = _buildIconUrlV2(repo, metadata['icon'], packageName);
          } catch (e) {
            print('Error building icon URL for $packageName: $e');
          }

          // Get best APK for device
          final apkInfo = await _getBestApk(latestVersion, repo);

          apps.add(
            FDroidApp(
              packageName: packageName,
              name: name,
              summary: summary,
              description: description,
              iconUrl: iconUrl,
              version: latestVersion['manifest']?['versionName'] ?? '1.0',
              versionCode: latestVersion['manifest']?['versionCode'] ?? 1,
              size: apkInfo['size'] ?? 0,
              apkUrl: apkInfo['url'] ?? '',
              license: metadata['license'] ?? 'Unknown',
              category: category,
              author: metadata['authorName'] ?? 'Unknown',
              website: metadata['webSite'] ?? '',
              sourceCode: metadata['sourceCode'] ?? '',
              added: DateTime.fromMillisecondsSinceEpoch(
                metadata['added'] ?? DateTime.now().millisecondsSinceEpoch,
              ),
              lastUpdated: DateTime.fromMillisecondsSinceEpoch(
                metadata['lastUpdated'] ??
                    DateTime.now().millisecondsSinceEpoch,
              ),
              screenshots: _parseScreenshotsV2(metadata['screenshots'], repo),
              antiFeatures: _parseAntiFeatures(latestVersion['antiFeatures']),
              downloads: 0,
              isInstalled: false,
              repository: repo.name,
            ),
          );
        } catch (e, stack) {
          print('Error parsing app ${entry.key}: $e');
          print('Stack trace: $stack');
        }
      }
    } catch (e) {
      print('Error in _parseIndexV2: $e');
    }

    return apps;
  }

  String _buildIconUrlV1(Repository repo, String packageName, dynamic icon) {
    const defaultIcon = 'https://f-droid.org/assets/ic_repo_app_default.png';
    final normalizedUrl = _normalizeUrl(repo.url);

    if (icon != null && icon is String && icon.isNotEmpty) {
      // Check if it's already a full URL
      if (icon.startsWith('http://') || icon.startsWith('https://')) {
        return icon;
      }

      // For index v1, icons are usually in icons-640 folder
      if (icon.contains('.')) {
        // Has file extension, likely a valid icon filename

        // Special handling for different repositories
        if (normalizedUrl.contains('apt.izzysoft.de') ||
            normalizedUrl.contains('android.izzysoft.de')) {
          // IzzyOnDroid stores icons directly in repo folder
          return _buildResourceUrl(normalizedUrl, 'icons/$icon');
        } else if (normalizedUrl.contains('f-droid.org')) {
          // F-Droid uses icons-640 folder
          return _buildResourceUrl(normalizedUrl, 'icons-640/$icon');
        } else {
          // Try generic approach
          if (icon.contains('/')) {
            return _buildResourceUrl(normalizedUrl, icon);
          } else {
            // Try icons folder first
            return _buildResourceUrl(normalizedUrl, 'icons/$icon');
          }
        }
      }
    }

    return defaultIcon;
  }

  Future<Map<String, dynamic>> _getBestApk(
    Map<String, dynamic> version,
    Repository repo,
  ) async {
    final normalizedUrl = _normalizeUrl(repo.url);
    final file = version['file'];
    if (file == null) return {'url': '', 'size': 0};

    // Get APK info
    final apkName = file['name'] ?? '';
    final size = file['size'] ?? 0;

    if (apkName.isEmpty) {
      return {'url': '', 'size': 0};
    }

    // Get native code ABIs
    final manifest = version['manifest'] ?? {};
    final nativecode = List<String>.from(manifest['nativecode'] ?? []);

    // Build APK URL
    final apkUrl = _buildResourceUrl(normalizedUrl, apkName);

    // If no native code, it's universal
    if (nativecode.isEmpty) {
      return {'url': apkUrl, 'size': size};
    }

    // Check if device ABIs match
    final deviceAbis = await _getDeviceAbis();
    final hasCompatibleAbi = nativecode.any((abi) => deviceAbis.contains(abi));

    if (hasCompatibleAbi) {
      return {'url': apkUrl, 'size': size};
    }

    // No compatible ABI
    print(
      'No compatible ABI for $apkName. Device: $deviceAbis, App: $nativecode',
    );
    return {'url': '', 'size': 0};
  }

  String _normalizeCategory(String category) {
    // Clean up the category string
    final cleaned = category.replaceAll('_', ' ').trim();

    final categoryMap = {
      'System': 'System',
      'Development': 'Development',
      'Games': 'Games',
      'Internet': 'Internet',
      'Multimedia': 'Multimedia',
      'Navigation': 'Navigation',
      'Phone & SMS': 'Phone & SMS',
      'Phone SMS': 'Phone & SMS',
      'Reading': 'Reading',
      'Science & Education': 'Science & Education',
      'Science Education': 'Science & Education',
      'Security': 'Security',
      'Sports & Health': 'Sports & Health',
      'Sports Health': 'Sports & Health',
      'Theming': 'Theming',
      'Time': 'Time',
      'Writing': 'Writing',
      'Money': 'Money',
      'Connectivity': 'Connectivity',
      'Graphics': 'Graphics',
    };

    // Try exact match first
    if (categoryMap.containsKey(cleaned)) {
      return categoryMap[cleaned]!;
    }

    // Try case-insensitive match
    final lowerCleaned = cleaned.toLowerCase();
    for (final entry in categoryMap.entries) {
      if (entry.key.toLowerCase() == lowerCleaned) {
        return entry.value;
      }
    }

    // Return cleaned category or 'Other'
    return cleaned.isNotEmpty ? cleaned : 'Other';
  }

  List<String> _parseScreenshotsV2(dynamic screenshots, Repository repo) {
    if (screenshots == null) return [];

    List<String> result = [];
    final normalizedUrl = _normalizeUrl(repo.url);

    if (screenshots is Map) {
      // TV screenshots first, then phone
      final tvScreenshots = screenshots['tv'];
      final phoneScreenshots = screenshots['phone'];

      void addScreenshots(dynamic screens) {
        if (screens is Map<String, dynamic>) {
          final localized = _getLocalizedList(screens);
          for (var screenshot in localized) {
            if (screenshot is String) {
              result.add(_buildResourceUrl(normalizedUrl, screenshot));
            }
          }
        }
      }

      if (tvScreenshots != null) addScreenshots(tvScreenshots);
      if (phoneScreenshots != null) addScreenshots(phoneScreenshots);
    }

    return result;
  }

  List<String> _parseScreenshotsV1(dynamic screenshots, Repository repo) {
    if (screenshots == null) return [];

    final normalizedUrl = _normalizeUrl(repo.url);
    List<String> result = [];

    if (screenshots is List) {
      for (var screenshot in screenshots) {
        if (screenshot is String) {
          result.add(_buildResourceUrl(normalizedUrl, screenshot));
        }
      }
    }

    return result;
  }

  List<dynamic> _getLocalizedList(Map<String, dynamic> localizedMap) {
    final value =
        localizedMap['en-US'] ??
        localizedMap['en'] ??
        localizedMap.values.firstOrNull ??
        [];
    return value is List ? value : [];
  }

  List<String> _parseAntiFeatures(dynamic antiFeatures) {
    if (antiFeatures == null) return [];

    if (antiFeatures is List) {
      return antiFeatures.whereType<String>().toList();
    }

    if (antiFeatures is Map) {
      return antiFeatures.keys.whereType<String>().toList();
    }

    return [];
  }

  Future<void> downloadAndInstall(
    FDroidApp app, {
    Function(double)? onProgress,
  }) async {
    try {
      // Check if APK URL is available
      if (app.apkUrl.isEmpty) {
        throw Exception('No compatible APK available for this device');
      }

      // Validate URL
      if (!app.apkUrl.startsWith('http://') &&
          !app.apkUrl.startsWith('https://')) {
        throw Exception('Invalid APK URL: ${app.apkUrl}');
      }

      final Directory tempDir = await getTemporaryDirectory();
      final String fileName = '${app.packageName}_${app.version}.apk';
      final String savePath = path.join(tempDir.path, fileName);

      print('Downloading APK from: ${app.apkUrl}');
      print('Saving to: $savePath');

      // Delete old file if exists
      final file = File(savePath);
      if (await file.exists()) {
        await file.delete();
      }

      // Download with proper error handling
      try {
        await _dio.download(
          app.apkUrl,
          savePath,
          onReceiveProgress: (received, total) {
            if (total != -1 && onProgress != null) {
              onProgress(received / total);
            }
          },
          options: Options(
            headers: {'User-Agent': 'Flicky/1.0.0 (Android TV F-Droid Client)'},
          ),
        );
      } catch (e) {
        print('Download error: $e');
        throw Exception('Failed to download APK: $e');
      }

      // Verify file exists and has content
      if (!await file.exists()) {
        throw Exception('Downloaded file not found');
      }

      final fileSize = await file.length();
      if (fileSize == 0) {
        throw Exception('Downloaded file is empty');
      }

      print('Download complete. File size: $fileSize bytes');

      // Install APK
      await ApkInstaller.installApk(savePath);

      // Mark as installed in our tracking
      await PackageInfoService.markAsInstalled(app.packageName, app.version);
    } catch (e) {
      print('Installation error: $e');
      throw Exception('Failed to install: $e');
    }
  }
}
