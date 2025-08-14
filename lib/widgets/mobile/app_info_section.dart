import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/utils/formatters.dart';
import 'package:flutter/material.dart';

class AppInfoSection extends StatelessWidget {
  final FDroidApp app;

  const AppInfoSection({Key? key, required this.app}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('App Information', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                _InfoRow(
                  icon: Icons.badge,
                  label: 'Package',
                  value: app.packageName,
                ),
                _InfoRow(
                  icon: Icons.tag,
                  label: 'Version',
                  value: 'v${app.version} (${app.versionCode})',
                ),
                _InfoRow(
                  icon: Icons.storage,
                  label: 'Size',
                  value: Formatters.formatSize(app.size),
                ),
                _InfoRow(
                  icon: Icons.gavel,
                  label: 'License',
                  value: app.license,
                ),
                _InfoRow(
                  icon: Icons.person,
                  label: 'Author',
                  value: app.author,
                ),
                _InfoRow(
                  icon: Icons.update,
                  label: 'Updated',
                  value: Formatters.formatDate(app.lastUpdated),
                ),
                _InfoRow(
                  icon: Icons.add_circle_outline,
                  label: 'Added',
                  value: Formatters.formatDate(app.added),
                ),
                _InfoRow(
                  icon: Icons.source,
                  label: 'Repository',
                  value: app.repository,
                ),
                if (app.website.isNotEmpty)
                  _InfoRow(
                    icon: Icons.language,
                    label: 'Website',
                    value: app.website,
                    isLink: true,
                  ),
                if (app.sourceCode.isNotEmpty)
                  _InfoRow(
                    icon: Icons.code,
                    label: 'Source Code',
                    value: app.sourceCode,
                    isLink: true,
                  ),
              ],
            ),
          ),
        ),

        // Anti-features section
        if (app.antiFeatures.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text(
            'Anti-Features',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(color: Colors.orange),
          ),
          const SizedBox(height: 12),
          Card(
            color: Colors.orange.withOpacity(0.1),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: app.antiFeatures
                    .map(
                      (feature) => Padding(
                        padding: const EdgeInsets.symmetric(vertical: 4),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(
                              Icons.warning,
                              color: Colors.orange,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                feature,
                                style: const TextStyle(color: Colors.orange),
                              ),
                            ),
                          ],
                        ),
                      ),
                    )
                    .toList(),
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final bool isLink;

  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.isLink = false,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: Colors.grey),
          const SizedBox(width: 12),
          SizedBox(
            width: 100,
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.grey,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(
                fontWeight: FontWeight.w400,
                color: isLink ? Theme.of(context).primaryColor : null,
                decoration: isLink ? TextDecoration.underline : null,
              ),
              maxLines: isLink ? 1 : null,
              overflow: isLink ? TextOverflow.ellipsis : null,
            ),
          ),
        ],
      ),
    );
  }
}
