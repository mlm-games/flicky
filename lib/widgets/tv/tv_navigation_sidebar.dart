import 'package:flicky/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class TVNavigationSidebar extends StatelessWidget {
  final int selectedIndex;
  final Function(int) onIndexChanged;

  const TVNavigationSidebar({
    Key? key,
    required this.selectedIndex,
    required this.onIndexChanged,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      width: 280,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF25282C) : Colors.white,
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
            padding: const EdgeInsets.all(20),
            child: Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: AppTheme.primaryGreen,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.shop_2, color: Colors.white),
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const [
                    Text(
                      'Flicky',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      'F-Droid for TV',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ],
                ),
              ],
            ),
          ),

          const SizedBox(height: 20),

          // Navigation items
          TVNavItem(
            icon: Icons.explore,
            label: 'Browse',
            isSelected: selectedIndex == 0,
            onTap: () => onIndexChanged(0),
            autofocus: selectedIndex == 0,
          ),
          TVNavItem(
            icon: Icons.category,
            label: 'Categories',
            isSelected: selectedIndex == 1,
            onTap: () => onIndexChanged(1),
            autofocus: selectedIndex == 1,
          ),
          TVNavItem(
            icon: Icons.update,
            label: 'Updates',
            isSelected: selectedIndex == 2,
            onTap: () => onIndexChanged(2),
            autofocus: selectedIndex == 2,
          ),
          TVNavItem(
            icon: Icons.settings,
            label: 'Settings',
            isSelected: selectedIndex == 3,
            onTap: () => onIndexChanged(3),
            autofocus: selectedIndex == 3,
          ),
        ],
      ),
    );
  }
}

class TVNavItem extends StatefulWidget {
  final IconData icon;
  final String label;
  final bool isSelected;
  final VoidCallback onTap;
  final bool autofocus;

  const TVNavItem({
    Key? key,
    required this.icon,
    required this.label,
    required this.isSelected,
    required this.onTap,
    this.autofocus = false,
  }) : super(key: key);

  @override
  State<TVNavItem> createState() => _TVNavItemState();
}

class _TVNavItemState extends State<TVNavItem> {
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

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      child: InkWell(
        focusNode: _focusNode,
        autofocus: widget.autofocus,
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          margin: const EdgeInsets.symmetric(vertical: 4),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: widget.isSelected
                ? AppTheme.primaryGreen.withOpacity(0.1)
                : isFocused
                ? Colors.grey.withOpacity(0.1)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isFocused ? AppTheme.primaryGreen : Colors.transparent,
              width: 2,
            ),
          ),
          child: Row(
            children: [
              Icon(
                widget.icon,
                color: widget.isSelected ? AppTheme.primaryGreen : null,
              ),
              const SizedBox(width: 12),
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
