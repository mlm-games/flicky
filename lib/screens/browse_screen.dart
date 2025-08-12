import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../widgets/app_card.dart';
import '../widgets/search_bar.dart' as custom;
import '../widgets/featured_carousel.dart';
import '../widgets/section_header.dart';
import '../widgets/loading_indicator.dart';

class BrowseScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(filteredAppsProvider);
    
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // Search bar
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(20),
              child: custom.SearchBar(),
            ),
          ),
          
          // Featured apps
          SliverToBoxAdapter(
            child: FeaturedCarousel(),
          ),
          
          // Recently updated
          SliverToBoxAdapter(
            child: SectionHeader(
              title: 'All Apps',
              onSeeAll: () {},
            ),
          ),
          
          // Apps grid
          SliverPadding(
            padding: EdgeInsets.all(20),
            sliver: apps.when(
              data: (appList) => SliverGrid(
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 5,
                  childAspectRatio: 0.8,
                  crossAxisSpacing: 16,
                  mainAxisSpacing: 16,
                ),
                delegate: SliverChildBuilderDelegate(
                  (context, index) => AppCard(app: appList[index]),
                  childCount: appList.length > 20 ? 20 : appList.length,
                ),
              ),
              loading: () => SliverToBoxAdapter(
                child: LoadingIndicator(),
              ),
              error: (error, stack) => SliverToBoxAdapter(
                child: Center(
                  child: Text('Error: $error'),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}