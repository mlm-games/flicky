import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

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
          // Logo
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
                      'Flicky',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      'Alt Store TV',
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
            onTap: () => onIndexChanged(2),
          ),
          _NavItem(
            icon: Icons.settings,
            label: 'Settings',
            isSelected: selectedIndex == 3,
            onTap: () => onIndexChanged(3),
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
  final VoidCallback onTap;
  
  const _NavItem({
    required this.icon,
    required this.label,
    required this.isSelected,
    required this.onTap,
  });
  
  @override
  _NavItemState createState() => _NavItemState();
}

class _NavItemState extends State<_NavItem> {
  bool isFocused = false;
  
  @override
  Widget build(BuildContext context) {
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
                    ? Colors.grey.withOpacity(0.1)
                    : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Icon(
                widget.icon,
                color: widget.isSelected ? AppTheme.primaryGreen : null,
              ),
              SizedBox(width: 12),
              Text(
                widget.label,
                style: TextStyle(
                  fontWeight: widget.isSelected ? FontWeight.bold : null,
                  color: widget.isSelected ? AppTheme.primaryGreen : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}