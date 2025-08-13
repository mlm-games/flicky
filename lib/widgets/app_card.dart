import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/fdroid_app.dart';
import '../theme/app_theme.dart';
import '../screens/app_detail_screen.dart';

class AppCard extends StatefulWidget {
  final FDroidApp app;
  final bool autofocus;

  const AppCard({required this.app, this.autofocus = false});

  @override
  _AppCardState createState() => _AppCardState();
}

class _AppCardState extends State<AppCard> {
  bool isFocused = false;
  late FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
    _focusNode.addListener(() {
      if (mounted) {
        setState(() {
          isFocused = _focusNode.hasFocus;
        });
      }
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  void _handleSelect() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => AppDetailScreen(app: widget.app)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Semantics(
      button: true,
      child: InkWell(
        focusNode: _focusNode,
        autofocus: widget.autofocus,
        onTap: _handleSelect,
        borderRadius: BorderRadius.circular(16),
        child: AnimatedContainer(
          duration: Duration(milliseconds: 200),
          transform: Matrix4.identity()..scale(isFocused ? 1.05 : 1.0),
          decoration: BoxDecoration(
            color: isDark ? AppTheme.cardDark : AppTheme.cardLight,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: isFocused
                  ? AppTheme.primaryGreen
                  : Colors.grey.withValues(alpha: 0.3),
              width: isFocused ? 3 : 1,
            ),
            boxShadow: isFocused
                ? [
                    BoxShadow(
                      color: AppTheme.primaryGreen.withValues(alpha: 0.3),
                      blurRadius: 12,
                      spreadRadius: 2,
                    ),
                  ]
                : [],
          ),
          padding: EdgeInsets.all(16),
          child: Column(
            children: [
              // Icon
              Expanded(
                child: Hero(
                  tag: 'app-icon-${widget.app.packageName}',
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: CachedNetworkImage(
                      imageUrl: widget.app.iconUrl,
                      fit: BoxFit.contain,
                      placeholder: (context, url) => Container(
                        color: Colors.grey.withValues(alpha: 0.2),
                        child: Icon(Icons.android, size: 48),
                      ),
                      errorWidget: (context, url, error) => Container(
                        color: Colors.grey.withValues(alpha: 0.2),
                        child: Icon(Icons.android, size: 48),
                      ),
                    ),
                  ),
                ),
              ),
              SizedBox(height: 8),
              // Name
              Text(
                widget.app.name,
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: isFocused ? 14 : 13,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
              ),
              // Summary
              Text(
                widget.app.summary,
                style: TextStyle(fontSize: 11, color: Colors.grey),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
