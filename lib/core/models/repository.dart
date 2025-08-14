class Repository {
  final String name;
  final String url;
  final String description;
  final bool enabled;
  final DateTime lastUpdated;
  final String publicKey;
  
  Repository({
    required this.name,
    required this.url,
    required this.description,
    required this.enabled,
    required this.lastUpdated,
    required this.publicKey,
  });
  
  factory Repository.fdroid() => Repository(
    name: 'F-Droid',
    url: 'https://f-droid.org/repo',
    description: 'The official F-Droid repository',
    enabled: true,
    lastUpdated: DateTime.now(),
    publicKey: '', // Add actual key
  );
  
  factory Repository.fdroidArchive() => Repository(
    name: 'F-Droid Archive',
    url: 'https://f-droid.org/archive',
    description: 'Old versions of applications from the F-Droid repository',
    enabled: false,
    lastUpdated: DateTime.now(),
    publicKey: '',
  );
  
  factory Repository.izzyOnDroid() => Repository(
    name: 'IzzyOnDroid',
    url: 'https://apt.izzysoft.de/fdroid/repo',
    description: 'IzzyOnDroid repository with additional apps',
    enabled: true,
    lastUpdated: DateTime.now(),
    publicKey: '',
  );
  
  factory Repository.guardian() => Repository(
    name: 'Guardian Project',
    url: 'https://guardianproject.info/fdroid/repo',
    description: 'Privacy and security focused apps',
    enabled: false,
    lastUpdated: DateTime.now(),
    publicKey: '',
  );
}