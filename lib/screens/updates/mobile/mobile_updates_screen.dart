import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/providers/app_providers.dart';
import 'package:flicky/services/package_info_service.dart';
import 'package:flicky/theme/app_theme.dart';
import 'package:flicky/utils/formatters.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';

class MobileUpdatesScreen extends ConsumerWidget {
  const MobileUpdatesScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final installedAppsAsync = ref.watch(installedAppsProvider);
    final availableUpdatesAsync = ref.watch(availableUpdatesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Updates')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(installedAppsProvider);
          ref.invalidate(availableUpdatesProvider);
        },
        child: CustomScrollView(
          slivers: [
            // Available updates section
            availableUpdatesAsync.when(
              data: (availableUpdates) {
                if (availableUpdates.isNotEmpty) {
                  return SliverToBoxAdapter(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Padding(
                          padding: const EdgeInsets.all(16),
                          child: ElevatedButton(
                            onPressed: () =>
                                _updateAll(context, ref, availableUpdates),
                            style: ElevatedButton.styleFrom(
                              minimumSize: const Size(double.infinity, 48),
                              backgroundColor: AppTheme.primaryGreen,
                            ),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.update, color: Colors.white),
                                const SizedBox(width: 8),
                                Text(
                                  'Update All (${availableUpdates.length})',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 16),
                          child: Text(
                            'Available Updates',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        ...availableUpdates.map((app) => _UpdateCard(app: app)),
                        const Divider(height: 32),
                      ],
                    ),
                  );
                }
                return const SliverToBoxAdapter(child: SizedBox.shrink());
              },
              loading: () =>
                  const SliverToBoxAdapter(child: LinearProgressIndicator()),
              error: (_, __) =>
                  const SliverToBoxAdapter(child: SizedBox.shrink()),
            ),

            // Installed apps section
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Installed Apps',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
              ),
            ),

            installedAppsAsync.when(
              data: (installedApps) {
                if (installedApps.isEmpty) {
                  return SliverFillRemaining(
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: const [
                          Icon(
                            Icons.apps_outlined,
                            size: 64,
                            color: Colors.grey,
                          ),
                          SizedBox(height: 16),
                          Text(
                            'No installed apps',
                            style: TextStyle(fontSize: 18, color: Colors.grey),
                          ),
                          Text(
                            'Apps you install from Flicky will appear here',
                            style: TextStyle(color: Colors.grey),
                          ),
                        ],
                      ),
                    ),
                  );
                }
                return SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) =>
                        _InstalledAppCard(app: installedApps[index]),
                    childCount: installedApps.length,
                  ),
                );
              },
              loading: () => const SliverFillRemaining(
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (_, __) => const SliverFillRemaining(
                child: Center(child: Text('Error loading installed apps')),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _updateAll(
    BuildContext context,
    WidgetRef ref,
    List<FDroidApp> apps,
  ) async {
    for (final app in apps) {
      try {
        await ref
            .read(installationServiceProvider)
            .updateApp(app, (progress) {});
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to update ${app.name}')),
          );
        }
      }
    }

    ref.invalidate(installedAppsProvider);
    ref.invalidate(availableUpdatesProvider);

    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('All apps updated successfully')),
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
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: ListTile(
        leading: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: CachedNetworkImage(
            imageUrl: app.iconUrl,
            width: 48,
            height: 48,
            placeholder: (context, url) => Container(
              color: Colors.grey[300],
              child: const Icon(Icons.android, size: 24),
            ),
            errorWidget: (context, url, error) => Container(
              color: Colors.grey[300],
              child: const Icon(Icons.android, size: 24),
            ),
          ),
        ),
        title: Text(app.name),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('New: v${app.version}'),
            Text(
              'Size: ${Formatters.formatSize(app.size)}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
        trailing: ElevatedButton(
          onPressed: () async {
            try {
              await ref
                  .read(installationServiceProvider)
                  .updateApp(app, (progress) {});
              ref.invalidate(installedAppsProvider);
              ref.invalidate(availableUpdatesProvider);
            } catch (e) {
              if (context.mounted) {
                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(SnackBar(content: Text('Update failed: $e')));
              }
            }
          },
          child: const Text('Update'),
        ),
      ),
    );
  }
}

class _InstalledAppCard extends ConsumerWidget {
  final FDroidApp app;

  const _InstalledAppCard({required this.app});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: ListTile(
        leading: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: CachedNetworkImage(
            imageUrl: app.iconUrl,
            width: 40,
            height: 40,
            placeholder: (context, url) => Container(
              color: Colors.grey[300],
              child: const Icon(Icons.android, size: 20),
            ),
            errorWidget: (context, url, error) => Container(
              color: Colors.grey[300],
              child: const Icon(Icons.android, size: 20),
            ),
          ),
        ),
        title: Text(app.name),
        subtitle: Text('v${app.version}'),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              icon: const Icon(Icons.open_in_new),
              onPressed: () async {
                await PackageInfoService.openApp(app.packageName);
              },
            ),
            IconButton(
              icon: const Icon(Icons.delete_outline, color: Colors.red),
              onPressed: () async {
                final result = await PackageInfoService.uninstallApp(
                  app.packageName,
                );
                if (result) {
                  ref.invalidate(installedAppsProvider);
                  ref.invalidate(availableUpdatesProvider);
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
