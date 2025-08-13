import 'package:installed_apps/installed_apps.dart';
import 'package:installed_apps/app_info.dart';
import 'package:hive_flutter/hive_flutter.dart';

class PackageInfoService {
  static const String INSTALLED_APPS_BOX = 'installed_apps_tracking';
  
  static Future<void> init() async {
    await Hive.openBox(INSTALLED_APPS_BOX);
  }
  
  // Get actually installed apps from system
  static Future<List<String>> getInstalledPackageNames() async {
    try {
      // Parameters are positional: excludeSystemApps, withIcon, packageNamePrefix
      final apps = await InstalledApps.getInstalledApps(
        true,  // excludeSystemApps
        false, // withIcon (we don't need icons here)
        '',    // packageNamePrefix (empty means all apps)
      );
      return apps.map((app) => app.packageName).toList();
    } catch (e) {
      print('Error getting installed apps: $e');
      // Fallback to Hive tracking
      final box = Hive.box(INSTALLED_APPS_BOX);
      return box.keys.cast<String>().toList();
    }
  }
  
  // Check if app is installed
  static Future<bool> isAppInstalled(String packageName) async {
    try {
      final isInstalled = await InstalledApps.isAppInstalled(packageName);
      // Handle nullable return
      return isInstalled ?? false;
    } catch (e) {
      final box = Hive.box(INSTALLED_APPS_BOX);
      return box.containsKey(packageName);
    }
  }
  
  // Get app version
  static Future<String?> getAppVersion(String packageName) async {
    try {
      final info = await InstalledApps.getAppInfo(packageName, BuiltWith.flutter);
      return info?.versionName;
    } catch (e) {
      final box = Hive.box(INSTALLED_APPS_BOX);
      final data = box.get(packageName);
      return data?['version'];
    }
  }
  
  // Open app
  static Future<void> openApp(String packageName) async {
    try {
      await InstalledApps.startApp(packageName);
    } catch (e) {
      print('Error opening app: $e');
    }
  }
  
  // Uninstall app (triggers system uninstall dialog)
  static Future<bool> uninstallApp(String packageName) async {
    try {
      final result = await InstalledApps.uninstallApp(packageName);
      if (result == true) {
        await markAsUninstalled(packageName);
      }
      return result ?? false;
    } catch (e) {
      print('Error uninstalling app: $e');
      return false;
    }
  }
  
  // Open app settings
  static Future<void> openAppSettings(String packageName) async {
    try {
      await InstalledApps.openSettings(packageName);
    } catch (e) {
      print('Error opening app settings: $e');
    }
  }
  
  // Check if system app
  static Future<bool> isSystemApp(String packageName) async {
    try {
      final result = await InstalledApps.isSystemApp(packageName);
      return result ?? false;
    } catch (e) {
      return false;
    }
  }
  
  // Track installation in Hive (for apps we install)
  static Future<void> markAsInstalled(String packageName, String version) async {
    final box = Hive.box(INSTALLED_APPS_BOX);
    await box.put(packageName, {
      'version': version,
      'installedAt': DateTime.now().toIso8601String(),
    });
  }
  
  static Future<void> markAsUninstalled(String packageName) async {
    final box = Hive.box(INSTALLED_APPS_BOX);
    await box.delete(packageName);
  }
  
  // Get app info with icon
  static Future<AppInfo?> getAppInfoWithIcon(String packageName) async {
    try {
      return await InstalledApps.getAppInfo(packageName, BuiltWith.flutter);
    } catch (e) {
      print('Error getting app info: $e');
      return null;
    }
  }
  
  // Get all installed F-Droid apps (apps we track)
  static Future<Map<String, dynamic>> getTrackedApps() async {
    final box = Hive.box(INSTALLED_APPS_BOX);
    final Map<String, dynamic> trackedApps = {};
    
    for (final key in box.keys) {
      final value = box.get(key);
      if (value != null && key is String) {
        trackedApps[key] = value;
      }
    }
    
    return trackedApps;
  }
}