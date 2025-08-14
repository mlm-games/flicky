import 'package:flicky/core/models/repository.dart';
import 'package:flicky/providers/app_providers.dart';
import 'package:flicky/theme/app_theme.dart';
import 'package:flicky/utils/formatters.dart';
import 'package:flicky/widgets/tv_focus_wrapper.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

class TVSettings extends ConsumerStatefulWidget {
  const TVSettings({Key? key}) : super(key: key);

  @override
  ConsumerState<TVSettings> createState() => _TVSettingsState();
}

class _TVSettingsState extends ConsumerState<TVSettings> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final themeMode = ref.watch(themeModeProvider);
    final syncState = ref.watch(syncNotifierProvider);
    final repositories = ref.watch(repositoriesProvider);
    final syncService = ref.watch(repositorySyncServiceProvider);

    return Scaffold(
      body: Row(
        children: [
          // Left panel - Settings categories
          Container(
            width: 300,
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
                  'Settings',
                  style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                FutureBuilder<DateTime?>(
                  future: syncService.getLastSyncTime(),
                  builder: (context, snapshot) {
                    if (snapshot.hasData && snapshot.data != null) {
                      return Text(
                        'Last synced: ${Formatters.formatTimeAgo(snapshot.data!)}',
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 12,
                        ),
                      );
                    }
                    return const Text(
                      'Never synced',
                      style: TextStyle(color: Colors.grey, fontSize: 12),
                    );
                  },
                ),
                const SizedBox(height: 32),

                // Sync button
                TVFocusWrapper(
                  autofocus: true,
                  onTap: syncState.isSyncing
                      ? null
                      : () => ref
                            .read(syncNotifierProvider.notifier)
                            .syncRepositories(force: true),
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
                        syncState.isSyncing
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  valueColor: AlwaysStoppedAnimation(
                                    Colors.white,
                                  ),
                                ),
                              )
                            : const Icon(Icons.sync, color: Colors.white),
                        const SizedBox(width: 8),
                        Text(
                          syncState.isSyncing ? 'Syncing...' : 'Sync Now',
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

                if (syncState.isSyncing) ...[
                  const SizedBox(height: 16),
                  LinearProgressIndicator(
                    value: syncState.progress,
                    backgroundColor: Colors.grey.withOpacity(0.2),
                    valueColor: const AlwaysStoppedAnimation(
                      AppTheme.primaryGreen,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    syncState.status,
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ],
            ),
          ),

          // Right panel - Settings content
          Expanded(
            child: SingleChildScrollView(
              controller: _scrollController,
              padding: const EdgeInsets.all(32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Theme section
                  _TVSettingsSection(
                    title: 'Appearance',
                    children: [
                      _TVThemeSelector(
                        currentTheme: themeMode,
                        onThemeChanged: (mode) {
                          ref.read(themeModeProvider.notifier).state = mode;
                        },
                      ),
                    ],
                  ),

                  // Repositories section
                  _TVSettingsSection(
                    title: 'Repositories',
                    children: [
                      ...repositories.map(
                        (repo) => _TVRepositoryTile(
                          repository: repo,
                          onToggle: (enabled) {
                            ref
                                .read(repositoriesProvider.notifier)
                                .toggleRepository(repo.url);
                          },
                        ),
                      ),
                      const SizedBox(height: 16),
                      TVFocusWrapper(
                        onTap: () => _showAddRepositoryDialog(context, ref),
                        child: Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            border: Border.all(color: AppTheme.primaryGreen),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: const [
                              Icon(Icons.add, color: AppTheme.primaryGreen),
                              SizedBox(width: 8),
                              Text(
                                'Add Repository',
                                style: TextStyle(
                                  color: AppTheme.primaryGreen,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),

                  // Downloads section
                  _TVSettingsSection(
                    title: 'Downloads',
                    children: [
                      _TVToggleSetting(
                        title: 'Auto-update apps',
                        subtitle: 'Automatically download and install updates',
                        value: false,
                        onChanged: (value) {},
                      ),
                      _TVToggleSetting(
                        title: 'Update over Wi-Fi only',
                        subtitle:
                            'Only download updates when connected to Wi-Fi',
                        value: true,
                        onChanged: (value) {},
                      ),
                    ],
                  ),

                  // About section
                  _TVSettingsSection(
                    title: 'About',
                    children: [
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Row(
                            children: [
                              Container(
                                width: 64,
                                height: 64,
                                decoration: BoxDecoration(
                                  color: AppTheme.primaryGreen,
                                  borderRadius: BorderRadius.circular(16),
                                ),
                                child: const Icon(
                                  Icons.shop_2,
                                  color: Colors.white,
                                  size: 32,
                                ),
                              ),
                              const SizedBox(width: 20),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: const [
                                  Text(
                                    'Flicky',
                                    style: TextStyle(
                                      fontSize: 24,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  Text(
                                    'F-Droid client for Android TV',
                                    style: TextStyle(
                                      fontSize: 16,
                                      color: Colors.grey,
                                    ),
                                  ),
                                  SizedBox(height: 8),
                                  Text(
                                    'Version 1.0.0',
                                    style: TextStyle(
                                      fontSize: 14,
                                      color: Colors.grey,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TVFocusWrapper(
                        onTap: () => launchUrl(
                          Uri.parse('https://github.com/mlm-games/flicky'),
                        ),
                        child: Card(
                          child: ListTile(
                            leading: const Icon(Icons.code),
                            title: const Text('Source Code'),
                            subtitle: const Text('View on GitHub'),
                            trailing: const Icon(Icons.open_in_new),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
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
              autofocus: true,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: urlController,
              decoration: const InputDecoration(
                labelText: 'Repository URL',
                hintText: 'https://example.com/fdroid/repo',
                border: OutlineInputBorder(),
              ),
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

class _TVSettingsSection extends StatelessWidget {
  final String title;
  final List<Widget> children;

  const _TVSettingsSection({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 20),
          child: Text(
            title,
            style: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: AppTheme.primaryGreen,
            ),
          ),
        ),
        ...children,
        const SizedBox(height: 32),
      ],
    );
  }
}

class _TVThemeSelector extends StatelessWidget {
  final ThemeMode currentTheme;
  final Function(ThemeMode) onThemeChanged;

  const _TVThemeSelector({
    required this.currentTheme,
    required this.onThemeChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _TVThemeOption(
          title: 'System',
          icon: Icons.settings_brightness,
          isSelected: currentTheme == ThemeMode.system,
          onTap: () => onThemeChanged(ThemeMode.system),
        ),
        const SizedBox(width: 16),
        _TVThemeOption(
          title: 'Light',
          icon: Icons.light_mode,
          isSelected: currentTheme == ThemeMode.light,
          onTap: () => onThemeChanged(ThemeMode.light),
        ),
        const SizedBox(width: 16),
        _TVThemeOption(
          title: 'Dark',
          icon: Icons.dark_mode,
          isSelected: currentTheme == ThemeMode.dark,
          onTap: () => onThemeChanged(ThemeMode.dark),
        ),
      ],
    );
  }
}

class _TVThemeOption extends StatefulWidget {
  final String title;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _TVThemeOption({
    required this.title,
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  State<_TVThemeOption> createState() => _TVThemeOptionState();
}

class _TVThemeOptionState extends State<_TVThemeOption> {
  late FocusNode _focusNode;
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
    _focusNode.addListener(() {
      setState(() {
        _isFocused = _focusNode.hasFocus;
      });
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        focusNode: _focusNode,
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: widget.isSelected
                ? AppTheme.primaryGreen.withOpacity(0.1)
                : _isFocused
                ? Colors.grey.withOpacity(0.2)
                : Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: _isFocused
                  ? AppTheme.primaryGreen
                  : widget.isSelected
                  ? AppTheme.primaryGreen
                  : Colors.grey.withOpacity(0.3),
              width: _isFocused ? 3 : (widget.isSelected ? 2 : 1),
            ),
            boxShadow: _isFocused
                ? [
                    BoxShadow(
                      color: AppTheme.primaryGreen.withOpacity(0.3),
                      blurRadius: 8,
                      spreadRadius: 0,
                    ),
                  ]
                : [],
          ),
          child: Column(
            children: [
              Icon(
                widget.icon,
                size: 32,
                color: widget.isSelected || _isFocused
                    ? AppTheme.primaryGreen
                    : null,
              ),
              const SizedBox(height: 8),
              Text(
                widget.title,
                style: TextStyle(
                  fontWeight: widget.isSelected ? FontWeight.bold : null,
                  color: widget.isSelected || _isFocused
                      ? AppTheme.primaryGreen
                      : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TVRepositoryTile extends StatefulWidget {
  final Repository repository;
  final Function(bool) onToggle;

  const _TVRepositoryTile({required this.repository, required this.onToggle});

  @override
  State<_TVRepositoryTile> createState() => _TVRepositoryTileState();
}

class _TVRepositoryTileState extends State<_TVRepositoryTile> {
  late FocusNode _focusNode;
  bool _isFocused = false;
  bool _isEnabled = false;

  @override
  void initState() {
    super.initState();
    _isEnabled = widget.repository.enabled;
    _focusNode = FocusNode();
    _focusNode.addListener(() {
      setState(() {
        _isFocused = _focusNode.hasFocus;
      });
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  void _toggle() {
    setState(() {
      _isEnabled = !_isEnabled;
    });
    widget.onToggle(_isEnabled);
  }

  @override
  Widget build(BuildContext context) {
    return InkWell(
      focusNode: _focusNode,
      onTap: _toggle,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: _isFocused ? Colors.grey.withOpacity(0.1) : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: _isFocused
                ? AppTheme.primaryGreen
                : Colors.grey.withOpacity(0.3),
            width: _isFocused ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            const Icon(Icons.storage, size: 24),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.repository.name,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  Text(
                    widget.repository.url,
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ),
            ),
            Switch(
              value: _isEnabled,
              onChanged: (value) {
                setState(() => _isEnabled = value);
                widget.onToggle(value);
              },
              activeColor: AppTheme.primaryGreen,
            ),
          ],
        ),
      ),
    );
  }
}

class _TVToggleSetting extends StatefulWidget {
  final String title;
  final String subtitle;
  final bool value;
  final Function(bool) onChanged;

  const _TVToggleSetting({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  @override
  State<_TVToggleSetting> createState() => _TVToggleSettingState();
}

class _TVToggleSettingState extends State<_TVToggleSetting> {
  late FocusNode _focusNode;
  bool _isFocused = false;
  late bool _value;

  @override
  void initState() {
    super.initState();
    _value = widget.value;
    _focusNode = FocusNode();
    _focusNode.addListener(() {
      setState(() {
        _isFocused = _focusNode.hasFocus;
      });
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  void _toggle() {
    setState(() {
      _value = !_value;
    });
    widget.onChanged(_value);
  }

  @override
  Widget build(BuildContext context) {
    return InkWell(
      focusNode: _focusNode,
      onTap: _toggle,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: _isFocused ? Colors.grey.withOpacity(0.1) : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: _isFocused ? AppTheme.primaryGreen : Colors.transparent,
            width: 2,
          ),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(widget.title, style: const TextStyle(fontSize: 16)),
                  if (widget.subtitle.isNotEmpty)
                    Text(
                      widget.subtitle,
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                ],
              ),
            ),
            Switch(
              value: _value,
              onChanged: (value) {
                setState(() => _value = value);
                widget.onChanged(value);
              },
              activeColor: AppTheme.primaryGreen,
            ),
          ],
        ),
      ),
    );
  }
}
