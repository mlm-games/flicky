import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/providers/app_providers.dart';
import 'package:flicky/theme/app_theme.dart';
import 'package:flicky/utils/formatters.dart';
import 'package:flicky/widgets/tv_focus_wrapper.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';

class TVAppDetail extends ConsumerStatefulWidget {
  final FDroidApp app;

  const TVAppDetail({Key? key, required this.app}) : super(key: key);

  @override
  ConsumerState<TVAppDetail> createState() => _TVAppDetailState();
}

class _TVAppDetailState extends ConsumerState<TVAppDetail> {
  bool isInstalling = false;
  double downloadProgress = 0.0;
  bool? isInstalled;
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _checkInstallStatus();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _checkInstallStatus() async {
    final service = ref.read(installationServiceProvider);
    final installed = await service.isAppInstalled(widget.app.packageName);
    if (mounted) {
      setState(() => isInstalled = installed);
    }
  }

  Future<void> _installApp() async {
    setState(() {
      isInstalling = true;
      downloadProgress = 0.0;
    });

    try {
      final service = ref.read(installationServiceProvider);
      await service.installApp(widget.app, (progress) {
        if (mounted) {
          setState(() => downloadProgress = progress);
        }
      });

      await _checkInstallStatus();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${widget.app.name} installed successfully!')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Installation failed: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => isInstalling = false);
      }
    }
  }

  Future<void> _uninstallApp() async {
    final service = ref.read(installationServiceProvider);
    await service.uninstallApp(widget.app.packageName);
    await _checkInstallStatus();
  }

  Future<void> _launchApp() async {
    final service = ref.read(installationServiceProvider);
    await service.launchApp(widget.app.packageName);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: [
          // Left panel - App info and actions
          Container(
            width: 400,
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
                // Back button
                TVFocusWrapper(
                  autofocus: true,
                  onTap: () => Navigator.pop(context),
                  child: Row(
                    children: const [
                      Icon(Icons.arrow_back),
                      SizedBox(width: 8),
                      Text('Back'),
                    ],
                  ),
                ),
                const SizedBox(height: 32),

                // App icon and name
                Center(
                  child: Column(
                    children: [
                      Hero(
                        tag: 'app-icon-${widget.app.packageName}',
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(24),
                          child: CachedNetworkImage(
                            imageUrl: widget.app.iconUrl,
                            width: 120,
                            height: 120,
                            fit: BoxFit.cover,
                            placeholder: (context, url) => Container(
                              color: Colors.grey[300],
                              child: const Icon(Icons.android, size: 60),
                            ),
                            errorWidget: (context, url, error) => Container(
                              color: Colors.grey[300],
                              child: const Icon(Icons.android, size: 60),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        widget.app.name,
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        widget.app.packageName,
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),

                // Action buttons
                if (isInstalling)
                  Column(
                    children: [
                      LinearProgressIndicator(
                        value: downloadProgress,
                        backgroundColor: Colors.grey[300],
                        valueColor: const AlwaysStoppedAnimation(
                          AppTheme.primaryGreen,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Downloading... ${(downloadProgress * 100).toInt()}%',
                      ),
                    ],
                  )
                else if (isInstalled == true)
                  Column(
                    children: [
                      TVFocusWrapper(
                        onTap: _launchApp,
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: AppTheme.primaryGreen,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: const [
                              Icon(Icons.open_in_new, color: Colors.white),
                              SizedBox(width: 8),
                              Text(
                                'Open',
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
                      const SizedBox(height: 12),
                      TVFocusWrapper(
                        onTap: _uninstallApp,
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            border: Border.all(color: Colors.red),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: const [
                              Icon(Icons.delete_outline, color: Colors.red),
                              SizedBox(width: 8),
                              Text(
                                'Uninstall',
                                style: TextStyle(
                                  color: Colors.red,
                                  fontSize: 16,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  )
                else
                  TVFocusWrapper(
                    onTap: _installApp,
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
                          Icon(Icons.download, color: Colors.brown[700]),
                          const SizedBox(width: 8),
                          Text(
                            'Install',
                            style: TextStyle(
                              color: Colors.brown[700],
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),

                const SizedBox(height: 24),

                // App metadata
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    Chip(
                      label: Text(widget.app.category),
                      backgroundColor: AppTheme.primaryGreen.withOpacity(0.1),
                    ),
                    Chip(label: Text('v${widget.app.version}')),
                    Chip(label: Text(Formatters.formatSize(widget.app.size))),
                    if (widget.app.repository.isNotEmpty)
                      Chip(
                        label: Text(widget.app.repository),
                        backgroundColor: widget.app.repository == 'IzzyOnDroid'
                            ? Colors.purple.withOpacity(0.1)
                            : Colors.blue.withOpacity(0.1),
                      ),
                  ],
                ),
              ],
            ),
          ),

          // Right panel - Details
          Expanded(
            child: SingleChildScrollView(
              controller: _scrollController,
              padding: const EdgeInsets.all(32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Summary
                  Text(
                    widget.app.summary,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Screenshots
                  if (widget.app.screenshots.isNotEmpty) ...[
                    const Text(
                      'Screenshots',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 16),
                    SizedBox(
                      height: 240,
                      child: ListView.builder(
                        scrollDirection: Axis.horizontal,
                        itemCount: widget.app.screenshots.length,
                        itemBuilder: (context, index) {
                          return Padding(
                            padding: const EdgeInsets.only(right: 16),
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(12),
                              child: CachedNetworkImage(
                                imageUrl: widget.app.screenshots[index],
                                height: 240,
                                placeholder: (context, url) => Container(
                                  width: 135,
                                  color: Colors.grey[300],
                                ),
                                errorWidget: (context, url, error) => Container(
                                  width: 135,
                                  color: Colors.grey[300],
                                  child: const Icon(Icons.error),
                                ),
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: 32),
                  ],

                  // Description
                  const Text(
                    'Description',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    widget.app.description,
                    style: const TextStyle(fontSize: 16, height: 1.5),
                  ),

                  // Anti-features
                  if (widget.app.antiFeatures.isNotEmpty) ...[
                    const SizedBox(height: 32),
                    const Text(
                      'Anti-Features',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Colors.orange,
                      ),
                    ),
                    const SizedBox(height: 16),
                    ...widget.app.antiFeatures.map(
                      (feature) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Row(
                          children: [
                            const Icon(
                              Icons.warning,
                              color: Colors.orange,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Text(feature, style: const TextStyle(fontSize: 16)),
                          ],
                        ),
                      ),
                    ),
                  ],

                  // App information
                  const SizedBox(height: 32),
                  const Text(
                    'Information',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 16),
                  _buildInfoRow('License', widget.app.license),
                  _buildInfoRow('Author', widget.app.author),
                  _buildInfoRow(
                    'Updated',
                    Formatters.formatDate(widget.app.lastUpdated),
                  ),
                  _buildInfoRow(
                    'Added',
                    Formatters.formatDate(widget.app.added),
                  ),
                  if (widget.app.website.isNotEmpty)
                    _buildInfoRow('Website', widget.app.website),
                  if (widget.app.sourceCode.isNotEmpty)
                    _buildInfoRow('Source Code', widget.app.sourceCode),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.grey,
                fontWeight: FontWeight.w500,
                fontSize: 16,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontWeight: FontWeight.w400, fontSize: 16),
            ),
          ),
        ],
      ),
    );
  }
}
