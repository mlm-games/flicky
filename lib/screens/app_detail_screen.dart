import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/fdroid_app.dart';
import '../theme/app_theme.dart';
import '../services/fdroid_service.dart';

class AppDetailScreen extends ConsumerStatefulWidget {
  final FDroidApp app;
  
  const AppDetailScreen({required this.app});
  
  @override
  _AppDetailScreenState createState() => _AppDetailScreenState();
}

class _AppDetailScreenState extends ConsumerState<AppDetailScreen> {
  bool isInstalling = false;
  double downloadProgress = 0.0;
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.app.name),
      ),
      body: SingleChildScrollView(
        padding: EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // App info
            Row(
              children: [
                CachedNetworkImage(
                  imageUrl: widget.app.iconUrl,
                  width: 100,
                  height: 100,
                  placeholder: (context, url) => Icon(Icons.android, size: 100),
                  errorWidget: (context, url, error) => Icon(Icons.android, size: 100),
                ),
                SizedBox(width: 20),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.app.name,
                        style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                      ),
                      Text(widget.app.packageName),
                      Text('Version: ${widget.app.version}'),
                    ],
                  ),
                ),
              ],
            ),
            
            SizedBox(height: 20),
            
            // Install button
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: isInstalling ? null : _installApp,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.primaryGreen,
                  padding: EdgeInsets.all(16),
                ),
                child: isInstalling
                    ? Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          CircularProgressIndicator(color: Colors.white),
                          SizedBox(width: 10),
                          Text('Installing... ${(downloadProgress * 100).toInt()}%'),
                        ],
                      )
                    : Text('Install', style: TextStyle(fontSize: 18)),
              ),
            ),
            
            if (downloadProgress > 0 && downloadProgress < 1)
              LinearProgressIndicator(value: downloadProgress),
            
            SizedBox(height: 20),
            
            // Description
            Text(
              'Description',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            SizedBox(height: 10),
            Text(widget.app.description),
          ],
        ),
      ),
    );
  }
  
  Future<void> _installApp() async {
    setState(() {
      isInstalling = true;
      downloadProgress = 0.0;
    });
    
    try {
      await ref.read(fdroidServiceProvider).downloadAndInstall(
        widget.app,
        onProgress: (progress) {
          setState(() => downloadProgress = progress);
        },
      );
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('App installed successfully')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Installation failed: $e')),
        );
      }
    } finally {
      setState(() {
        isInstalling = false;
        downloadProgress = 0.0;
      });
    }
  }
}