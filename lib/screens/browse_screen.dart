import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../widgets/app_card.dart';
import '../widgets/search_bar.dart' as custom;
import '../widgets/loading_indicator.dart';
import '../widgets/error_widget.dart';

class BrowseScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(sortedAppsProvider);
    
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // Search bar with sort
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(20),
              child: Column(
                children: [
                  custom.SearchBar(),
                  SizedBox(height: 10),
                  // Show loading status
                  apps.when(
                    data: (appList) => Text(
                      '${appList.length} apps available',
                      style: TextStyle(color: Colors.grey),
                    ),
                    loading: () => Text(
                      'Loading F-Droid repository...',
                      style: TextStyle(color: Colors.grey),
                    ),
                    error: (_, __) => Text(
                      'Using offline data',
                      style: TextStyle(color: Colors.orange),
                    ),
                  ),
                ],
              ),
            ),
          ),
          
          // Apps grid
          SliverPadding(
            padding: EdgeInsets.all(20),
            sliver: apps.when(
              data: (appList) {
                if (appList.isEmpty) {
                  return SliverToBoxAdapter(
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.search_off, size: 64, color: Colors.grey),
                          SizedBox(height: 16),
                          Text(
                            'No apps found',
                            style: TextStyle(fontSize: 18, color: Colors.grey),
                          ),
                        ],
                      ),
                    ),
                  );
                }
                return SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: _getGridCrossAxisCount(context),
                    childAspectRatio: 0.8,
                    crossAxisSpacing: 16,
                    mainAxisSpacing: 16,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (context, index) => AppCard(
                      app: appList[index],
                      autofocus: index == 0,
                    ),
                    childCount: appList.length,
                  ),
                );
              },
              loading: () => SliverToBoxAdapter(
                child: Container(
                  height: 400,
                  child: LoadingIndicator(),
                ),
              ),
              error: (error, stack) => SliverToBoxAdapter(
                child: ErrorDisplay(error: error, stackTrace: stack),
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  int _getGridCrossAxisCount(BuildContext context) {
    final width = MediaQuery.of(context).size.width;
    if (width > 1400) return 6;
    if (width > 1200) return 5;
    if (width > 900) return 4;
    if (width > 600) return 3;
    return 2;
  }
}