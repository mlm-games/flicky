class BrowseScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(appsProvider);
    
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // Search bar
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(20),
              child: SearchBar(),
            ),
          ),
          
          // Featured apps carousel
          SliverToBoxAdapter(
            child: FeaturedCarousel(),
          ),
          
          // Recently updated
          SliverToBoxAdapter(
            child: SectionHeader(
              title: 'Recently Updated',
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
                  childCount: appList.length,
                ),
              ),
              loading: () => SliverToBoxAdapter(
                child: LoadingIndicator(),
              ),
              error: (error, stack) => SliverToBoxAdapter(
                child: ErrorWidget(error: error),
              ),
            ),
          ),
        ],
      ),
    );
  }
}