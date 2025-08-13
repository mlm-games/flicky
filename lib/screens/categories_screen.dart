import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../widgets/app_card.dart';
import '../theme/app_theme.dart';

class CategoriesScreen extends ConsumerStatefulWidget {
  const CategoriesScreen({super.key});

  @override
  CategoriesScreenState createState() => CategoriesScreenState();
}

class CategoriesScreenState extends ConsumerState<CategoriesScreen> {
  String selectedCategory = 'All';

  @override
  Widget build(BuildContext context) {
    final categories = ref.watch(categoriesProvider);
    final apps = ref.watch(appsProvider);

    return Scaffold(
      body: Row(
        children: [
          // Categories sidebar
          Container(
            width: 250,
            decoration: BoxDecoration(
              color: Theme.of(context).cardColor,
              border: Border(
                right: BorderSide(color: Colors.grey..withValues(alpha: 0.2)),
              ),
            ),
            child: Column(
              children: [
                Container(
                  padding: EdgeInsets.all(20),
                  child: Text(
                    'Categories',
                    style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                  ),
                ),
                Expanded(
                  child: ListView(
                    children: [
                      _CategoryItem(
                        title: 'All',
                        icon: Icons.apps,
                        isSelected: selectedCategory == 'All',
                        onTap: () => setState(() => selectedCategory = 'All'),
                        count: apps.maybeWhen(
                          data: (list) => list.length,
                          orElse: () => 0,
                        ),
                      ),
                      ...categories.map(
                        (category) => _CategoryItem(
                          title: category,
                          icon: _getCategoryIcon(category),
                          isSelected: selectedCategory == category,
                          onTap: () =>
                              setState(() => selectedCategory = category),
                          count: apps.maybeWhen(
                            data: (list) => list
                                .where((app) => app.category == category)
                                .length,
                            orElse: () => 0,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Apps grid
          Expanded(
            child: apps.when(
              data: (appList) {
                final filteredApps = selectedCategory == 'All'
                    ? appList
                    : appList
                          .where((app) => app.category == selectedCategory)
                          .toList();

                return Column(
                  children: [
                    Container(
                      padding: EdgeInsets.all(20),
                      child: Row(
                        children: [
                          Icon(_getCategoryIcon(selectedCategory)),
                          SizedBox(width: 12),
                          Text(
                            selectedCategory,
                            style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          SizedBox(width: 12),
                          Chip(
                            label: Text('${filteredApps.length} apps'),
                            backgroundColor: AppTheme.primaryGreen.withValues(
                              alpha: 0.1,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: GridView.builder(
                        padding: EdgeInsets.all(20),
                        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: _getGridCount(context),
                          childAspectRatio: 0.8,
                          crossAxisSpacing: 16,
                          mainAxisSpacing: 16,
                        ),
                        itemCount: filteredApps.length,
                        itemBuilder: (context, index) {
                          return AppCard(app: filteredApps[index]);
                        },
                      ),
                    ),
                  ],
                );
              },
              loading: () => Center(child: CircularProgressIndicator()),
              error: (error, stack) => Center(child: Text('Error: $error')),
            ),
          ),
        ],
      ),
    );
  }

  int _getGridCount(BuildContext context) {
    final width = MediaQuery.of(context).size.width - 250;
    if (width > 1200) return 5;
    if (width > 900) return 4;
    if (width > 600) return 3;
    return 2;
  }

  IconData _getCategoryIcon(String category) {
    switch (category.toLowerCase()) {
      case 'all':
        return Icons.apps;
      case 'connectivity':
        return Icons.wifi;
      case 'development':
        return Icons.code;
      case 'games':
        return Icons.games;
      case 'graphics':
        return Icons.palette;
      case 'internet':
        return Icons.language;
      case 'money':
        return Icons.attach_money;
      case 'multimedia':
        return Icons.movie;
      case 'navigation':
        return Icons.navigation;
      case 'phone & sms':
        return Icons.phone;
      case 'reading':
        return Icons.book;
      case 'science & education':
        return Icons.school;
      case 'security':
        return Icons.security;
      case 'sports & health':
        return Icons.fitness_center;
      case 'system':
        return Icons.settings_applications;
      case 'theming':
        return Icons.color_lens;
      case 'time':
        return Icons.access_time;
      case 'writing':
        return Icons.edit;
      default:
        return Icons.category;
    }
  }
}

class _CategoryItem extends StatefulWidget {
  final String title;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;
  final int count;

  const _CategoryItem({
    required this.title,
    required this.icon,
    required this.isSelected,
    required this.onTap,
    required this.count,
  });

  @override
  _CategoryItemState createState() => _CategoryItemState();
}

class _CategoryItemState extends State<_CategoryItem> {
  bool isFocused = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      onFocusChange: (focused) => setState(() => isFocused = focused),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: Duration(milliseconds: 200),
          margin: EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: widget.isSelected
                ? AppTheme.primaryGreen.withValues(alpha: 0.1)
                : isFocused
                ? Colors.grey.withValues(alpha: 0.1)
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
                size: 20,
                color: widget.isSelected ? AppTheme.primaryGreen : null,
              ),
              SizedBox(width: 12),
              Expanded(
                child: Text(
                  widget.title,
                  style: TextStyle(
                    fontWeight: widget.isSelected
                        ? FontWeight.bold
                        : FontWeight.normal,
                    color: widget.isSelected ? AppTheme.primaryGreen : null,
                  ),
                ),
              ),
              Container(
                padding: EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: widget.isSelected
                      ? AppTheme.primaryGreen
                      : Colors.grey.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '${widget.count}',
                  style: TextStyle(
                    fontSize: 12,
                    color: widget.isSelected ? Colors.white : null,
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
