import 'dart:convert';
import 'package:hive_flutter/hive_flutter.dart';
import '../models/fdroid_app.dart';

class CacheService {
  static const String APPS_BOX = 'apps_cache';
  static const String CACHE_KEY = 'fdroid_apps';
  static const String TIMESTAMP_KEY = 'last_updated';
  
  static Future<void> init() async {
    await Hive.openBox(APPS_BOX);
  }
  
  static Future<void> cacheApps(List<FDroidApp> apps) async {
    final box = Hive.box(APPS_BOX);
    
    // Convert apps to JSON
    final appsJson = apps.map((app) => {
      'packageName': app.packageName,
      'name': app.name,
      'summary': app.summary,
      'description': app.description,
      'iconUrl': app.iconUrl,
      'version': app.version,
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
      'downloads': app.downloads,
      'isInstalled': app.isInstalled,
    }).toList();
    
    await box.put(CACHE_KEY, jsonEncode(appsJson));
    await box.put(TIMESTAMP_KEY, DateTime.now().millisecondsSinceEpoch);
  }
  
  static List<FDroidApp>? getCachedApps() {
    final box = Hive.box(APPS_BOX);
    final cachedData = box.get(CACHE_KEY);
    
    if (cachedData == null) return null;
    
    try {
      final List<dynamic> appsJson = jsonDecode(cachedData);
      return appsJson.map((json) => FDroidApp(
        packageName: json['packageName'],
        name: json['name'],
        summary: json['summary'],
        description: json['description'],
        iconUrl: json['iconUrl'],
        version: json['version'],
        size: json['size'],
        apkUrl: json['apkUrl'],
        license: json['license'],
        category: json['category'],
        author: json['author'],
        website: json['website'],
        sourceCode: json['sourceCode'],
        added: DateTime.fromMillisecondsSinceEpoch(json['added']),
        lastUpdated: DateTime.fromMillisecondsSinceEpoch(json['lastUpdated']),
        screenshots: List<String>.from(json['screenshots']),
        downloads: json['downloads'],
        isInstalled: json['isInstalled'],
      )).toList();
    } catch (e) {
      print('Error loading cached apps: $e');
      return null;
    }
  }
  
  static bool isCacheExpired() {
    final box = Hive.box(APPS_BOX);
    final timestamp = box.get(TIMESTAMP_KEY);
    
    if (timestamp == null) return true;
    
    final lastUpdated = DateTime.fromMillisecondsSinceEpoch(timestamp);
    final now = DateTime.now();
    
    // Cache expires after 1 hour
    return now.difference(lastUpdated).inHours > 1;
  }
}