import 'dart:io';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';
import 'package:dio/dio.dart';
import '../models/fdroid_app.dart';
import 'package_info_service.dart';

class InstallationService {
  static const platform = MethodChannel('com.flicky/install');
  
  final Dio _dio = Dio();
  
  Future<bool> requestInstallPermission() async {
    if (Platform.isAndroid) {
      final status = await Permission.requestInstallPackages.request();
      return status.isGranted;
    }
    return false;
  }
  
  Future<void> installApp(FDroidApp app, Function(double) onProgress) async {
    try {
      // Request permission
      final hasPermission = await requestInstallPermission();
      if (!hasPermission) {
        throw Exception('Install permission denied');
      }
      
      // Download APK
      onProgress(0.0);
      final apkPath = await _downloadApk(app, onProgress);
      
      // Install APK
      await _installApkFile(apkPath);
      
      // Mark as installed
      await PackageInfoService.markAsInstalled(app.packageName, app.version);
      
    } catch (e) {
      throw Exception('Installation failed: $e');
    }
  }
  
  Future<String> _downloadApk(FDroidApp app, Function(double) onProgress) async {
    final tempDir = await getTemporaryDirectory();
    final savePath = '${tempDir.path}/${app.packageName}_${app.version}.apk';
    
    await _dio.download(
      app.apkUrl,
      savePath,
      onReceiveProgress: (received, total) {
        if (total != -1) {
          onProgress(received / total);
        }
      },
    );
    
    return savePath;
  }
  
  Future<void> _installApkFile(String apkPath) async {
    if (Platform.isAndroid) {
      final result = await OpenFilex.open(
        apkPath,
        type: 'application/vnd.android.package-archive',
      );
      
      if (result.type != ResultType.done) {
        throw Exception('Failed to install APK: ${result.message}');
      }
    }
  }
  
  Future<void> uninstallApp(String packageName) async {
    await PackageInfoService.uninstallApp(packageName);
  }
  
  Future<void> launchApp(String packageName) async {
    await PackageInfoService.openApp(packageName);
  }
  
  Future<bool> isAppInstalled(String packageName) async {
    return await PackageInfoService.isAppInstalled(packageName);
  }
  
  Future<void> updateApp(FDroidApp app, Function(double) onProgress) async {
    await installApp(app, onProgress);
  }
}