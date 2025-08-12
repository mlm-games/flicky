class NavigationSidebar extends StatelessWidget {
  final int selectedIndex;
  final Function(int) onIndexChanged;
  
  const NavigationSidebar({
    required this.selectedIndex,
    required this.onIndexChanged,
  });
  
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Container(
      width: 280,
      padding: EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? Color(0xFF25282C) : Colors.white,
        border: Border(
          right: BorderSide(
            color: isDark ? Colors.grey.shade800 : Colors.grey.shade200,
          ),
        ),
      ),
      child: Column(
        children: [
          // Logo section
          Container(
            padding: EdgeInsets.all(20),
            child: Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: AppTheme.primaryGreen,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(Icons.shop_2, color: Colors.white),
                ),
                SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'F-Droid TV',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      'Free Software',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          
          SizedBox(height: 20),
          
          // Navigation items
          _NavItem(
            icon: Icons.explore,
            label: 'Browse',
            isSelected: selectedIndex == 0,
            onTap: () => onIndexChanged(0),
          ),
          _NavItem(
            icon: Icons.category,
            label: 'Categories',
            isSelected: selectedIndex == 1,
            onTap: () => onIndexChanged(1),
          ),
          _NavItem(
            icon: Icons.update,
            label: 'Updates',
            isSelected: selectedIndex == 2,
            badge: '3', // Show update count
            onTap: () => onIndexChanged(2),
          ),
          _NavItem(
            icon: Icons.settings,
            label: 'Settings',
            isSelected: selectedIndex == 3,
            onTap: () => onIndexChanged(3),
          ),
          
          Spacer(),
          
          // Repository info
          Container(
            padding: EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: isDark ? Colors.grey.shade900 : Colors.grey.shade100,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    Icon(Icons.storage, size: 16, color: Colors.grey),
                    SizedBox(width: 8),
                    Text('F-Droid Main', style: TextStyle(fontSize: 12)),
                  ],
                ),
                SizedBox(height: 8),
                LinearProgressIndicator(
                  value: 0.7,
                  backgroundColor: Colors.grey.shade300,
                  valueColor: AlwaysStoppedAnimation(AppTheme.primaryGreen),
                ),
                SizedBox(height: 4),
                Text(
                  '2,543 apps available',
                  style: TextStyle(fontSize: 11, color: Colors.grey),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _NavItem extends StatefulWidget {
  final IconData icon;
  final String label;
  final bool isSelected;
  final String? badge;
  final VoidCallback onTap;
  
  const _NavItem({
    required this.icon,
    required this.label,
    required this.isSelected,
    required this.onTap,
    this.badge,
  });
  
  @override
  _NavItemState createState() => _NavItemState();
}

class _NavItemState extends State<_NavItem> {
  bool isFocused = false;
  
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Focus(
      onFocusChange: (focused) => setState(() => isFocused = focused),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: Duration(milliseconds: 200),
          margin: EdgeInsets.symmetric(vertical: 4),
          padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: widget.isSelected
                ? AppTheme.primaryGreen.withOpacity(0.1)
                : isFocused
                    ? (isDark ? Colors.grey.shade800 : Colors.grey.shade100)
                    : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
            border: widget.isSelected
                ? Border.all(color: AppTheme.primaryGreen, width: 2)
                : null,
          ),
          child: Row(
            children: [
              Icon(
                widget.icon,
                color: widget.isSelected ? AppTheme.primaryGreen : null,
              ),
              SizedBox(width: 12),
              Expanded(
                child: Text(
                  widget.label,
                  style: TextStyle(
                    fontWeight: widget.isSelected ? FontWeight.bold : null,
                    color: widget.isSelected ? AppTheme.primaryGreen : null,
                  ),
                ),
              ),
              if (widget.badge != null)
                Container(
                  padding: EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.red,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    widget.badge!,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}