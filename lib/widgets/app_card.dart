class AppCard extends StatefulWidget {
  final FDroidApp app;
  
  const AppCard({required this.app});
  
  @override
  _AppCardState createState() => _AppCardState();
}

class _AppCardState extends State<AppCard> {
  bool isFocused = false;
  bool isHovered = false;
  
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Focus(
      onFocusChange: (focused) => setState(() => isFocused = focused),
      child: MouseRegion(
        onEnter: (_) => setState(() => isHovered = true),
        onExit: (_) => setState(() => isHovered = false),
        child: GestureDetector(
          onTap: () => _showAppDetails(context),
          child: AnimatedContainer(
            duration: Duration(milliseconds: 200),
            transform: Matrix4.identity()
              ..scale(isFocused || isHovered ? 1.05 : 1.0),
            child: Container(
              decoration: BoxDecoration(
                color: isDark ? AppTheme.cardDark : AppTheme.cardLight,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isFocused
                      ? AppTheme.primaryGreen
                      : isDark
                          ? Colors.grey.shade800
                          : Colors.grey.shade200,
                  width: isFocused ? 2 : 1,
                ),
                boxShadow: (isFocused || isHovered)
                    ? [
                        BoxShadow(
                          color: AppTheme.primaryGreen.withOpacity(0.2),
                          blurRadius: 20,
                          spreadRadius: 2,
                        ),
                      ]
                    : null,
              ),
              padding: EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // App icon
                  Center(
                    child: Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(14),
                        color: Colors.grey.shade200,
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(14),
                        child: CachedNetworkImage(
                          imageUrl: widget.app.iconUrl,
                          placeholder: (context, url) => Container(
                            color: Colors.grey.shade300,
                            child: Icon(Icons.android, size: 32),
                          ),
                          errorWidget: (context, url, error) => Icon(
                            Icons.android,
                            size: 32,
                            color: Colors.grey,
                          ),
                        ),
                      ),
                    ),
                  ),
                  
                  SizedBox(height: 12),
                  
                  // App name
                  Text(
                    widget.app.name,
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  
                  SizedBox(height: 4),
                  
                  // App summary
                  Text(
                    widget.app.summary,
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  
                  Spacer(),
                  
                  // Version and size
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'v${widget.app.version}',
                        style: TextStyle(
                          fontSize: 11,
                          color: Colors.grey,
                        ),
                      ),
                      Text(
                        _formatSize(widget.app.size),
                        style: TextStyle(
                          fontSize: 11,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                  
                  SizedBox(height: 8),
                  
                  // Install button
                  AnimatedContainer(
                    duration: Duration(milliseconds: 200),
                    height: 32,
                    decoration: BoxDecoration(
                      color: isFocused
                          ? AppTheme.primaryGreen
                          : AppTheme.primaryGreen.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Center(
                      child: Text(
                        widget.app.isInstalled ? 'Open' : 'Install',
                        style: TextStyle(
                          color: isFocused
                              ? Colors.white
                              : AppTheme.primaryGreen,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
  
  void _showAppDetails(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => AppDetailScreen(app: widget.app),
      ),
    );
  }
  
  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}