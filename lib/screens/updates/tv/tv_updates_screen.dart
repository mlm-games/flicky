import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/providers/app_providers.dart';
import 'package:flicky/services/package_info_service.dart';
import 'package:flicky/theme/app_theme.dart';
import 'package:flicky/utils/formatters.dart';
import 'package:flicky/widgets/tv_focus_wrapper.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';

class TVUpdatesScreen extends ConsumerWidget {
  const TVUpdatesScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final installedAppsAsync = ref.watch(installedAppsProvider);
    final availableUpdatesAsync = ref.watch(availableUpdatesProvider);

    return Scaffold(
      body: Row(
        children: [
          // Left panel - Updates summary
          Container(
            width: 350,
            padding: const EdgeInsets.all(32),
            decoration: BoxDecoration(
              color: Theme.of(context).cardColor,
              border: Border(
                right: BorderSide(color: Colors.grey.withOpacity(0.2)),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Updates',
                  style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Keep your apps up to date',
                  style: TextStyle(color: Colors.grey, fontSize: 16),
                ),
                const SizedBox(height: 32),

                availableUpdatesAsync.when(
                  data: (updates) {
                    if (updates.isNotEmpty) {
                      return Column(
                        children: [
                          TVFocusWrapper(
                            autofocus: true,
                            onTap: () => _updateAll(context, ref, updates),
                            child: Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: AppTheme.primaryGreen,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.update, color: Colors.white),
                                  const SizedBox(width: 8),
                                  Text(
                                    'Update All (${updates.length})',
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
                          const SizedBox(height: 16),
                          Text(
                            '${updates.length} updates available',
                            style: const TextStyle(fontSize: 16),
                          ),
                        ],
                      );
                    }
                    return Column(
                      children: const [
                        Icon(
                          Icons.check_circle,
                          size: 64,
                          color: AppTheme.primaryGreen,
                        ),
                        SizedBox(height: 16),
                        Text(
                          'All apps are up to date',
                          style: TextStyle(fontSize: 18),
                        ),
                      ],
                    );
                  },
                  loading: () => const CircularProgressIndicator(),
                  error: (_, __) => const Text('Error loading updates'),
                ),

                const SizedBox(height: 32),
                const Divider(),
                const SizedBox(height: 16),

                installedAppsAsync.when(
                  data: (apps) => Text(
                    '${apps.length} installed apps',
                    style: const TextStyle(fontSize: 16),
                  ),
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                ),
              ],
            ),
          ),

          // Right panel - Apps list
          Expanded(
            child: CustomScrollView(
              slivers: [
                // Available updates
                availableUpdatesAsync.when(
                  data: (updates) {
                    if (updates.isEmpty) {
                      return const SliverToBoxAdapter(child: SizedBox.shrink());
                    }
                    return SliverToBoxAdapter(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Padding(
                            padding: EdgeInsets.all(32),
                            child: Text(
                              'Available Updates',
                              style: TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          ...updates.map((app) => _TVUpdateCard(app: app)),
                          const Divider(height: 64),
                        ],
                      ),
                    );
                  },
                  loading: () => const SliverToBoxAdapter(
                    child: LinearProgressIndicator(),
                  ),
                  error: (_, __) =>
                      const SliverToBoxAdapter(child: SizedBox.shrink()),
                ),

                // Installed apps
                const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.all(32),
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
                  data: (apps) {
                    if (apps.isEmpty) {
                      return const SliverFillRemaining(
                        child: Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
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
                            ],
                          ),
                        ),
                      );
                    }
                    return SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) =>
                            _TVInstalledAppCard(app: apps[index]),
                        childCount: apps.length,
                      ),
                    );
                  },
                  loading: () => const SliverFillRemaining(
                    child: Center(child: CircularProgressIndicator()),
                  ),
                  error: (_, __) => const SliverFillRemaining(
                    child: Center(child: Text('Error loading apps')),
                  ),
                ),
              ],
            ),
          ),
        ],
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
  }
}

class _TVUpdateCard extends ConsumerWidget {
  final FDroidApp app;

  const _TVUpdateCard({required this.app});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return TVFocusWrapper(
      onTap: () async {
        await ref
            .read(installationServiceProvider)
            .updateApp(app, (progress) {});
        ref.invalidate(installedAppsProvider);
        ref.invalidate(availableUpdatesProvider);
      },
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 8),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
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
                    child: const Icon(Icons.android, size: 30),
                  ),
                  errorWidget: (context, url, error) => Container(
                    color: Colors.grey[300],
                    child: const Icon(Icons.android, size: 30),
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      app.name,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'New version: ${app.version}',
                      style: const TextStyle(color: Colors.grey, fontSize: 14),
                    ),
                    Text(
                      'Size: ${Formatters.formatSize(app.size)}',
                      style: const TextStyle(color: Colors.grey, fontSize: 12),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: AppTheme.primaryGreen,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'Update',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TVInstalledAppCard extends StatelessWidget {
  final FDroidApp app;

  const _TVInstalledAppCard({required this.app});

  @override
  Widget build(BuildContext context) {
    return TVFocusWrapper(
      onTap: () async {
        await PackageInfoService.openApp(app.packageName);
      },
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 4),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              ClipRRect(
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
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      app.name,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      'v${app.version}',
                      style: const TextStyle(color: Colors.grey, fontSize: 14),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.open_in_new, color: Colors.grey),
            ],
          ),
        ),
      ),
    );
  }
}
