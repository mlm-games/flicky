import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final fdroidServiceProvider = Provider((ref) => FDroidService());

class FDroidService {
  final dio = Dio();
  static const String REPO_URL = 'https://f-droid.org/repo';
  
  Future<List<FDroidApp>> fetchApps() async {
    try {
      // Fetch index
      final response = await dio.get('$REPO_URL/index-v1.json');
      final data = response.data;
      
      // Parse apps
      final List<FDroidApp> apps = [];
      final appsData = data['apps'] as List;
      final packagesData = data['packages'] as Map;
      
      for (final appData in appsData) {
        final packageName = appData['packageName'];
        final packageInfo = packagesData[packageName];
        
        if (packageInfo != null && packageInfo.isNotEmpty) {
          final latestPackage = packageInfo[0];
          
          apps.add(FDroidApp(
            packageName: packageName,
            name: appData['name'] ?? '',
            summary: appData['summary'] ?? '',
            description: appData['description'] ?? '',
            iconUrl: '$REPO_URL/${appData['icon']}',
            version: latestPackage['versionName'] ?? '',
            size: latestPackage['size'] ?? 0,
            apkUrl: '$REPO_URL/${latestPackage['apkName']}',
            license: appData['license'] ?? 'Unknown',
            category: appData['categories']?.first ?? 'Other',
            author: appData['authorName'] ?? 'Unknown',
            website: appData['webSite'] ?? '',
            sourceCode: appData['sourceCode'] ?? '',
            added: DateTime.fromMillisecondsSinceEpoch(appData['added'] ?? 0),
            lastUpdated: DateTime.fromMillisecondsSinceEpoch(appData['lastUpdated'] ?? 0),
            screenshots: _parseScreenshots(appData['screenshots']),
            downloads: 0, // F-Droid doesn't provide this
            isInstalled: false, // Check locally
          ));
        }
      }
      
      return apps;
    } catch (e) {
      throw Exception('Failed to fetch apps: $e');
    }
  }
  
  List<String> _parseScreenshots(dynamic screenshots) {
    if (screenshots == null) return [];
    
    final List<String> result = [];
    if (screenshots is List) {
      for (final screenshot in screenshots) {
        result.add('$REPO_URL/$screenshot');
      }
    }
    return result;
  }
  
  Future<void> downloadAndInstall(
    FDroidApp app, {
    Function(double)? onProgress,
  }) async {
    // Download APK
    final apkPath = '/storage/emulated/0/Download/${app.packageName}.apk';
    
    await dio.download(
      app.apkUrl,
      apkPath,
      onReceiveProgress: (received, total) {
        if (total != -1 && onProgress != null) {
          onProgress(received / total);
        }
      },
    );
    
    // Install APK
    // Use install_plugin or implement native platform channel
    await InstallPlugin.installApk(apkPath);
  }
}