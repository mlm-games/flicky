import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/fdroid_app.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';

class AppDetailScreen extends ConsumerStatefulWidget {
  final FDroidApp app;
  
  const AppDetailScreen({required this.app});
  
  @override
  _AppDetailScreenState createState() => _AppDetailScreenState();
}

class _AppDetailScreenState extends ConsumerState<AppDetailScreen> {
  bool isInstalling = false;
  double downloadProgress = 0.0;
  bool? isInstalled;
  
  @override
  void initState() {
    super.initState();
    _checkInstallStatus();
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
      await service.installApp(
        widget.app,
        (progress) {
          if (mounted) {
            setState(() => downloadProgress = progress);
          }
        },
      );
      
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
      appBar: AppBar(
        title: Text(widget.app.name),
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // App header
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // App icon
                Hero(
                  tag: 'app-icon-${widget.app.packageName}',
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(24),
                    child: CachedNetworkImage(
                      imageUrl: widget.app.iconUrl,
                      width: 100,
                      height: 100,
                      fit: BoxFit.cover,
                      placeholder: (context, url) => Container(
                        color: Colors.grey[300],
                        child: Icon(Icons.android, size: 50),
                      ),
                      errorWidget: (context, url, error) => Container(
                        color: Colors.grey[300],
                        child: Icon(Icons.android, size: 50),
                      ),
                    ),
                  ),
                ),
                SizedBox(width: 20),
                
                // App info
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.app.name,
                        style: TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      SizedBox(height: 4),
                      Text(
                        widget.app.packageName,
                        style: TextStyle(
                          color: Colors.grey,
                          fontSize: 12,
                        ),
                      ),
                      SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        children: [
                          Chip(
                            label: Text(widget.app.category),
                            backgroundColor: AppTheme.primaryGreen.withOpacity(0.1),
                          ),
                          Chip(
                            label: Text('v${widget.app.version}'),
                          ),
                          Chip(
                            label: Text(_formatSize(widget.app.size)),
                          ),
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
              ],
            ),
            
            SizedBox(height: 20),
            
            // Action buttons
            if (isInstalling)
              Column(
                children: [
                  LinearProgressIndicator(
                    value: downloadProgress,
                    backgroundColor: Colors.grey[300],
                    valueColor: AlwaysStoppedAnimation(AppTheme.primaryGreen),
                  ),
                  SizedBox(height: 8),
                  Text('Downloading... ${(downloadProgress * 100).toInt()}%'),
                ],
              )
            else if (isInstalled == true)
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _launchApp,
                      icon: Icon(Icons.open_in_new),
                      label: Text('Open'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryGreen,
                        padding: EdgeInsets.all(12),
                      ),
                    ),
                  ),
                  SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _uninstallApp,
                      icon: Icon(Icons.delete_outline),
                      label: Text('Uninstall'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.red,
                        padding: EdgeInsets.all(12),
                      ),
                    ),
                  ),
                ],
              )
            else
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: _installApp,
                  icon: Icon(Icons.download, color: Colors.brown[900]),
                  label: Text(
                    'Install',
                    style: TextStyle(
                      color: Colors.brown[900],
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.primaryGreen,
                    foregroundColor: Colors.brown[900],
                    padding: EdgeInsets.all(16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),
            
            SizedBox(height: 20),
            
            // Summary
            Text(
              widget.app.summary,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
              ),
            ),
            
            SizedBox(height: 20),
            
            // Screenshots
            if (widget.app.screenshots.isNotEmpty) ...[
              Text(
                'Screenshots',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              SizedBox(height: 12),
              Container(
                height: 200,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  itemCount: widget.app.screenshots.length,
                  itemBuilder: (context, index) {
                    return Padding(
                      padding: EdgeInsets.only(right: 12),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: CachedNetworkImage(
                          imageUrl: widget.app.screenshots[index],
                          height: 200,
                          placeholder: (context, url) => Container(
                            width: 120,
                            color: Colors.grey[300],
                          ),
                          errorWidget: (context, url, error) => Container(
                            width: 120,
                            color: Colors.grey[300],
                            child: Icon(Icons.error),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
              SizedBox(height: 20),
            ],
            
            // Description
            Text(
              'Description',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: 12),
            Text(
              widget.app.description,
              style: TextStyle(fontSize: 14, height: 1.5),
            ),
            
            // Anti-features
            if (widget.app.antiFeatures.isNotEmpty) ...[
              SizedBox(height: 20),
              Text(
                'Anti-Features',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Colors.orange,
                ),
              ),
              SizedBox(height: 12),
              ...widget.app.antiFeatures.map((feature) => Padding(
                padding: EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Icon(Icons.warning, color: Colors.orange, size: 20),
                    SizedBox(width: 8),
                    Text(feature),
                  ],
                ),
              )),
            ],
            
            // App info
            SizedBox(height: 20),
            Text(
              'Information',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: 12),
            _InfoRow('Package', widget.app.packageName),
            _InfoRow('Version', 'v${widget.app.version}'),
            _InfoRow('Version Code', widget.app.versionCode.toString()),
            _InfoRow('Size', _formatSize(widget.app.size)),
            _InfoRow('License', widget.app.license),
            _InfoRow('Author', widget.app.author),
            _InfoRow('Updated', _formatDate(widget.app.lastUpdated)),
            _InfoRow('Added', _formatDate(widget.app.added)),
            _InfoRow('Repository', widget.app.repository),
            if (widget.app.website.isNotEmpty)
              _InfoRow('Website', widget.app.website),
            if (widget.app.sourceCode.isNotEmpty)
              _InfoRow('Source Code', widget.app.sourceCode),
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
  
  String _formatDate(DateTime date) {
    return '${date.day}/${date.month}/${date.year}';
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  
  const _InfoRow(this.label, this.value);
  
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: TextStyle(
                color: Colors.grey,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(fontWeight: FontWeight.w400),
            ),
          ),
        ],
      ),
    );
  }
}