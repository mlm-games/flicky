import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../models/fdroid_app.dart';
import '../theme/app_theme.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../services/package_info_service.dart';
import '../services/fdroid_service.dart';

class UpdatesScreen extends ConsumerWidget {
    @override
    Widget build(BuildContext context, WidgetRef ref) {
    final installedAppsAsync = ref.watch(installedAppsProvider);
    final availableUpdatesAsync = ref.watch(availableUpdatesProvider);

    return Scaffold(
        body: CustomScrollView(
        slivers: [
            // Header
            SliverToBoxAdapter(
            child: Container(
                padding: EdgeInsets.all(20),
                child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                    Text(
                    'Updates',
                    style: TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                    ),
                    ),
                    SizedBox(height: 8),
                    Text(
                    'Keep your apps up to date',
                    style: TextStyle(
                        color: Colors.grey,
                        fontSize: 16,
                    ),
                    ),
                ],
                ),
            ),
            ),
            
            // Handle async updates
            availableUpdatesAsync.when(
            data: (availableUpdates) {
                if (availableUpdates.isNotEmpty) {
                return SliverToBoxAdapter(
                    child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                        Padding(
                        padding: EdgeInsets.symmetric(horizontal: 20),
                        child: ElevatedButton(
                            onPressed: () => _updateAll(context, ref, availableUpdates),
                            style: ElevatedButton.styleFrom(
                            backgroundColor: AppTheme.primaryGreen,
                            padding: EdgeInsets.all(16),
                            shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                            ),
                            ),
                            child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                                Icon(Icons.update, color: Colors.white),
                                SizedBox(width: 8),
                                Text(
                                'Update All (${availableUpdates.length} apps)',
                                style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                ),
                                ),
                            ],
                            ),
                        ),
                        ),
                        Padding(
                        padding: EdgeInsets.all(20),
                        child: Text(
                            'Available Updates',
                            style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            ),
                        ),
                        ),
                        ...availableUpdates.map((app) => _UpdateCard(app: app)).toList(),
                    ],
                    ),
                );
                }
                return SliverToBoxAdapter(child: SizedBox.shrink());
            },
            loading: () => SliverToBoxAdapter(
                child: Center(child: CircularProgressIndicator()),
            ),
            error: (_, __) => SliverToBoxAdapter(child: SizedBox.shrink()),
            ),
            
            // Installed apps section
            SliverToBoxAdapter(
            child: Padding(
                padding: EdgeInsets.all(20),
                child: Text(
                'Installed Apps',
                style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                ),
                ),
            ),
            ),
            
            installedAppsAsync.when(
            data: (installedApps) {
                if (installedApps.isEmpty) {
                return SliverToBoxAdapter(
                    child: Center(
                    child: Container(
                        padding: EdgeInsets.all(40),
                        child: Column(
                        children: [
                            Icon(
                            Icons.apps_outlined,
                            size: 64,
                            color: Colors.grey,
                            ),
                            SizedBox(height: 16),
                            Text(
                            'No installed apps',
                            style: TextStyle(
                                fontSize: 18,
                                color: Colors.grey,
                            ),
                            ),
                            Text(
                            'Apps you install from Flicky will appear here',
                            style: TextStyle(
                                color: Colors.grey,
                            ),
                            ),
                        ],
                        ),
                    ),
                    ),
                );
                }
                return SliverList(
                delegate: SliverChildBuilderDelegate(
                    (context, index) => _InstalledAppCard(app: installedApps[index]),
                    childCount: installedApps.length,
                ),
                );
            },
            loading: () => SliverToBoxAdapter(
                child: Center(child: CircularProgressIndicator()),
            ),
            error: (_, __) => SliverToBoxAdapter(
                child: Center(child: Text('Error loading installed apps')),
            ),
            ),
        ],
        ),
    );
    }

    Future<void> _updateAll(BuildContext context, WidgetRef ref, List<FDroidApp> apps) async {
    for (final app in apps) {
        try {
        await ref.read(fdroidServiceProvider).downloadAndInstall(app);
        } catch (e) {
        if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to update ${app.name}')),
            );
        }
        }
    }
    
    // Refresh the lists
    ref.invalidate(installedAppsProvider);
    ref.invalidate(availableUpdatesProvider);
    
    if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('All apps updated successfully')),
        );
    }
    }
}



class _UpdateCard extends ConsumerWidget {
  final FDroidApp app;
  
  const _UpdateCard({required this.app});
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Padding(
        padding: EdgeInsets.all(16),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: CachedNetworkImage(
                imageUrl: app.iconUrl,
                width: 60,
                height: 60,
                placeholder: (context, url) => Container(
                  color: Colors.grey[300],
                  child: Icon(Icons.android, size: 30),
                ),
                errorWidget: (context, url, error) => Container(
                  color: Colors.grey[300],
                  child: Icon(Icons.android, size: 30),
                ),
              ),
            ),
            SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    app.name,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'New version: ${app.version}',
                    style: TextStyle(
                      color: Colors.grey,
                      fontSize: 14,
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'Size: ${_formatSize(app.size)}',
                    style: TextStyle(
                      color: Colors.grey,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            ElevatedButton(
              onPressed: () async {
                try {
                  await ref.read(fdroidServiceProvider).downloadAndInstall(app);
                  ref.invalidate(installedAppsProvider);
                  ref.invalidate(availableUpdatesProvider);
                  
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('${app.name} updated successfully')),
                    );
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Update failed: $e')),
                    );
                  }
                }
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.primaryGreen,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              child: Text('Update'),
            ),
          ],
        ),
      ),
    );
  }
  
  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}

class _InstalledAppCard extends ConsumerWidget {
  final FDroidApp app;
  
  const _InstalledAppCard({required this.app});
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Padding(
        padding: EdgeInsets.all(16),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: CachedNetworkImage(
                imageUrl: app.iconUrl,
                width: 48,
                height: 48,
                placeholder: (context, url) => Container(
                  color: Colors.grey[300],
                  child: Icon(Icons.android, size: 24),
                ),
                errorWidget: (context, url, error) => Container(
                  color: Colors.grey[300],
                  child: Icon(Icons.android, size: 24),
                ),
              ),
            ),
            SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    app.name,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    'v${app.version}',
                    style: TextStyle(
                      color: Colors.grey,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
            ),
            TextButton(
              onPressed: () async {
                await PackageInfoService.openApp(app.packageName);
              },
              child: Text('Open'),
            ),
            TextButton(
              onPressed: () async {
                final result = await PackageInfoService.uninstallApp(app.packageName);
                if (result) {
                  ref.invalidate(installedAppsProvider);
                  ref.invalidate(availableUpdatesProvider);
                  
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('${app.name} uninstalled')),
                    );
                  }
                }
              },
              child: Text(
                'Uninstall',
                style: TextStyle(color: Colors.red),
              ),
            ),
          ],
        ),
      ),
    );
  }
}