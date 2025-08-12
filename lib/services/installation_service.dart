import 'dart:io';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';
import 'package:dio/dio.dart';
import '../models/fdroid_app.dart';
import 'fdroid_service.dart';

class InstallationService {
  static const platform = MethodChannel('com.flicky/install');
  
  final FDroidService _fdroidService;
  final Dio _dio = Dio();
  
  InstallationService(this._fdroidService);
  
  Future<bool> requestInstallPermission() async {
    if (Platform.isAndroid) {
      // Request install permission
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
      await _fdroidService.markAsInstalled(app);
      
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
      // Use OpenFilex to install APK
      final result = await OpenFilex.open(
        apkPath,
        type: 'application/vnd.android.package-archive',
      );
      
      if (result.type != ResultType.done) {
        // Fallback to native installation
        try {
          await platform.invokeMethod('installApk', {'path': apkPath});
        } catch (e) {
          throw Exception('Failed to install APK: $e');
        }
      }
    }
  }
  
  Future<void> uninstallApp(String packageName) async {
    if (Platform.isAndroid) {
      try {
        await platform.invokeMethod('uninstallApp', {'packageName': packageName});
        await _fdroidService.markAsUninstalled(packageName);
      } catch (e) {
        throw Exception('Failed to uninstall app: $e');
      }
    }
  }
  
  Future<void> launchApp(String packageName) async {
    if (Platform.isAndroid) {
      try {
        await platform.invokeMethod('launchApp', {'packageName': packageName});
      } catch (e) {
        throw Exception('Failed to launch app: $e');
      }
    }
  }
  
  Future<bool> isAppInstalled(String packageName) async {
    if (Platform.isAndroid) {
      try {
        final result = await platform.invokeMethod('isInstalled', {'packageName': packageName});
        return result as bool;
      } catch (e) {
        // Fallback to checking our local database
        return await _fdroidService.isInstalled(packageName);
      }
    }
    return false;
  }
  
  Future<void> updateApp(FDroidApp app, Function(double) onProgress) async {
    // Same as install for updates
    await installApp(app, onProgress);
  }
  
  Future<void> updateAllApps(List<FDroidApp> apps, Function(int, double) onProgress) async {
    for (int i = 0; i < apps.length; i++) {
      await updateApp(apps[i], (progress) {
        onProgress(i, progress);
      });
    }
  }
}