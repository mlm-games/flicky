import 'package:hive_flutter/hive_flutter.dart';

class Repository {
  final String name;
  final String url;
  final bool isEnabled;
  
  Repository({
    required this.name,
    required this.url,
    required this.isEnabled,
  });
  
  Map<String, dynamic> toJson() => {
    'name': name,
    'url': url,
    'isEnabled': isEnabled,
  };
  
  factory Repository.fromJson(Map<String, dynamic> json) => Repository(
    name: json['name'],
    url: json['url'],
    isEnabled: json['isEnabled'],
  );
}

class RepositoryService {
  static const String REPO_BOX = 'repositories';
  static const String REPO_KEY = 'repo_list';
  
  static Future<void> init() async {
    await Hive.openBox(REPO_BOX);
    
    // Initialize with default repos if empty
    final repos = getRepositories();
    if (repos.isEmpty) {
      await saveRepositories([
        Repository(name: 'F-Droid', url: 'https://f-droid.org/repo', isEnabled: true),
        Repository(name: 'F-Droid Archive', url: 'https://f-droid.org/archive', isEnabled: false),
        Repository(name: 'IzzyOnDroid', url: 'https://android.izzysoft.de/repo', isEnabled: false),
      ]);
    }
  }
  
  static List<Repository> getRepositories() {
    final box = Hive.box(REPO_BOX);
    final data = box.get(REPO_KEY);
    if (data == null) return [];
    
    return (data as List).map((e) => Repository.fromJson(Map<String, dynamic>.from(e))).toList();
  }
  
  static Future<void> saveRepositories(List<Repository> repos) async {
    final box = Hive.box(REPO_BOX);
    await box.put(REPO_KEY, repos.map((e) => e.toJson()).toList());
  }
  
  static Future<void> addRepository(String name, String url) async {
    final repos = getRepositories();
    repos.add(Repository(name: name, url: url, isEnabled: true));
    await saveRepositories(repos);
  }
  
  static Future<void> toggleRepository(String url, bool isEnabled) async {
    final repos = getRepositories();
    final index = repos.indexWhere((r) => r.url == url);
    if (index != -1) {
      repos[index] = Repository(
        name: repos[index].name,
        url: repos[index].url,
        isEnabled: isEnabled,
      );
      await saveRepositories(repos);
    }
  }
}