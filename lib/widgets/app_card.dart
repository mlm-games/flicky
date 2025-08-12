import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/fdroid_app.dart';
import '../theme/app_theme.dart';
import '../screens/app_detail_screen.dart';

class AppCard extends StatefulWidget {
  final FDroidApp app;
  
  const AppCard({required this.app});
  
  @override
  _AppCardState createState() => _AppCardState();
}

class _AppCardState extends State<AppCard> {
  bool isFocused = false;
  
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Focus(
      onFocusChange: (focused) => setState(() => isFocused = focused),
      child: GestureDetector(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => AppDetailScreen(app: widget.app),
            ),
          );
        },
        child: AnimatedContainer(
          duration: Duration(milliseconds: 200),
          transform: Matrix4.identity()
            ..scale(isFocused ? 1.05 : 1.0),
          decoration: BoxDecoration(
            color: isDark ? AppTheme.cardDark : AppTheme.cardLight,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: isFocused
                  ? AppTheme.primaryGreen
                  : Colors.grey.withOpacity(0.3),
              width: isFocused ? 2 : 1,
            ),
          ),
          padding: EdgeInsets.all(16),
          child: Column(
            children: [
              // Icon
              Expanded(
                child: CachedNetworkImage(
                  imageUrl: widget.app.iconUrl,
                  placeholder: (context, url) => Icon(Icons.android, size: 48),
                  errorWidget: (context, url, error) => Icon(Icons.android, size: 48),
                ),
              ),
              SizedBox(height: 8),
              // Name
              Text(
                widget.app.name,
                style: TextStyle(fontWeight: FontWeight.bold),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              // Summary
              Text(
                widget.app.summary,
                style: TextStyle(fontSize: 12, color: Colors.grey),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }
}