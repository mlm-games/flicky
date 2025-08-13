import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';
import '../services/repository_sync_service.dart';
import '../models/repository.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  @override
  _SettingsScreenState createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool _isSyncing = false;

  Future<void> _syncRepositories() async {
    setState(() => _isSyncing = true);
    
    try {
      await ref.read(repositorySyncServiceProvider).syncAllRepositories(force: true);
      ref.invalidate(appsProvider);
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Repositories synced successfully')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Sync failed: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSyncing = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final themeMode = ref.watch(themeModeProvider);
    final syncService = ref.watch(repositorySyncServiceProvider);
    
    return Scaffold(
      body: ListView(
        padding: EdgeInsets.all(20),
        children: [
          Row(
            children: [
              Text(
                'Settings',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Spacer(),
              // Sync button
              ElevatedButton.icon(
                onPressed: _isSyncing ? null : _syncRepositories,
                icon: _isSyncing 
                    ? SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(Icons.sync),
                label: Text(_isSyncing ? 'Syncing...' : 'Sync Now'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.primaryGreen,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          SizedBox(height: 8),
          FutureBuilder<DateTime?>(
            future: syncService.getLastSyncTime(),
            builder: (context, snapshot) {
              if (snapshot.hasData && snapshot.data != null) {
                final lastSync = snapshot.data!;
                final diff = DateTime.now().difference(lastSync);
                String timeAgo;
                if (diff.inMinutes < 1) {
                  timeAgo = 'Just now';
                } else if (diff.inHours < 1) {
                  timeAgo = '${diff.inMinutes} minutes ago';
                } else if (diff.inDays < 1) {
                  timeAgo = '${diff.inHours} hours ago';
                } else {
                  timeAgo = '${diff.inDays} days ago';
                }
                return Text(
                  'Last synced: $timeAgo',
                  style: TextStyle(color: Colors.grey, fontSize: 12),
                );
              }
              return Text(
                'Never synced',
                style: TextStyle(color: Colors.grey, fontSize: 12),
              );
            },
          ),
          SizedBox(height: 20),
          
          _SettingsSection(
            title: 'Appearance',
            children: [
              _ThemeSelector(
                currentTheme: themeMode,
                onThemeChanged: (mode) {
                  ref.read(themeModeProvider.notifier).state = mode;
                },
              ),
            ],
          ),
          
          _SettingsSection(
            title: 'Repositories',
            children: [
              ...ref.watch(repositoriesProvider).map((repo) => 
                _TVFriendlyRepositoryTile(
                  repository: repo,
                  onToggle: (enabled) {
                    ref.read(repositoriesProvider.notifier).toggleRepository(repo.url);
                  },
                ),
              ),
              SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => _showAddRepositoryDialog(context),
                icon: Icon(Icons.add),
                label: Text('Add Repository'),
                style: OutlinedButton.styleFrom(
                  padding: EdgeInsets.all(16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
            ],
          ),
          
          _SettingsSection(
            title: 'Downloads',
            children: [
              _TVFriendlySwitch(
                title: 'Auto-update apps',
                subtitle: 'Automatically download and install updates',
                value: false,
                onChanged: (value) {},
              ),
              _TVFriendlySwitch(
                title: 'Update over Wi-Fi only',
                subtitle: 'Only download updates when connected to Wi-Fi',
                value: true,
                onChanged: (value) {},
              ),
              ListTile(
                title: Text('Download location'),
                subtitle: Text('/storage/emulated/0/Download/Flicky'),
                trailing: Icon(Icons.folder),
                onTap: () {},
              ),
            ],
          ),
          
          // About section
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
                  child: Icon(Icons.shop_2, color: Colors.white),
                ),
                title: Text('Flicky-Droid/Store'),
                subtitle: Text('Aptoide TV for Fdroid Repos'),
              ),
              ListTile(
                leading: Icon(Icons.code),
                title: Text('Source Code'),
                subtitle: Text('View on GitHub'),
                onTap: () async {
                    const url = 'https://github.com/mlm-games/flicky';
                    if (await canLaunch(url)) {
                    await launch(url);
                    } else {
                    throw 'Could not launch $url';
                    }
                },
              ),
              ListTile(
                leading: Icon(Icons.bug_report),
                title: Text('Report an Issue (Maybe not from the TV..)'),
                onTap: () {},
              ),
              ListTile(
                leading: Icon(Icons.favorite),
                title: Text('Donate'),
                subtitle: Text('Support me! (if you like the app)'),
                onTap: () async {
                    const url = 'https://github.com/mlm-games/flicky';
                    if (await canLaunch(url)) {
                    await launch(url);
                    } else {
                    throw 'Could not launch $url';
                    }
                },
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _showAddRepositoryDialog(BuildContext context) {
    final urlController = TextEditingController();
    final nameController = TextEditingController();
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Add Repository'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: urlController,
              decoration: InputDecoration(
                labelText: 'Repository URL',
                hintText: 'https://example.com/fdroid/repo',
                border: OutlineInputBorder(),
              ),
              autofocus: true,
            ),
            SizedBox(height: 16),
            TextField(
              controller: nameController,
              decoration: InputDecoration(
                labelText: 'Repository Name (optional)',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              if (urlController.text.isNotEmpty) {
                final name = nameController.text.isEmpty 
                    ? Uri.parse(urlController.text).host 
                    : nameController.text;
                ref.read(repositoriesProvider.notifier).addRepository(
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
            child: Text('Add'),
          ),
        ],
      ),
    );
  }
}

// TV-friendly switch widget
class _TVFriendlySwitch extends StatefulWidget {
  final String title;
  final String subtitle;
  final bool value;
  final Function(bool) onChanged;
  
  const _TVFriendlySwitch({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });
  
  @override
  _TVFriendlySwitchState createState() => _TVFriendlySwitchState();
}

class _TVFriendlySwitchState extends State<_TVFriendlySwitch> {
  late FocusNode _focusNode;
  bool _isFocused = false;
  bool _value = false;
  
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
        duration: Duration(milliseconds: 200),
        padding: EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: _isFocused ? Colors.grey.withOpacity(0.1) : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
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
                  Text(widget.title, style: TextStyle(fontSize: 16)),
                  if (widget.subtitle.isNotEmpty)
                    Text(
                      widget.subtitle,
                      style: TextStyle(fontSize: 12, color: Colors.grey),
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


class _SettingsSection extends StatelessWidget {
  final String title;
  final List<Widget> children;
  
  const _SettingsSection({
    required this.title,
    required this.children,
  });
  
  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 16),
          child: Text(
            title,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: AppTheme.primaryGreen,
            ),
          ),
        ),
        Card(
          child: Column(children: children),
        ),
        SizedBox(height: 20),
      ],
    );
  }
}

class _ThemeSelector extends StatelessWidget {
  final ThemeMode currentTheme;
  final Function(ThemeMode) onThemeChanged;
  
  const _ThemeSelector({
    required this.currentTheme,
    required this.onThemeChanged,
  });
  
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Theme',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w500,
            ),
          ),
          SizedBox(height: 12),
          Row(
            children: [
              _ThemeOption(
                title: 'System',
                icon: Icons.settings_brightness,
                isSelected: currentTheme == ThemeMode.system,
                onTap: () => onThemeChanged(ThemeMode.system),
              ),
              SizedBox(width: 12),
              _ThemeOption(
                title: 'Light',
                icon: Icons.light_mode,
                isSelected: currentTheme == ThemeMode.light,
                onTap: () => onThemeChanged(ThemeMode.light),
              ),
              SizedBox(width: 12),
              _ThemeOption(
                title: 'Dark',
                icon: Icons.dark_mode,
                isSelected: currentTheme == ThemeMode.dark,
                onTap: () => onThemeChanged(ThemeMode.dark),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ThemeOption extends StatefulWidget {
  final String title;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;
  
  const _ThemeOption({
    required this.title,
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });
  
  @override
  _ThemeOptionState createState() => _ThemeOptionState();
}

class _ThemeOptionState extends State<_ThemeOption> {
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
          duration: Duration(milliseconds: 200),
          padding: EdgeInsets.all(16),
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
                color: widget.isSelected || _isFocused ? AppTheme.primaryGreen : null,
              ),
              SizedBox(height: 8),
              Text(
                widget.title,
                style: TextStyle(
                  fontWeight: widget.isSelected ? FontWeight.bold : null,
                  color: widget.isSelected || _isFocused ? AppTheme.primaryGreen : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TVFriendlyRepositoryTile extends StatefulWidget {
  final Repository repository;
  final Function(bool) onToggle;
  
  const _TVFriendlyRepositoryTile({
    required this.repository,
    required this.onToggle,
  });
  
  @override
  _TVFriendlyRepositoryTileState createState() => _TVFriendlyRepositoryTileState();
}

class _TVFriendlyRepositoryTileState extends State<_TVFriendlyRepositoryTile> {
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
        duration: Duration(milliseconds: 200),
        padding: EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: _isFocused ? Colors.grey.withOpacity(0.1) : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: _isFocused ? AppTheme.primaryGreen : Colors.transparent,
            width: 2,
          ),
        ),
        child: Row(
          children: [
            Icon(Icons.storage),
            SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(widget.repository.name, style: TextStyle(fontSize: 16)),
                  Text(
                    widget.repository.url,
                    style: TextStyle(fontSize: 12, color: Colors.grey),
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