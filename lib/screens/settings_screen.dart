import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';

class SettingsScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);
    
    return Scaffold(
      body: ListView(
        padding: EdgeInsets.all(20),
        children: [
          Text(
            'Settings',
            style: TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.bold,
            ),
          ),
          SizedBox(height: 20),
          
          // Appearance section
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
          
          // Repository section
          _SettingsSection(
            title: 'Repositories',
            children: [
              _RepositoryTile(
                name: 'F-Droid',
                url: 'https://f-droid.org/repo',
                isEnabled: true,
                onToggle: (value) {},
              ),
              _RepositoryTile(
                name: 'F-Droid Archive',
                url: 'https://f-droid.org/archive',
                isEnabled: false,
                onToggle: (value) {},
              ),
              _RepositoryTile(
                name: 'IzzyOnDroid',
                url: 'https://android.izzysoft.de/repo',
                isEnabled: true,
                onToggle: (value) {},
              ),
              SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () {
                  _showAddRepositoryDialog(context);
                },
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
          
          // Download section
          _SettingsSection(
            title: 'Downloads',
            children: [
              SwitchListTile(
                title: Text('Auto-update apps'),
                subtitle: Text('Automatically download and install updates'),
                value: false,
                onChanged: (value) {},
              ),
              SwitchListTile(
                title: Text('Update over Wi-Fi only'),
                subtitle: Text('Only download updates when connected to Wi-Fi'),
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
                title: Text('Flicky'),
                subtitle: Text('Version 1.0.0'),
              ),
              ListTile(
                leading: Icon(Icons.code),
                title: Text('Source Code'),
                subtitle: Text('View on GitHub'),
                onTap: () {},
              ),
              ListTile(
                leading: Icon(Icons.bug_report),
                title: Text('Report an Issue'),
                onTap: () {},
              ),
              ListTile(
                leading: Icon(Icons.favorite),
                title: Text('Donate'),
                subtitle: Text('Support development'),
                onTap: () {},
              ),
            ],
          ),
        ],
      ),
    );
  }
  
  void _showAddRepositoryDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Add Repository'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              decoration: InputDecoration(
                labelText: 'Repository URL',
                hintText: 'https://example.com/fdroid/repo',
                border: OutlineInputBorder(),
              ),
            ),
            SizedBox(height: 16),
            TextField(
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
              // TODO: Add repository
              Navigator.pop(context);
            },
            child: Text('Add'),
          ),
        ],
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

class _ThemeOption extends StatelessWidget {
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
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isSelected
                ? AppTheme.primaryGreen.withOpacity(0.1)
                : Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected
                  ? AppTheme.primaryGreen
                  : Colors.grey.withOpacity(0.3),
              width: 2,
            ),
          ),
          child: Column(
            children: [
              Icon(
                icon,
                color: isSelected ? AppTheme.primaryGreen : null,
              ),
              SizedBox(height: 8),
              Text(
                title,
                style: TextStyle(
                  fontWeight: isSelected ? FontWeight.bold : null,
                  color: isSelected ? AppTheme.primaryGreen : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RepositoryTile extends StatelessWidget {
  final String name;
  final String url;
  final bool isEnabled;
  final Function(bool) onToggle;
  
  const _RepositoryTile({
    required this.name,
    required this.url,
    required this.isEnabled,
    required this.onToggle,
  });
  
  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      title: Text(name),
      subtitle: Text(url),
      value: isEnabled,
      onChanged: onToggle,
      secondary: Icon(Icons.storage),
    );
  }
}