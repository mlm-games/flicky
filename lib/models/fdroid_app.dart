class FDroidApp {
  final String packageName;
  final String name;
  final String summary;
  final String description;
  final String iconUrl;
  final String version;
  final int size;
  final String apkUrl;
  final String license;
  final String category;
  final String author;
  final String website;
  final String sourceCode;
  final DateTime added;
  final DateTime lastUpdated;
  final List<String> screenshots;
  final int downloads;
  final bool isInstalled;
  
  FDroidApp({
    required this.packageName,
    required this.name,
    required this.summary,
    required this.description,
    required this.iconUrl,
    required this.version,
    required this.size,
    required this.apkUrl,
    required this.license,
    required this.category,
    required this.author,
    required this.website,
    required this.sourceCode,
    required this.added,
    required this.lastUpdated,
    required this.screenshots,
    required this.downloads,
    required this.isInstalled,
  });
}