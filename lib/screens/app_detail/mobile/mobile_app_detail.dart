import 'package:cached_network_image/cached_network_image.dart';
import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/utils/formatters.dart';
import 'package:flicky/widgets/mobile/app_info_section.dart';
import 'package:flicky/widgets/mobile/expandable_description.dart';
import 'package:flicky/widgets/mobile/screenshot_carousel.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class MobileAppDetail extends ConsumerWidget {
  final FDroidApp app;

  const MobileAppDetail({Key? key, required this.app}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 200,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              background: Hero(
                tag: 'app-icon-${app.packageName}',
                child: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Theme.of(context).primaryColor.withOpacity(0.1),
                        Theme.of(context).scaffoldBackgroundColor,
                      ],
                    ),
                  ),
                  child: Center(
                    child: SizedBox(
                      width: 100,
                      height: 100,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(20),
                        child: CachedNetworkImage(
                          imageUrl: app.iconUrl,
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // App Title and Meta
                  Text(
                    app.name,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Chip(label: Text(app.category)),
                      const SizedBox(width: 8),
                      Chip(label: Text('v${app.version}')),
                      const SizedBox(width: 8),
                      Chip(label: Text(Formatters.formatSize(app.size))),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Install Button
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: () => _handleInstall(context, ref),
                      icon: const Icon(Icons.download),
                      label: const Text('Install'),
                      style: ElevatedButton.styleFrom(
                        padding: const EdgeInsets.all(16),
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Screenshots
                  if (app.screenshots.isNotEmpty) ...[
                    Text(
                      'Screenshots',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 12),
                    ScreenshotCarousel(screenshots: app.screenshots),
                    const SizedBox(height: 24),
                  ],

                  // Description
                  Text(
                    'About this app',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 12),
                  ExpandableDescription(
                    description: app.description,
                    maxLines: 5,
                  ),
                  const SizedBox(height: 24),

                  // App Info
                  AppInfoSection(app: app),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _handleInstall(BuildContext context, WidgetRef ref) {
    // Installation logic
  }
}
