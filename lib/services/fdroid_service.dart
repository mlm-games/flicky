import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import '../models/fdroid_app.dart';
import 'apk_installer.dart';

final fdroidServiceProvider = Provider((ref) => FDroidService());

class FDroidService {
  final dio = Dio();
  static const String REPO_URL = 'https://f-droid.org/repo';
  
  Future<List<FDroidApp>> fetchApps() async {
    try {
      // For testing, return mock data first
      return _getMockApps();
      
      // Real implementation (uncomment when ready):
      // final response = await dio.get('$REPO_URL/index-v1.json');
      // return _parseApps(response.data);
    } catch (e) {
      throw Exception('Failed to fetch apps: $e');
    }
  }
  
  Future<void> downloadAndInstall(
    FDroidApp app, {
    Function(double)? onProgress,
  }) async {
    try {
      final Directory tempDir = await getTemporaryDirectory();
      final String fileName = '${app.packageName}_${app.version}.apk';
      final String savePath = '${tempDir.path}/$fileName';
      
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
  
  // Mock data for testing
  List<FDroidApp> _getMockApps() {
    return List.generate(20, (index) => FDroidApp(
      packageName: 'com.example.app$index',
      name: 'App ${index + 1}',
      summary: 'This is a summary for app ${index + 1}',
      description: 'This is a longer description for app ${index + 1}. It contains more details about what the app does.',
      iconUrl: 'https://via.placeholder.com/150',
      version: '1.0.$index',
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
      downloads: 1000 * (index + 1),
      isInstalled: false,
    ));
  }
}