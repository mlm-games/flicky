import 'package:flicky/core/models/repository.dart';
import 'package:flicky/providers/app_providers.dart';
import 'package:flicky/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

class MobileSettings extends ConsumerWidget {
  const MobileSettings({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);
    final syncState = ref.watch(syncNotifierProvider);
    final repositories = ref.watch(repositoriesProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
        actions: [
          IconButton(
            icon: syncState.isSyncing
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sync),
            onPressed: syncState.isSyncing
                ? null
                : () => ref
                      .read(syncNotifierProvider.notifier)
                      .syncRepositories(force: true),
          ),
        ],
      ),
      body: ListView(
        children: [
          // Theme Section
          _SettingsSection(
            title: 'Appearance',
            children: [
              ListTile(
                leading: const Icon(Icons.brightness_6),
                title: const Text('Theme'),
                subtitle: Text(_getThemeName(themeMode)),
                onTap: () => _showThemeDialog(context, ref, themeMode),
              ),
            ],
          ),

          // Repositories Section
          _SettingsSection(
            title: 'Repositories',
            children: [
              ...repositories.map(
                (repo) => SwitchListTile(
                  title: Text(repo.name),
                  subtitle: Text(repo.url),
                  value: repo.enabled,
                  onChanged: (value) {
                    ref
                        .read(repositoriesProvider.notifier)
                        .toggleRepository(repo.url);
                  },
                  secondary: const Icon(Icons.storage),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.add),
                title: const Text('Add Repository'),
                onTap: () => _showAddRepositoryDialog(context, ref),
              ),
            ],
          ),

          // Downloads Section
          _SettingsSection(
            title: 'Downloads',
            children: [
              SwitchListTile(
                title: const Text('Auto-update apps'),
                subtitle: const Text(
                  'Automatically download and install updates',
                ),
                value: false,
                onChanged: (value) {},
                secondary: const Icon(Icons.update),
              ),
              SwitchListTile(
                title: const Text('Update over Wi-Fi only'),
                subtitle: const Text(
                  'Only download updates when connected to Wi-Fi',
                ),
                value: true,
                onChanged: (value) {},
                secondary: const Icon(Icons.wifi),
              ),
            ],
          ),

          // About Section
          _SettingsSection(
            title: 'About',
            children: [
              ListTile(
                leading: Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: AppTheme.primaryGreen,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.shop_2, color: Colors.white),
                ),
                title: const Text('Flicky'),
                subtitle: const Text('F-Droid client for Android TV'),
              ),
              ListTile(
                leading: const Icon(Icons.code),
                title: const Text('Source Code'),
                subtitle: const Text('View on GitHub'),
                onTap: () =>
                    launchUrl(Uri.parse('https://github.com/mlm-games/flicky')),
              ),
              ListTile(
                leading: const Icon(Icons.bug_report),
                title: const Text('Report an Issue'),
                onTap: () => launchUrl(
                  Uri.parse('https://github.com/mlm-games/flicky/issues'),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.favorite),
                title: const Text('Support Development'),
                subtitle: const Text('Donate if you like the app'),
                onTap: () =>
                    launchUrl(Uri.parse('https://github.com/mlm-games/flicky')),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _getThemeName(ThemeMode mode) {
    switch (mode) {
      case ThemeMode.system:
        return 'System';
      case ThemeMode.light:
        return 'Light';
      case ThemeMode.dark:
        return 'Dark';
    }
  }

  void _showThemeDialog(
    BuildContext context,
    WidgetRef ref,
    ThemeMode current,
  ) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Choose Theme'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            RadioListTile<ThemeMode>(
              title: const Text('System'),
              subtitle: const Text('Follow system theme'),
              value: ThemeMode.system,
              groupValue: current,
              onChanged: (value) {
                if (value != null) {
                  ref.read(themeModeProvider.notifier).state = value;
                  Navigator.pop(context);
                }
              },
            ),
            RadioListTile<ThemeMode>(
              title: const Text('Light'),
              subtitle: const Text('Always use light theme'),
              value: ThemeMode.light,
              groupValue: current,
              onChanged: (value) {
                if (value != null) {
                  ref.read(themeModeProvider.notifier).state = value;
                  Navigator.pop(context);
                }
              },
            ),
            RadioListTile<ThemeMode>(
              title: const Text('Dark'),
              subtitle: const Text('Always use dark theme'),
              value: ThemeMode.dark,
              groupValue: current,
              onChanged: (value) {
                if (value != null) {
                  ref.read(themeModeProvider.notifier).state = value;
                  Navigator.pop(context);
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showAddRepositoryDialog(BuildContext context, WidgetRef ref) {
    final urlController = TextEditingController();
    final nameController = TextEditingController();

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add Repository'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: const InputDecoration(
                labelText: 'Repository Name',
                hintText: 'e.g., Custom Repo',
                border: OutlineInputBorder(),
              ),
              textCapitalization: TextCapitalization.words,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: urlController,
              decoration: const InputDecoration(
                labelText: 'Repository URL',
                hintText: 'https://example.com/fdroid/repo',
                border: OutlineInputBorder(),
              ),
              keyboardType: TextInputType.url,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              if (urlController.text.isNotEmpty) {
                final name = nameController.text.isEmpty
                    ? Uri.parse(urlController.text).host
                    : nameController.text;
                ref
                    .read(repositoriesProvider.notifier)
                    .addRepository(
                      Repository(
                        name: name,
                        url: urlController.text,
                        description: 'Custom repository',
                        enabled: true,
                        lastUpdated: DateTime.now(),
                        publicKey: '',
                      ),
                    );
                Navigator.pop(context);

                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Added repository: $name')),
                );
              }
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.primaryGreen,
            ),
            child: const Text('Add'),
          ),
        ],
      ),
    );
  }
}

class _SettingsSection extends StatelessWidget {
  final String title;
  final List<Widget> children;

  const _SettingsSection({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
          child: Text(
            title,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.bold,
              color: Theme.of(context).primaryColor,
            ),
          ),
        ),
        ...children,
      ],
    );
  }
}
